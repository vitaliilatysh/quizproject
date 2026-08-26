package ua.nure.latysh.quizzes.api.result;

import org.springframework.stereotype.Service;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;

import java.util.List;

@Service
public class ResultQueryService {
    private final AttemptRepository attemptRepository;

    public ResultQueryService(AttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    public List<ResultResponse> findCompletedByUsername(String username) {
        return attemptRepository.findCompletedByUsername(username).stream()
                .map(attempt -> new ResultResponse(
                        attempt.getId(),
                        attempt.getQuiz().getId(),
                        attempt.getQuiz().getName(),
                        attempt.getScore(),
                        attempt.getEndTime()))
                .toList();
    }
}
