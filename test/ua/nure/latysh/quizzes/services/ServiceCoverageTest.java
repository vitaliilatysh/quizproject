package ua.nure.latysh.quizzes.services;

import org.junit.Test;
import ua.nure.latysh.quizzes.dto.QuizDto;
import ua.nure.latysh.quizzes.dto.ResultDto;
import ua.nure.latysh.quizzes.dto.UserDto;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.entities.Level;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.entities.Role;
import ua.nure.latysh.quizzes.entities.Status;
import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.repositories.AnswerRepository;
import ua.nure.latysh.quizzes.repositories.AttemptRepository;
import ua.nure.latysh.quizzes.repositories.LevelRepository;
import ua.nure.latysh.quizzes.repositories.QuestionRepository;
import ua.nure.latysh.quizzes.repositories.QuizRepository;
import ua.nure.latysh.quizzes.repositories.RoleRepository;
import ua.nure.latysh.quizzes.repositories.StatusRepository;
import ua.nure.latysh.quizzes.repositories.SubjectRepository;
import ua.nure.latysh.quizzes.repositories.UserRepository;
import ua.nure.latysh.quizzes.security.PasswordHasher;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServiceCoverageTest {

    @Test
    public void defaultConstructorsWireProductionRepositories() {
        assertNotNull(new AnswerService());
        assertNotNull(new AttemptService());
        assertNotNull(new LevelService());
        assertNotNull(new QuestionService());
        assertNotNull(new QuizService());
        assertNotNull(new ResultService());
        assertNotNull(new SubjectService());
        assertNotNull(new UserService());
        assertThrows(java.util.NoSuchElementException.class,
                () -> RequiredEntity.get(Optional.empty(), "Quiz 404"));
    }

    @Test
    public void answerServiceDelegatesEveryOperation() {
        AnswerRepository repository = mock(AnswerRepository.class);
        AnswerService service = new AnswerService(repository);
        Answer answer = new Answer();
        List<Answer> answers = List.of(answer);
        when(repository.findAll()).thenReturn(answers);
        when(repository.save(answer)).thenReturn(true);
        when(repository.findById(7)).thenReturn(Optional.of(answer));
        when(repository.findAllByQuestionId(8)).thenReturn(answers);

        assertEquals(answers, service.getAllAnswers());
        assertTrue(service.saveAnswer(answer));
        assertEquals(Optional.of(answer), service.findAnswerById(7));
        service.updateAnswer(answer);
        assertEquals(answers, service.findAnswersByQuestionId(8));

        verify(repository).update(answer);
    }

    @Test
    public void attemptServiceDelegatesEveryOperation() {
        AttemptRepository repository = mock(AttemptRepository.class);
        AttemptService service = new AttemptService(repository);
        Attempt attempt = attempt(11, 4, 5, 80);
        User user = new User();
        user.setId(5);
        List<Attempt> attempts = List.of(attempt);
        when(repository.findAll()).thenReturn(attempts);
        LocalDateTime from = LocalDateTime.parse("2026-08-01T10:00");
        LocalDateTime to = LocalDateTime.parse("2026-08-02T10:00");
        when(repository.findAllBetweenFinishDates(from, to)).thenReturn(attempts);
        when(repository.findAllByUserId(5)).thenReturn(attempts);
        when(repository.save(attempt)).thenReturn(true);
        when(repository.findLastByUserId(5)).thenReturn(Optional.of(attempt));

        assertEquals(attempts, service.getAllAttempts());
        assertEquals(attempts, service.getAllAttemptsBetweenFinishDates(from, to));
        assertEquals(attempts, service.findAllAttemptsPerUser(5));
        assertTrue(service.saveAttempt(attempt));
        assertEquals(Optional.of(attempt), service.findTheLatestForUser(user));
        service.updateAttemptByScore(attempt);

        verify(repository).update(attempt);
    }

    @Test
    public void attemptServiceStartsAndCompletesUsingTheServerClock() {
        AttemptRepository repository = mock(AttemptRepository.class);
        Instant now = Instant.parse("2026-08-12T08:00:00Z");
        AttemptService service = new AttemptService(repository, Clock.fixed(now, ZoneOffset.UTC));
        User user = user(5, "alice");
        Quiz quiz = quiz(7, "Security", 1, 1, 3);
        when(repository.create(org.mockito.ArgumentMatchers.any(Attempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Attempt started = service.startAttempt(user, quiz);
        assertEquals(5, started.getUserId());
        assertEquals(7, started.getQuizId());
        assertEquals(Date.from(now), started.getStartTime());
        assertEquals(Date.from(now.plusSeconds(180)), started.getExpiresAt());
        assertFalse(started.isCompleted());

        Set<Integer> answers = Set.of(10, 20);
        service.completeAttempt(9, 5, answers);
        verify(repository).complete(9, 5, answers, Date.from(now));
    }

    @Test
    public void levelAndSubjectServicesDelegateEveryOperation() {
        LevelRepository levelRepository = mock(LevelRepository.class);
        Level level = level(1, "hard");
        when(levelRepository.findByName("hard")).thenReturn(Optional.of(level));
        when(levelRepository.findAll()).thenReturn(List.of(level));
        LevelService levelService = new LevelService(levelRepository);
        assertEquals(Optional.of(level), levelService.findAnswerById("hard"));
        assertEquals(List.of(level), levelService.findAllLevels());

        SubjectRepository subjectRepository = mock(SubjectRepository.class);
        Subject subject = subject(2, "Java");
        when(subjectRepository.findAll()).thenReturn(List.of(subject));
        when(subjectRepository.save(subject)).thenReturn(true);
        when(subjectRepository.findById(2)).thenReturn(Optional.of(subject));
        SubjectService subjectService = new SubjectService(subjectRepository);
        assertEquals(List.of(subject), subjectService.getAllSubjects());
        assertTrue(subjectService.addSubject(subject));
        subjectService.deleteSubject(subject);
        assertEquals(Optional.of(subject), subjectService.findSubjectById(2));
        subjectService.updateSubject(subject);
        verify(subjectRepository).delete(subject);
        verify(subjectRepository).update(subject);
    }

    @Test
    public void questionServiceDelegatesAndCreatesQuestionFromQuiz() {
        QuestionRepository questionRepository = mock(QuestionRepository.class);
        QuizRepository quizRepository = mock(QuizRepository.class);
        QuestionService service = new QuestionService(questionRepository, quizRepository);
        Quiz quiz = quiz(9, "Quiz", 1, 2, 10);
        Question question = question(3, "Question", 9);
        when(quizRepository.findById(9)).thenReturn(Optional.of(quiz));
        when(questionRepository.saveQuestion(org.mockito.ArgumentMatchers.any(Question.class))).thenReturn(question);
        when(questionRepository.findAll()).thenReturn(List.of(question));
        when(questionRepository.findById(3)).thenReturn(Optional.of(question));
        when(questionRepository.findByName("Question")).thenReturn(Optional.of(question));
        when(questionRepository.findAllByQuizId(9)).thenReturn(List.of(question));

        assertEquals(List.of(question), service.getAllQuestions());
        assertEquals(question, service.addQuestion("Question", 9));
        service.deleteQuestion(question);
        assertEquals(Optional.of(question), service.findQuestionById(3));
        assertEquals(Optional.of(question), service.findQuestionByName("Question"));
        service.updateQuestion(question);
        assertEquals(List.of(question), service.findQuestionsByQuizId(9));
        verify(questionRepository).delete(question);
        verify(questionRepository).update(question);
    }

    @Test
    public void quizServiceCoversQueriesCommandsAndMappings() {
        QuizRepository quizRepository = mock(QuizRepository.class);
        SubjectRepository subjectRepository = mock(SubjectRepository.class);
        LevelRepository levelRepository = mock(LevelRepository.class);
        QuestionRepository questionRepository = mock(QuestionRepository.class);
        QuizService service = new QuizService(quizRepository, subjectRepository, levelRepository, questionRepository);

        Quiz quiz = quiz(10, "Java", 3, 4, 15);
        Subject subject = subject(4, "Programming");
        Level level = level(3, "medium");
        Question question = question(20, "Q", 10);
        when(quizRepository.findAll()).thenReturn(List.of(quiz));
        when(quizRepository.findBySubjectName("Programming")).thenReturn(List.of(quiz));
        when(quizRepository.findBySubjectId(4)).thenReturn(List.of(quiz));
        when(quizRepository.findById(10)).thenReturn(Optional.of(quiz));
        when(quizRepository.findByName("Java")).thenReturn(Optional.of(quiz));
        when(subjectRepository.findById(4)).thenReturn(Optional.of(subject));
        when(subjectRepository.findByName("Programming")).thenReturn(Optional.of(subject));
        when(levelRepository.findById(3)).thenReturn(Optional.of(level));
        when(levelRepository.findByName("medium")).thenReturn(Optional.of(level));
        when(questionRepository.findAllByQuizId(10)).thenReturn(List.of(question));
        when(quizRepository.save(org.mockito.ArgumentMatchers.any(Quiz.class))).thenReturn(true);

        QuizDto mapped = service.getAllQuizzes().get(0);
        assertEquals("Java", mapped.getName());
        assertEquals("Programming", mapped.getSubjectName());
        assertEquals("medium", mapped.getComplexity());
        assertEquals(1, mapped.getTotalQuestionsNumber());
        assertEquals("Java", service.findQuizBySubjectName("Programming").get(0).getName());
        assertEquals(List.of(quiz), service.findQuizzesBySubjectId(4));
        assertEquals(Optional.of(quiz), service.findQuizById(10));
        assertEquals(Optional.of(quiz), service.findQuizByName("Java"));

        QuizDto input = new QuizDto();
        input.setId(10);
        input.setName("Java");
        input.setSubjectName("Programming");
        input.setComplexity("medium");
        input.setTimeToPass(15);
        assertTrue(service.addQuiz(input));
        service.updateQuiz(input);
        service.deleteQuiz(quiz);
        verify(quizRepository).update(org.mockito.ArgumentMatchers.argThat(value -> value.getId() == 10));
        verify(quizRepository).delete(quiz);
    }

    @Test
    public void userServiceCoversAuthenticationMappingAndStatusChanges() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        StatusRepository statusRepository = mock(StatusRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        UserService service = new UserService(userRepository, roleRepository, statusRepository, passwordHasher);
        User user = user(6, "alice");
        Role role = new Role();
        role.setRole("user");
        Status status = new Status();
        status.setStatus("active");
        when(userRepository.findByLogin("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matchesLegacy("secret", user.getPassword())).thenReturn(true);
        when(passwordHasher.hash("secret")).thenReturn("encoded");
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(userRepository.findById(6)).thenReturn(Optional.of(user));
        when(roleRepository.findById(2)).thenReturn(Optional.of(role));
        when(statusRepository.findById(1)).thenReturn(Optional.of(status));

        assertEquals(user, service.findByLoginAndPassword("alice", "secret"));
        verify(userRepository).updatePassword(user);
        assertEquals(Optional.of(user), service.findUserByLogin("alice"));
        List<UserDto> users = service.findAllUsers();
        assertEquals(1, users.size());
        assertEquals("alice", users.get(0).getFirstName());
        assertEquals("user", users.get(0).getRole());
        assertEquals("active", users.get(0).getStatus());
        assertEquals(Optional.of(user), service.findUserById(6));
        assertEquals(6, service.convertUserToUserDto(user).getId());

        service.blockUser("6");
        assertEquals(2, user.getStatusId());
        service.unblockUser("6");
        assertEquals(1, user.getStatusId());
        service.save(user);
        service.updateUserLoginDate(user);
        verify(userRepository).save(user);
        verify(userRepository).updateLoginDate(user);
    }

    @Test
    public void userServiceCoversEncodedPasswordsFailuresAndAlreadyHashedSaves() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        StatusRepository statusRepository = mock(StatusRepository.class);
        assertNotNull(new UserService(userRepository, roleRepository, statusRepository));

        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        UserService service = new UserService(userRepository, roleRepository, statusRepository, passwordHasher);
        User encodedUser = user(7, "encoded");
        encodedUser.setPassword("pbkdf2-value");
        when(userRepository.findByLogin("encoded")).thenReturn(Optional.of(encodedUser));
        when(passwordHasher.isEncoded("pbkdf2-value")).thenReturn(true);
        when(passwordHasher.matches("secret", "pbkdf2-value")).thenReturn(true);

        assertEquals(encodedUser, service.findByLoginAndPassword("encoded", "secret"));
        assertEquals(null, encodedUser.getPassword());
        verify(userRepository, org.mockito.Mockito.never()).updatePassword(encodedUser);

        User invalidUser = user(8, "invalid");
        invalidUser.setPassword("hash");
        when(userRepository.findByLogin("invalid")).thenReturn(Optional.of(invalidUser));
        when(passwordHasher.isEncoded("hash")).thenReturn(true);
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);
        assertEquals(null, service.findByLoginAndPassword("invalid", "wrong"));
        assertEquals(null, service.findByLoginAndPassword("missing", "password"));

        User alreadyEncoded = user(9, "saved");
        alreadyEncoded.setPassword("encoded-save");
        when(passwordHasher.isEncoded("encoded-save")).thenReturn(true);
        service.save(alreadyEncoded);
        verify(passwordHasher, org.mockito.Mockito.never()).hash("encoded-save");
        verify(userRepository).save(alreadyEncoded);
    }

    @Test
    public void resultServiceMapsResultsAndCalculatesScore() {
        QuizRepository quizRepository = mock(QuizRepository.class);
        AttemptService attemptService = mock(AttemptService.class);
        UserService userService = mock(UserService.class);
        ResultService service = new ResultService(quizRepository, attemptService, userService);

        Attempt attempt = attempt(30, 10, 6, 50);
        User user = user(6, "alice");
        Quiz quiz = quiz(10, "Java", 1, 1, 15);
        Attempt duplicate = attempt(31, 10, 6, 80);
        LocalDateTime from = LocalDateTime.parse("2026-08-01T10:00");
        LocalDateTime to = LocalDateTime.parse("2026-08-02T10:00");
        when(attemptService.getAllAttempts()).thenReturn(List.of(attempt, duplicate));
        when(attemptService.getAllAttemptsBetweenFinishDates(from, to)).thenReturn(List.of(attempt));
        when(attemptService.findAllAttemptsPerUser(6)).thenReturn(List.of(attempt));
        when(userService.findUserById(6)).thenReturn(Optional.of(user));
        when(quizRepository.findById(10)).thenReturn(Optional.of(quiz));

        List<ResultDto> all = service.getAllResults();
        assertEquals("alice", all.get(0).getUsername());
        assertEquals("Java", service.getAllResultsBetweenFinishDates(from, to).get(0).getQuizName());
        assertEquals(30, service.getAllResultsByUserId(6).get(0).getAttemptId());
        verify(quizRepository, org.mockito.Mockito.times(3)).findById(10);
        verify(userService, org.mockito.Mockito.times(2)).findUserById(6);
        assertThrows(IllegalArgumentException.class,
                () -> service.getAllResultsBetweenFinishDates(to, from));
        assertThrows(IllegalArgumentException.class, () -> service.getAllResultsByUserId(0));
    }

    private static Answer answer(int id, int questionId, boolean correct) {
        Answer answer = new Answer();
        answer.setId(id);
        answer.setQuestionId(questionId);
        answer.setCorrect(correct);
        return answer;
    }

    private static Attempt attempt(int id, int quizId, int userId, int score) {
        Attempt attempt = new Attempt();
        attempt.setId(id);
        attempt.setQuizId(quizId);
        attempt.setUserId(userId);
        attempt.setScore(score);
        attempt.setEndTime(new Date(1_700_000_000_000L));
        return attempt;
    }

    private static Level level(int id, String name) {
        Level level = new Level();
        level.setId(id);
        level.setLevelName(name);
        return level;
    }

    private static Question question(int id, String name, int quizId) {
        Question question = new Question();
        question.setId(id);
        question.setQuestion(name);
        question.setQuizId(quizId);
        return question;
    }

    private static Quiz quiz(int id, String name, int levelId, int subjectId, int time) {
        Quiz quiz = new Quiz();
        quiz.setId(id);
        quiz.setName(name);
        quiz.setLevelId(levelId);
        quiz.setSubjectId(subjectId);
        quiz.setTimeToPass(time);
        return quiz;
    }

    private static Subject subject(int id, String name) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        return subject;
    }

    private static User user(int id, String login) {
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        user.setFirstName(login);
        user.setLastName("User");
        user.setPassword("secret");
        user.setRegisterDateTime(new Date(1_700_000_000_000L));
        user.setLoginDateTime(new Date(1_700_000_100_000L));
        user.setRoleId(2);
        user.setStatusId(1);
        return user;
    }
}
