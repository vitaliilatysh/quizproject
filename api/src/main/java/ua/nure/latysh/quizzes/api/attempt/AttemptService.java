package ua.nure.latysh.quizzes.api.attempt;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Transactional boundary for the three attempt use cases exposed by the API.
 *
 * <p>The small facade is deliberate. Starting, reading and completing an
 * attempt have different dependencies and reasons to change, so their work is
 * delegated to focused package-private collaborators. Keeping the transaction
 * here still guarantees that every repository call made by one request shares
 * the same repeatable-read snapshot.
 *
 * <p>Write methods repeat the isolation level because a method annotation
 * replaces, rather than augments, the class-level annotation.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AttemptService {
    private final AttemptStarter starter;
    private final AttemptFinder finder;
    private final AttemptCompleter completer;

    AttemptService(AttemptStarter starter, AttemptFinder finder, AttemptCompleter completer) {
        this.starter = starter;
        this.finder = finder;
        this.completer = completer;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AttemptResponse start(int quizId, String username) {
        return starter.start(quizId, username);
    }

    public AttemptResponse findOwned(int attemptId, String username) {
        return finder.findOwned(attemptId, username);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AttemptCompletionResponse complete(int attemptId, String username, Set<Integer> answerIds) {
        return completer.complete(attemptId, username, answerIds);
    }
}
