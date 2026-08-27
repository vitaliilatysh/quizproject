package ua.nure.latysh.quizzes.api.result;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;

@Service
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
