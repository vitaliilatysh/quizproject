package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.repositories.QuestionRepository;
import ua.nure.latysh.quizzes.repositories.QuizRepository;
import ua.nure.latysh.quizzes.repositories.impl.QuestionRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.impl.QuizRepositoryImpl;

import java.util.List;

public class QuestionService {
    private QuestionRepository questionRepository;
    private QuizRepository quizRepository = new QuizRepositoryImpl();

    public QuestionService() {
        this.questionRepository = new QuestionRepositoryImpl();
    }

    public List<Question> getAllQuestions() {
        return questionRepository.findAll();
    }

    public Question addQuestion(String questionName, int quizId) {
        Quiz foundQuiz = quizRepository.findById(quizId);
        Question newQuestion = new Question();
        newQuestion.setQuestion(questionName);
        newQuestion.setQuizId(foundQuiz.getId());
        return questionRepository.saveQuestion(newQuestion);
    }

    public void deleteQuestion(Question question) {
        questionRepository.delete(question);
    }

    public Question findQuestionById(int questionId) {
        return questionRepository.findById(questionId);
    }

    public Question findQuestionByName(String questionName) {
        return questionRepository.findByName(questionName);
    }

    public void updateQuestion(Question question) {
        questionRepository.update(question);
    }

    public List<Question> findQuestionsByQuizId(int quizId) {
        return questionRepository.findAllByQuizId(quizId);
    }
}
