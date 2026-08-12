package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.repositories.QuestionRepository;
import ua.nure.latysh.quizzes.repositories.QuizRepository;
import ua.nure.latysh.quizzes.repositories.impl.QuestionRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.impl.QuizRepositoryImpl;

import java.util.List;
import java.util.Optional;

public class QuestionService {
    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;

    public QuestionService() {
        this(new QuestionRepositoryImpl(), new QuizRepositoryImpl());
    }

    public QuestionService(QuestionRepository questionRepository, QuizRepository quizRepository) {
        this.questionRepository = questionRepository;
        this.quizRepository = quizRepository;
    }

    public List<Question> getAllQuestions() {
        return questionRepository.findAll();
    }

    public Question addQuestion(String questionName, int quizId) {
        Quiz foundQuiz = RequiredEntity.get(quizRepository.findById(quizId), "Quiz " + quizId);
        Question newQuestion = new Question();
        newQuestion.setQuestion(questionName);
        newQuestion.setQuizId(foundQuiz.getId());
        return questionRepository.saveQuestion(newQuestion);
    }

    public void deleteQuestion(Question question) {
        questionRepository.delete(question);
    }

    public Optional<Question> findQuestionById(int questionId) {
        return questionRepository.findById(questionId);
    }

    public Optional<Question> findQuestionByName(String questionName) {
        return questionRepository.findByName(questionName);
    }

    public void updateQuestion(Question question) {
        questionRepository.update(question);
    }

    public List<Question> findQuestionsByQuizId(int quizId) {
        return questionRepository.findAllByQuizId(quizId);
    }
}
