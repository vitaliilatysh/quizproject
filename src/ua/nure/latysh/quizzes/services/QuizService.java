package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.dto.QuizDto;
import ua.nure.latysh.quizzes.entities.Level;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.repositories.LevelRepository;
import ua.nure.latysh.quizzes.repositories.QuestionRepository;
import ua.nure.latysh.quizzes.repositories.QuizRepository;
import ua.nure.latysh.quizzes.repositories.SubjectRepository;
import ua.nure.latysh.quizzes.repositories.impl.LevelRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.impl.QuestionRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.impl.QuizRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.impl.SubjectRepositoryImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuizService {

    private final QuizRepository quizRepository;
    private final SubjectRepository subjectRepository;
    private final LevelRepository levelRepository;
    private final QuestionRepository questionRepository;

    public QuizService() {
        this(new QuizRepositoryImpl(), new SubjectRepositoryImpl(),
                new LevelRepositoryImpl(), new QuestionRepositoryImpl());
    }

    public QuizService(QuizRepository quizRepository,
                       SubjectRepository subjectRepository,
                       LevelRepository levelRepository,
                       QuestionRepository questionRepository) {
        this.quizRepository = quizRepository;
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.questionRepository = questionRepository;
    }

    public List<QuizDto> getAllQuizzes() {
        return convertToDto(quizRepository.findAll());
    }

    public boolean addQuiz(QuizDto quizDto) {
        return quizRepository.save(toEntity(quizDto));
    }

    public void deleteQuiz(Quiz quiz) {
        quizRepository.delete(quiz);
    }

    public Optional<Quiz> findQuizById(int quizId) {
        return quizRepository.findById(quizId);
    }

    public Optional<Quiz> findQuizByName(String quizName) {
        return quizRepository.findByName(quizName);
    }

    public void updateQuiz(QuizDto quizDto) {
        quizRepository.update(toEntity(quizDto));
    }

    public List<Quiz> findQuizzesBySubjectId(int subjectId) {
        return quizRepository.findBySubjectId(subjectId);
    }

    public List<QuizDto> findQuizBySubjectName(String subjectName) {
        return convertToDto(quizRepository.findBySubjectName(subjectName));
    }

    private List<QuizDto> convertToDto(List<Quiz> quizzes) {
        List<QuizDto> quizDtos = new ArrayList<>();
        for (Quiz quiz : quizzes) {
            Subject subject = RequiredEntity.get(subjectRepository.findById(quiz.getSubjectId()),
                    "Subject " + quiz.getSubjectId());
            Level level = RequiredEntity.get(levelRepository.findById(quiz.getLevelId()),
                    "Level " + quiz.getLevelId());
            List<Question> questionsPerQuiz = questionRepository.findAllByQuizId(quiz.getId());

            QuizDto quizDto = new QuizDto();
            quizDto.setId(quiz.getId());
            quizDto.setName(quiz.getName());
            quizDto.setSubjectName(subject.getName());
            quizDto.setTimeToPass(quiz.getTimeToPass());
            quizDto.setComplexity(level.getLevelName());
            quizDto.setTotalQuestionsNumber(questionsPerQuiz.size());
            quizDtos.add(quizDto);
        }
        return quizDtos;
    }

    private Quiz toEntity(QuizDto quizDto) {
        Level level = RequiredEntity.get(levelRepository.findByName(quizDto.getComplexity()),
                "Level " + quizDto.getComplexity());
        Subject subject = RequiredEntity.get(subjectRepository.findByName(quizDto.getSubjectName()),
                "Subject " + quizDto.getSubjectName());

        Quiz quiz = new Quiz();
        quiz.setId(quizDto.getId());
        quiz.setName(quizDto.getName());
        quiz.setLevelId(level.getId());
        quiz.setSubjectId(subject.getId());
        quiz.setTimeToPass(quizDto.getTimeToPass());
        return quiz;
    }
}
