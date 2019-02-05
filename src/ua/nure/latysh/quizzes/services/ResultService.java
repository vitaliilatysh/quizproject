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
    private QuizRepository quizRepository = new QuizRepositoryImpl();
    private ResultRepository resultRepository = new ResultRepositoryImpl();
    private AnswerRepository answerRepository = new AnswerRepositoryImpl();
    private QuestionRepository questionRepository = new QuestionRepositoryImpl();
    private AttemptService attemptService = new AttemptService();
    private UserService userService = new UserService();

    public ResultService() {
        this.resultRepository = new ResultRepositoryImpl();
    }

    public List<ResultDto> getAllResults(){
        List<ResultDto> resultDtos = new ArrayList<>();
        List<Attempt> attempts = attemptService.getAllAttempts();

        for (Attempt attempt : attempts) {
            ResultDto resultDto = new ResultDto();
            User user = userService.findUserById(attempt.getUserId());
            resultDto.setUsername(user.getLogin());
            resultDto.setQuizName(quizRepository.findById(attempt.getQuizId()).getName());
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
            User user = userService.findUserById(attempt.getUserId());
            resultDto.setUsername(user.getLogin());
            resultDto.setQuizName(quizRepository.findById(attempt.getQuizId()).getName());
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
            resultDto.setQuizName(quizRepository.findById(attempt.getQuizId()).getName());
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
        List<Question> questions = null;
        int totalQuestions = 0;
        int userCorrectQuestions = 0;

        if (results.size() != 0) {
            int questionId = answerRepository.findById(results.get(0).getAnswerId()).getQuestionId();

            Question question = questionRepository.findById(questionId);
            int quizId = question.getQuizId();
            questions = questionRepository.findAllByQuizId(quizId);
            totalQuestions = questions.size();

            for (Question q : questions) {
                List<Answer> answersPerQuestion = answerRepository.findAllByQuestionId(q.getId());

                List<Answer> correctAnswersPerQuestion = new ArrayList<>();
                List<Answer> userAnswersPerQuestion = new ArrayList<>();
                List<Answer> userCorrectAnswersList = new ArrayList<>();

                for (Result result : results) {
                    if (answerRepository.findById(result.getAnswerId()).getQuestionId() == q.getId()) {
                        userAnswersPerQuestion.add(answerRepository.findById(result.getAnswerId()));
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
}
