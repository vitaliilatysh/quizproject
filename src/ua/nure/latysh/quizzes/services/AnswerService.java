package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.repositories.AnswerRepository;
import ua.nure.latysh.quizzes.repositories.impl.AnswerRepositoryImpl;

import java.util.List;

public class AnswerService {
    private AnswerRepository answerRepository;

    public AnswerService() {
        this.answerRepository = new AnswerRepositoryImpl();
    }

    public List<Answer> getAllAnswers() {
        return answerRepository.findAll();
    }

    public boolean saveAnswer(Answer answer) {
        return answerRepository.save(answer);
    }

    //
//    public void deleteQuestion(Question question) {
//        questionRepository.delete(question);
//    }
//
    public Answer findAnswerById(int answerId) {
        return answerRepository.findById(answerId);
    }

    public void updateAnswer(Answer answer) {
        answerRepository.update(answer);
    }

    public List<Answer> findAnswersByQuestionId(int questionId) {
        return answerRepository.findAllByQuestionId(questionId);
    }
}
