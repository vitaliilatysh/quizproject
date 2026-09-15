package ua.nure.latysh.quizzes.api.attempt;

import org.springframework.stereotype.Component;
import ua.nure.latysh.quizzes.api.domain.Attempt;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

/** Reads an attempt only through its ownership boundary. */
@Component
final class AttemptFinder {
    private final AttemptRepository attemptRepository;
    private final AttemptSnapshotStore snapshots;

    AttemptFinder(AttemptRepository attemptRepository, AttemptSnapshotStore snapshots) {
        this.attemptRepository = attemptRepository;
        this.snapshots = snapshots;
    }

    AttemptResponse findOwned(int attemptId, String username) {
        Attempt attempt = attemptRepository.findByIdAndUserLogin(attemptId, username)
                .orElseThrow(() -> missingAttempt(attemptId));
        return snapshots.toResponse(attempt, snapshots.find(attempt));
    }

    private static ResourceNotFoundException missingAttempt(int attemptId) {
        return new ResourceNotFoundException("Attempt " + attemptId + " was not found");
    }
}
