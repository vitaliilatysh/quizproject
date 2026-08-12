package ua.nure.latysh.quizzes.entities;

import org.junit.Test;
import ua.nure.latysh.quizzes.dto.ProfileDto;
import ua.nure.latysh.quizzes.dto.QuizDto;
import ua.nure.latysh.quizzes.dto.ResultDto;
import ua.nure.latysh.quizzes.dto.UserDto;
import ua.nure.latysh.quizzes.exceptions.RepositoryException;

import java.lang.reflect.Method;
import java.util.Date;

import static org.junit.Assert.assertEquals;

public class PojoCoverageTest {

    @Test
    public void allPropertiesRoundTripThroughAccessors() throws Exception {
        Class<?>[] types = {
                ProfileDto.class, QuizDto.class, ResultDto.class, UserDto.class,
                Answer.class, Attempt.class, Level.class, Question.class, Quiz.class,
                Result.class, Role.class, Status.class, Subject.class, User.class
        };

        for (Class<?> type : types) {
            Object target = type.getConstructor().newInstance();
            for (Method setter : type.getDeclaredMethods()) {
                if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) {
                    continue;
                }
                Object value = sampleValue(setter.getParameterTypes()[0]);
                setter.invoke(target, value);

                String property = setter.getName().substring(3);
                String prefix = setter.getParameterTypes()[0] == boolean.class ? "is" : "get";
                Method getter = type.getMethod(prefix + property);
                assertEquals(type.getSimpleName() + "." + property, value, getter.invoke(target));
            }
        }

        RepositoryException repositoryException = new RepositoryException("database", new Exception("cause"));
        assertEquals("database", repositoryException.getMessage());
    }

    private Object sampleValue(Class<?> type) {
        if (type == int.class) {
            return 42;
        }
        if (type == boolean.class) {
            return true;
        }
        if (type == String.class) {
            return "value";
        }
        if (type == Date.class) {
            return new Date(1_700_000_000_000L);
        }
        throw new IllegalArgumentException("Unsupported property type: " + type);
    }
}
