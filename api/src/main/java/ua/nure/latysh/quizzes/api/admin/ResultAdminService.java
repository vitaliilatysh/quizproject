package ua.nure.latysh.quizzes.api.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.admin.AdminModels.ResultResponse;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;
import ua.nure.latysh.quizzes.api.support.InvalidRequestException;

import java.time.Instant;

/**
 * Completed attempts across every reader, for reporting.
 *
 * <p>Reads only, and one aggregate, which is why it is its own service rather
 * than a method on one of the others: nothing here administers anything. See
 * the package documentation for transactions.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ResultAdminService {
    private final AttemptRepository attemptRepository;

    public ResultAdminService(AttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    public Page<ResultResponse> results(Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("Result range start must not be after its end");
        }
        return attemptRepository.findCompletedInRange(from, to, pageable)
                .map(attempt -> new ResultResponse(
                        attempt.getId(),
                        attempt.getUser().getLogin(),
                        attempt.getQuiz().getId(),
                        attempt.getQuiz().getName(),
                        attempt.getScore(),
                        attempt.getEndTime()));
    }
}
