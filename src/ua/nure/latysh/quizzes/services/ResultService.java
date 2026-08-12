package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.dto.ResultDto;
import ua.nure.latysh.quizzes.entities.*;
import ua.nure.latysh.quizzes.repositories.AnswerRepository;
import ua.nure.latysh.quizzes.repositories.QuestionRepository;
import ua.nure.latysh.quizzes.repositories.QuizRepository;
import ua.nure.latysh.quizzes.repositories.ResultRepository;
import ua.nure.latysh.quizzes.repositories.impl.AnswerRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.impl.QuestionRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.impl.QuizRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.impl.ResultRepositoryImpl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ResultService {
    private final QuizRepository quizRepository;
    private final ResultRepository resultRepository;
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final AttemptService attemptService;
    private final UserService userService;

    public ResultService() {
        this(new QuizRepositoryImpl(), new ResultRepositoryImpl(), new AnswerRepositoryImpl(),
                new QuestionRepositoryImpl(), new AttemptService(), new UserService());
    }

    public ResultService(QuizRepository quizRepository,
                         ResultRepository resultRepository,
                         AnswerRepository answerRepository,
                         QuestionRepository questionRepository,
                         AttemptService attemptService,
                         UserService userService) {
        this.quizRepository = quizRepository;
        this.resultRepository = resultRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.attemptService = attemptService;
        this.userService = userService;
    }

    public List<ResultDto> getAllResults(){
        List<ResultDto> resultDtos = new ArrayList<>();
        List<Attempt> attempts = attemptService.getAllAttempts();

        for (Attempt attempt : attempts) {
            ResultDto resultDto = new ResultDto();
            User user = RequiredEntity.get(userService.findUserById(attempt.getUserId()),
                    "User " + attempt.getUserId());
            resultDto.setUsername(user.getLogin());
            resultDto.setQuizName(findQuiz(attempt.getQuizId()).getName());
            resultDto.setQuizScore(attempt.getScore());
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
            resultDto.setEndTime(simpleDateFormat.format(attempt.getEndTime()));
            resultDtos.add(resultDto);
        }
        return resultDtos;
    }

    public List<ResultDto> getAllResultsBetweenFinishDates(String startRange, String endRange){
        List<ResultDto> resultDtos = new ArrayList<>();
        List<Attempt> attempts = attemptService.getAllAttemptsBetweenFinishDates(startRange, endRange);

        for (Attempt attempt : attempts) {
            ResultDto resultDto = new ResultDto();
            User user = RequiredEntity.get(userService.findUserById(attempt.getUserId()),
                    "User " + attempt.getUserId());
            resultDto.setUsername(user.getLogin());
            resultDto.setQuizName(findQuiz(attempt.getQuizId()).getName());
            resultDto.setQuizScore(attempt.getScore());
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
            resultDto.setEndTime(simpleDateFormat.format(attempt.getEndTime()));
            resultDtos.add(resultDto);
        }
        return resultDtos;
    }

    public List<ResultDto> getAllResultsByUserId(int userId) {
        List<ResultDto> resultDtos = new ArrayList<>();
        List<Attempt> attempts = attemptService.findAllAttemptsPerUser(userId);

        for (Attempt attempt : attempts) {
            ResultDto resultDto = new ResultDto();
            resultDto.setAttemptId(attempt.getId());
            resultDto.setQuizName(findQuiz(attempt.getQuizId()).getName());
            resultDto.setQuizScore(attempt.getScore());
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
            resultDto.setEndTime(simpleDateFormat.format(attempt.getEndTime()));
            resultDtos.add(resultDto);
        }
        return resultDtos;
    }

    public boolean saveResult(Result result) {
        return resultRepository.save(result);
    }

    public List<Result> findAllByAttemptId(int attemptId) {
        return resultRepository.findByAttemptId(attemptId);
    }

    public float getResultForQuizByAttemptId(int attemptId) {
        List<Result> results = resultRepository.findByAttemptId(attemptId);
        int totalQuestions = 0;
        int userCorrectQuestions = 0;

        if (results.size() != 0) {
            int questionId = findAnswer(results.get(0).getAnswerId()).getQuestionId();

            Question question = RequiredEntity.get(questionRepository.findById(questionId),
                    "Question " + questionId);
            int quizId = question.getQuizId();
            List<Question> questions = questionRepository.findAllByQuizId(quizId);
            totalQuestions = questions.size();

            for (Question q : questions) {
                List<Answer> answersPerQuestion = answerRepository.findAllByQuestionId(q.getId());

                List<Answer> correctAnswersPerQuestion = new ArrayList<>();
                List<Answer> userAnswersPerQuestion = new ArrayList<>();
                List<Answer> userCorrectAnswersList = new ArrayList<>();

                for (Result result : results) {
                    Answer answer = findAnswer(result.getAnswerId());
                    if (answer.getQuestionId() == q.getId()) {
                        userAnswersPerQuestion.add(answer);
                    }
                }
                for (Answer answer : userAnswersPerQuestion) {
                    if (answer.isCorrect()) {
                        userCorrectAnswersList.add(answer);
                    } else {
                        userCorrectAnswersList.clear();
                        break;
                    }
                }

                for (Answer answer : answersPerQuestion) {
                    if (answer.isCorrect()) {
                        correctAnswersPerQuestion.add(answer);
                    }
                }

                if (userCorrectAnswersList.size() == correctAnswersPerQuestion.size()) {
                    ++userCorrectQuestions;
                }

            }
        }

        return (userCorrectQuestions / (float) totalQuestions) * 100;
    }

    private Quiz findQuiz(int quizId) {
        return RequiredEntity.get(quizRepository.findById(quizId), "Quiz " + quizId);
    }

    private Answer findAnswer(int answerId) {
        return RequiredEntity.get(answerRepository.findById(answerId), "Answer " + answerId);
    }
}
