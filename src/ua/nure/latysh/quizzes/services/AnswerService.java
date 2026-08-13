package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.repositories.AnswerRepository;
import ua.nure.latysh.quizzes.repositories.impl.AnswerRepositoryImpl;

import java.util.List;
import java.util.Optional;

public class AnswerService {
    private final AnswerRepository answerRepository;

    public AnswerService() {
        this(new AnswerRepositoryImpl());
    }

    public AnswerService(AnswerRepository answerRepository) {
        this.answerRepository = answerRepository;
    }

    public List<Answer> getAllAnswers() {
        return answerRepository.findAll();
    }

    public boolean saveAnswer(Answer answer) {
        return answerRepository.save(answer);
    }

    public Optional<Answer> findAnswerById(int answerId) {
        return answerRepository.findById(answerId);
    }

    public void updateAnswer(Answer answer) {
        answerRepository.update(answer);
    }

    public List<Answer> findAnswersByQuestionId(int questionId) {
        return answerRepository.findAllByQuestionId(questionId);
    }
}
