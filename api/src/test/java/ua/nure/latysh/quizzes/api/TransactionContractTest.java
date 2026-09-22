package ua.nure.latysh.quizzes.api;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.support.TransactionTemplate;
import ua.nure.latysh.quizzes.api.attempt.AttemptService;
import ua.nure.latysh.quizzes.api.quiz.QuizQueryService;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API contract for the transaction boundaries and isolation levels the services declare. */
class TransactionContractTest extends ApiContractTestBase {
    @Test
    void resolvesRoleAndStatusWithoutAnOpenTransaction() {
        // Role and status are lazy, and Spring Security looks users up outside a
        // transaction during login. If the fetch join is ever dropped from
        // findByLogin, touching them here throws and the only symptom callers
        // see is a 401, which says nothing about the cause. This asserts the
        // associations are usable on a detached account.
        UserDetails student = userDetailsService.loadUserByUsername("student");
        assertThat(student.isEnabled()).isTrue();
        assertThat(student.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");

        UserDetails admin = userDetailsService.loadUserByUsername("admin");
        assertThat(admin.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");

        // Status drives this flag, so a lazy status that failed to load would
        // not simply throw — it would silently mis-report the account.
        assertThat(userDetailsService.loadUserByUsername("blocked").isEnabled()).isFalse();
    }

    @Test
    void readsAnAttemptInASingleTransaction() {
        // findOwned issues two queries: the attempt, and the snapshot it was
        // issued with. Without a read-only transaction around the method each
        // one ran in its own session on its own connection, so the two could
        // see two different states of the database.
        //
        // It used to be three — the attempt, its questions, and their answers —
        // and the snapshot replaced the last two. The count is asserted rather
        // than bounded because a fourth would mean the snapshot was missed and
        // the quiz read live instead, which is the fallback for a rolling
        // deployment and must not become the ordinary path.
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        attemptService.findOwned(1, "student");   // warm up: first call loads classes and metadata
        statistics.clear();

        attemptService.findOwned(1, "student");

        assertThat(statistics.getPrepareStatementCount())
                .as("the read still issues its two queries")
                .isEqualTo(2);
        assertThat(statistics.getSessionOpenCount())
                .as("but they share one session, and so one transaction")
                .isEqualTo(1);

        // The session count alone does not say which isolation that transaction
        // asked for, and the snapshot guarantee below is worthless without it,
        // so check that the service still requests it.
        Transactional declared = AnnotationUtils.findAnnotation(
                AopUtils.getTargetClass(attemptService), Transactional.class);
        assertThat(declared).isNotNull();
        assertThat(declared.readOnly()).isTrue();
        assertThat(declared.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }

    /**
     * Every service that pins the isolation level pins it on every method.
     *
     * <p>The test above reads the class annotation, which is the one thing a
     * write method does not use: Spring takes the most specific annotation and
     * does not merge, so a method-level {@code @Transactional} replaces the
     * class-level one outright — readOnly, isolation and all. Fifteen write
     * methods were once declared that way and ran at the database default.
     *
     * <p>The services are discovered rather than listed. A list is only correct
     * until someone adds a service — splitting AdminService into four would have
     * quietly taken all four out of this contract while the test went on passing
     * — so the rule is stated as a property of the code instead: a class that
     * pins the level on itself must pin it on each of its transactional methods.
     * A service that does not pin it at all is not making the claim and is not
     * held to it; {@code ApiUserDetailsService} is the one such class.
     *
     * <p>It asks {@link AnnotationTransactionAttributeSource}, the same class
     * the transaction interceptor consults at runtime, so it reports the
     * attribute that will actually be applied rather than the annotation text.
     */
    @Test
    void everyServiceThatPinsTheIsolationLevelPinsItOnEveryMethod() {
        var attributes = new AnnotationTransactionAttributeSource();
        var unpinned = new java.util.ArrayList<String>();
        var pinnedServices = new java.util.ArrayList<String>();

        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = applicationContext.getType(beanName);
            if (type == null || !type.getPackageName().startsWith("ua.nure.latysh.quizzes.api")) {
                continue;
            }
            Class<?> target = org.springframework.util.ClassUtils.getUserClass(type);
            if (AnnotationUtils.findAnnotation(target, Transactional.class) == null) {
                continue;
            }
            pinnedServices.add(target.getSimpleName());
            for (java.lang.reflect.Method method : target.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                TransactionAttribute attribute = attributes.getTransactionAttribute(method, target);
                if (attribute != null
                        && attribute.getIsolationLevel() != TransactionDefinition.ISOLATION_REPEATABLE_READ) {
                    unpinned.add(target.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertThat(pinnedServices)
                .as("discovery found no services at all, so this would pass whatever the code did")
                .contains("AttemptService", "AccountService", "QuizQueryService", "ResultQueryService",
                        "CatalogueAdminService", "QuizAdminService", "QuestionAdminService",
                        "UserAdminService", "ResultAdminService");
        assertThat(unpinned)
                .as("a bare @Transactional drops the class-level isolation without saying so")
                .isEmpty();
    }

    /**
     * The property the pin buys, on a transaction that writes.
     *
     * <p>{@link #holdsOneSnapshotForTheLengthOfARead()} asserts this for the
     * read-only transactions. A write method reads before it decides — start
     * counts a quiz's questions twice before admitting an attempt — so it wants
     * the same snapshot, and until now it did not ask for one. The test
     * datasource makes the gap visible: H2 defaults to READ COMMITTED, so the
     * write paths were exercised at a weaker level than MySQL gives them in
     * production, and a race the pin prevents there would pass here unnoticed.
     */
    @Test
    void aWritingTransactionHoldsOneSnapshotOnlyWhenItAsksFor() throws Exception {
        String original = jdbcTemplate.queryForObject(
                "SELECT name FROM subjects WHERE id = 1", String.class);

        assertThat(readTwiceAcrossAConcurrentCommit(TransactionDefinition.ISOLATION_DEFAULT))
                .as("H2's default is READ COMMITTED, which is the level the writes used to get")
                .isEqualTo("Renamed Mid-Write");

        assertThat(readTwiceAcrossAConcurrentCommit(TransactionDefinition.ISOLATION_REPEATABLE_READ))
                .as("the level the write methods now pin holds the snapshot they decide on")
                .isEqualTo(original);
    }

    @Test
    void holdsOneSnapshotForTheLengthOfARead() throws Exception {
        // Sharing a transaction is necessary but not sufficient: under READ
        // COMMITTED every statement takes its own snapshot, so a concurrent
        // commit would still be visible between two queries of the same read
        // and the test above would pass anyway. This asserts the property that
        // one actually depends on, which is why the read transactions pin
        // REPEATABLE_READ instead of inheriting whatever the database defaults
        // to. Whether a level really holds a snapshot is the database's answer,
        // not Spring's, so ReadIsolationIntegrationTest asks MySQL the same
        // question; this covers the H2 datasource the rest of the suite uses.
        String original = jdbcTemplate.queryForObject(
                "SELECT name FROM subjects WHERE id = 1", String.class);
        var writer = Executors.newSingleThreadExecutor();
        var template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try {
            String secondRead = template.execute(status -> {
                jdbcTemplate.queryForObject("SELECT name FROM subjects WHERE id = 1", String.class);
                try {
                    // a different connection commits while this read is open
                    writer.submit(() -> jdbcTemplate.update(
                            "UPDATE subjects SET name = ? WHERE id = 1", "Renamed Mid-Read")).get();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                return jdbcTemplate.queryForObject(
                        "SELECT name FROM subjects WHERE id = 1", String.class);
            });
            assertThat(secondRead)
                    .as("a commit landing mid-read must not change what the read sees")
                    .isEqualTo(original);
        } finally {
            writer.shutdown();
            jdbcTemplate.update("UPDATE subjects SET name = ? WHERE id = 1", original);
        }
    }

    /**
     * Reads a row inside a read-write transaction, lets another connection
     * commit a change to it, and returns what the second read sees.
     */
    private String readTwiceAcrossAConcurrentCommit(int isolationLevel) throws Exception {
        String original = jdbcTemplate.queryForObject(
                "SELECT name FROM subjects WHERE id = 1", String.class);
        var writer = Executors.newSingleThreadExecutor();
        var template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(isolationLevel);
        try {
            return template.execute(status -> {
                jdbcTemplate.queryForObject("SELECT name FROM subjects WHERE id = 1", String.class);
                try {
                    writer.submit(() -> jdbcTemplate.update(
                            "UPDATE subjects SET name = ? WHERE id = 1", "Renamed Mid-Write")).get();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                return jdbcTemplate.queryForObject(
                        "SELECT name FROM subjects WHERE id = 1", String.class);
            });
        } finally {
            writer.shutdown();
            jdbcTemplate.update("UPDATE subjects SET name = ? WHERE id = 1", original);
        }
    }
}
