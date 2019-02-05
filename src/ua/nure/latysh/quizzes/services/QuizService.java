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

public class QuizService {

    private QuizRepository quizRepository;
    private SubjectRepository subjectRepository = new SubjectRepositoryImpl();
    private LevelRepository levelRepository = new LevelRepositoryImpl();
    private QuestionRepository questionRepository = new QuestionRepositoryImpl();

    public QuizService() {
        this.quizRepository = new QuizRepositoryImpl();
    }

    public List<QuizDto> getAllQuizzes() {
        List<Quiz> quizzes = quizRepository.findAll();
        convertToDto(quizzes);

        return convertToDto(quizzes);
    }

    public boolean addQuiz(QuizDto quizDto) {
        Level level = levelRepository.findByName(quizDto.getComplexity());
        Subject subject = subjectRepository.findByName(quizDto.getSubjectName());
        Quiz quiz = new Quiz();
        quiz.setName(quizDto.getName());
        quiz.setLevelId(level.getId());
        quiz.setSubjectId(subject.getId());
        quiz.setTimeToPass(quizDto.getTimeToPass());
        return quizRepository.save(quiz);
    }

    public void deleteQuiz(Quiz quiz) {
        quizRepository.delete(quiz);
    }

    public Quiz findQuizById(int quizId) {
        return quizRepository.findById(quizId);
    }

    public Quiz findQuizByName(String quizName) {
        return quizRepository.findByName(quizName);
    }

    public void updateQuiz(QuizDto quizDto) {
        Level level = levelRepository.findByName(quizDto.getComplexity());
        Subject subject = subjectRepository.findByName(quizDto.getSubjectName());
        Quiz quiz = new Quiz();
        quiz.setId(quizDto.getId());
        quiz.setName(quizDto.getName());
        quiz.setLevelId(level.getId());
        quiz.setSubjectId(subject.getId());
        quiz.setTimeToPass(quizDto.getTimeToPass());
        quizRepository.update(quiz);
    }

    public List<Quiz> findQuizzesBySubjectId(int subjectId) {
        return quizRepository.findBySubjectId(subjectId);
    }

    public List<QuizDto> findQuizBySubjectName(String subjectName) {

        List<Quiz> quizzes = quizRepository.findBySubjectName(subjectName);

        return convertToDto(quizzes);
    }

    private List<QuizDto> convertToDto(List<Quiz> quizzes) {
        List<QuizDto> quizDtos = new ArrayList<>();
        for (Quiz quiz : quizzes) {
            QuizDto quizDto = new QuizDto();
            int subjectId = quiz.getSubjectId();
            int levelId = quiz.getLevelId();
            Subject subject = subjectRepository.findById(subjectId);
            Level level = levelRepository.findById(levelId);
            List<Question> questionsPerQuiz = questionRepository.findAllByQuizId(quiz.getId());

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
}
