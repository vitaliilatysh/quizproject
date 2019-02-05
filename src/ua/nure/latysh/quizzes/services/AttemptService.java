package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.repositories.AttemptRepository;
import ua.nure.latysh.quizzes.repositories.impl.AttemptRepositoryImpl;

import java.util.Date;
import java.util.List;

public class AttemptService {
    private AttemptRepository attemptRepository;

    public AttemptService() {
        this.attemptRepository = new AttemptRepositoryImpl();
    }

    public List<Attempt> getAllAttempts() {
        return attemptRepository.findAll();
    }

    public List<Attempt> getAllAttemptsBetweenFinishDates(String startRange, String endRange) {
        return attemptRepository.findAllBetweenFinishDates(startRange, endRange);
    }

    public List<Attempt> findAllAttemptsPerUser(int userId) {
        return attemptRepository.findAllByUserId(userId);
    }

    public boolean saveAttempt(Attempt attempt) {
        return attemptRepository.save(attempt);
    }

    public Attempt findTheLatestForUser(User user) {
        return attemptRepository.findLastByUserId(user.getId());
    }

    public void updateAttemptByScore(Attempt attempt) {
        attemptRepository.update(attempt);
    }

//
//    public void deleteQuestion(Question question) {
//        questionRepository.delete(question);
//    }
////
//    public Answer findAnswerById(int answerId) {
//        return answerRepository.findById(answerId);
//    }
////
////    public void updateQuestion(Question question) {
////        questionRepository.update(question);
////    }
//
//    public List<Answer> findAnswersByQuestionId(int questionId) {
//        return answerRepository.findAllByQuestionId(questionId);
//    }
}
