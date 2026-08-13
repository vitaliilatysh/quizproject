package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.dto.ResultDto;
import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.repositories.QuizRepository;
import ua.nure.latysh.quizzes.repositories.impl.QuizRepositoryImpl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ResultService {
    private static final DateTimeFormatter FINISH_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final QuizRepository quizRepository;
    private final AttemptService attemptService;
    private final UserService userService;

    public ResultService() {
        this(new QuizRepositoryImpl(), new AttemptService(), new UserService());
    }

    public ResultService(QuizRepository quizRepository,
                         AttemptService attemptService,
                         UserService userService) {
        this.quizRepository = quizRepository;
        this.attemptService = attemptService;
        this.userService = userService;
    }

    public List<ResultDto> getAllResults() {
        return mapAttempts(attemptService.getAllAttempts(), true);
    }

    public List<ResultDto> getAllResultsBetweenFinishDates(LocalDateTime startRange, LocalDateTime endRange) {
        if (startRange.isAfter(endRange)) {
            throw new IllegalArgumentException("Start date must not be after end date");
        }
        return mapAttempts(attemptService.getAllAttemptsBetweenFinishDates(startRange, endRange), true);
    }

    public List<ResultDto> getAllResultsByUserId(int userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User id must be positive");
        }
        return mapAttempts(attemptService.findAllAttemptsPerUser(userId), false);
    }

    private List<ResultDto> mapAttempts(List<Attempt> attempts, boolean includeUsername) {
        Map<Integer, String> quizNames = new HashMap<>();
        Map<Integer, String> usernames = new HashMap<>();
        return attempts.stream().map(attempt -> {
            ResultDto dto = new ResultDto();
            dto.setAttemptId(attempt.getId());
            dto.setQuizName(quizNames.computeIfAbsent(attempt.getQuizId(), this::findQuizName));
            dto.setQuizScore(attempt.getScore());
            dto.setEndTime(FINISH_TIME_FORMAT.format(attempt.getEndTime().toInstant()));
            if (includeUsername) {
                dto.setUsername(usernames.computeIfAbsent(attempt.getUserId(), this::findUsername));
            }
            return dto;
        }).toList();
    }

    private String findQuizName(int quizId) {
        Quiz quiz = RequiredEntity.get(quizRepository.findById(quizId), "Quiz " + quizId);
        return quiz.getName();
    }

    private String findUsername(int userId) {
        User user = RequiredEntity.get(userService.findUserById(userId), "User " + userId);
        return user.getLogin();
    }
}
