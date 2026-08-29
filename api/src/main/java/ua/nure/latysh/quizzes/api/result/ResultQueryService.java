package ua.nure.latysh.quizzes.api.result;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;

/**
 * Reads run in one read-only transaction so that every statement a request
 * issues sees the same snapshot. Without this each repository call opened its
 * own session on its own connection, so a multi-query read could observe a
 * database that changed underneath it.
 *
 * <p>The isolation level is pinned rather than inherited because sharing a
 * transaction is not by itself enough: under {@code READ COMMITTED} every
 * statement takes a fresh snapshot, so a concurrent commit is still visible
 * between two queries of the same read. MySQL defaults to repeatable read and
 * would behave correctly by accident; stating it here keeps the guarantee from
 * depending on how a given database happens to be configured.
 *
 * <p>This service only reads. A method that writes must override the class
 * annotation with its own {@code @Transactional}: under a read-only
 * transaction Hibernate never flushes, so a modified entity is discarded
 * without an error.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ResultQueryService {
    private final AttemptRepository attemptRepository;

    public ResultQueryService(AttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    public Page<ResultResponse> findCompletedByUsername(String username, Pageable pageable) {
        return attemptRepository.findCompletedByUsername(username, pageable)
                .map(attempt -> new ResultResponse(
                        attempt.getId(),
                        attempt.getQuiz().getId(),
                        attempt.getQuiz().getName(),
                        attempt.getScore(),
                        attempt.getEndTime()));
    }
}
