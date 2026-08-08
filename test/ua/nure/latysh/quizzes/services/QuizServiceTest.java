package ua.nure.latysh.quizzes.services;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import ua.nure.latysh.quizzes.dto.QuizDto;
import ua.nure.latysh.quizzes.entities.Level;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.repositories.LevelRepository;
import ua.nure.latysh.quizzes.repositories.QuestionRepository;
import ua.nure.latysh.quizzes.repositories.QuizRepository;
import ua.nure.latysh.quizzes.repositories.SubjectRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuizServiceTest {

    private QuizRepository quizRepository;
    private SubjectRepository subjectRepository;
    private LevelRepository levelRepository;
    private QuestionRepository questionRepository;
    private QuizService quizService;

    @Before
    public void setUp() {
        quizRepository = mock(QuizRepository.class);
        subjectRepository = mock(SubjectRepository.class);
        levelRepository = mock(LevelRepository.class);
        questionRepository = mock(QuestionRepository.class);
        quizService = new QuizService(
                quizRepository, subjectRepository, levelRepository, questionRepository);
    }

    @Test
    public void getAllQuizzesMapsEntitiesAndLoadsThemOnce() {
        Quiz quiz = quiz(7, "Java basics", 2, 3, 20);
        Subject subject = subject(3, "Java");
        Level level = level(2, "Easy");
        List<Question> questions = Arrays.asList(new Question(), new Question());

        when(quizRepository.findAll()).thenReturn(Collections.singletonList(quiz));
        when(subjectRepository.findById(3)).thenReturn(subject);
        when(levelRepository.findById(2)).thenReturn(level);
        when(questionRepository.findAllByQuizId(7)).thenReturn(questions);

        List<QuizDto> result = quizService.getAllQuizzes();

        assertEquals(1, result.size());
        QuizDto dto = result.get(0);
        assertEquals(7, dto.getId());
        assertEquals("Java basics", dto.getName());
        assertEquals("Java", dto.getSubjectName());
        assertEquals("Easy", dto.getComplexity());
        assertEquals(20, dto.getTimeToPass());
        assertEquals(2, dto.getTotalQuestionsNumber());
        verify(quizRepository, times(1)).findAll();
    }

    @Test
    public void addQuizConvertsDtoBeforeSaving() {
        QuizDto dto = dto(0, "SQL", "Medium", "Databases", 15);
        when(levelRepository.findByName("Medium")).thenReturn(level(4, "Medium"));
        when(subjectRepository.findByName("Databases")).thenReturn(subject(8, "Databases"));
        when(quizRepository.save(org.mockito.ArgumentMatchers.any(Quiz.class))).thenReturn(true);

        boolean saved = quizService.addQuiz(dto);

        ArgumentCaptor<Quiz> captor = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepository).save(captor.capture());
        Quiz quiz = captor.getValue();
        assertTrue(saved);
        assertEquals("SQL", quiz.getName());
        assertEquals(4, quiz.getLevelId());
        assertEquals(8, quiz.getSubjectId());
        assertEquals(15, quiz.getTimeToPass());
    }

    @Test
    public void updateQuizPreservesIdWhileConvertingDto() {
        QuizDto dto = dto(11, "HTTP", "Hard", "Web", 30);
        when(levelRepository.findByName("Hard")).thenReturn(level(5, "Hard"));
        when(subjectRepository.findByName("Web")).thenReturn(subject(9, "Web"));

        quizService.updateQuiz(dto);

        ArgumentCaptor<Quiz> captor = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepository).update(captor.capture());
        assertEquals(11, captor.getValue().getId());
    }

    private Quiz quiz(int id, String name, int levelId, int subjectId, int timeToPass) {
        Quiz quiz = new Quiz();
        quiz.setId(id);
        quiz.setName(name);
        quiz.setLevelId(levelId);
        quiz.setSubjectId(subjectId);
        quiz.setTimeToPass(timeToPass);
        return quiz;
    }

    private Subject subject(int id, String name) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        return subject;
    }

    private Level level(int id, String name) {
        Level level = new Level();
        level.setId(id);
        level.setLevelName(name);
        return level;
    }

    private QuizDto dto(int id, String name, String complexity, String subjectName, int timeToPass) {
        QuizDto dto = new QuizDto();
        dto.setId(id);
        dto.setName(name);
        dto.setComplexity(complexity);
        dto.setSubjectName(subjectName);
        dto.setTimeToPass(timeToPass);
        return dto;
    }
}
