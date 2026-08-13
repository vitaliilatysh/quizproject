package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.repositories.AnswerRepository;
import ua.nure.latysh.quizzes.repositories.QuestionRepository;
import ua.nure.latysh.quizzes.repositories.QuizRepository;
import ua.nure.latysh.quizzes.repositories.impl.AnswerRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.impl.QuestionRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.impl.QuizRepositoryImpl;

import java.util.List;
import java.util.Optional;

public class QuestionService {
    private static final int ANSWERS_PER_QUESTION = 4;
    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final AnswerRepository answerRepository;

    public QuestionService() {
        this(new QuestionRepositoryImpl(), new QuizRepositoryImpl(), new AnswerRepositoryImpl());
    }

    public QuestionService(QuestionRepository questionRepository, QuizRepository quizRepository) {
        this(questionRepository, quizRepository, null);
    }

    public QuestionService(QuestionRepository questionRepository, QuizRepository quizRepository,
                           AnswerRepository answerRepository) {
        this.questionRepository = questionRepository;
        this.quizRepository = quizRepository;
        this.answerRepository = answerRepository;
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

    public Question createQuestion(String questionName, int quizId, List<Answer> answers) {
        Quiz foundQuiz = RequiredEntity.get(quizRepository.findById(quizId), "Quiz " + quizId);
        validateAnswers(answers, false);
        Question question = new Question();
        question.setQuestion(questionName);
        question.setQuizId(foundQuiz.getId());
        return questionRepository.createWithAnswers(question, answers);
    }

    public void updateQuestion(int questionId, String questionName, List<Answer> answers) {
        Question question = RequiredEntity.get(questionRepository.findById(questionId), "Question " + questionId);
        validateAnswers(answers, true);
        question.setQuestion(questionName);
        questionRepository.updateWithAnswers(question, answers);
    }

    public QuestionDetails getQuestionDetails(int questionId) {
        Question question = RequiredEntity.get(questionRepository.findById(questionId), "Question " + questionId);
        if (answerRepository == null) {
            throw new IllegalStateException("Answer repository is required for question details");
        }
        List<Answer> answers = answerRepository.findAllByQuestionId(questionId);
        validateAnswers(answers, true);
        return new QuestionDetails(question, List.copyOf(answers));
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

    private void validateAnswers(List<Answer> answers, boolean idsRequired) {
        if (answers == null || answers.size() != ANSWERS_PER_QUESTION) {
            throw new IllegalArgumentException("A question must have exactly four answers");
        }
        if (answers.stream().noneMatch(Answer::isCorrect)) {
            throw new IllegalArgumentException("A question must have at least one correct answer");
        }
        if (answers.stream().anyMatch(answer -> answer.getAnswer() == null || answer.getAnswer().isBlank())) {
            throw new IllegalArgumentException("Answers must not be blank");
        }
        if (idsRequired && answers.stream().anyMatch(answer -> answer.getId() <= 0)) {
            throw new IllegalArgumentException("Persisted answers must have identifiers");
        }
    }

    public record QuestionDetails(Question question, List<Answer> answers) {
    }
}

