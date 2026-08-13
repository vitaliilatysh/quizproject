package ua.nure.latysh.quizzes.servlets;

import org.junit.Test;
import ua.nure.latysh.quizzes.dto.QuizDto;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RequestParametersTest {

    @Test
    public void utilityConstructorAndTypedValuesAreCovered() throws Exception {
        Constructor<RequestParameters> constructor = RequestParameters.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("text")).thenReturn(" value ");
        when(request.getParameter("number")).thenReturn(" 12 ");

        assertEquals("value", RequestParameters.requiredText(request, "text"));
        assertEquals(12, RequestParameters.positiveInt(request, "number"));
    }

    @Test
    public void missingBlankAndMalformedValuesAreRejected() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("missing")).thenReturn(null);
        when(request.getParameter("blank")).thenReturn("   ");
        when(request.getParameter("zero")).thenReturn("0");
        when(request.getParameter("malformed")).thenReturn("twelve");

        assertBadRequest(request, "missing", false, "Missing or blank parameter: missing");
        assertBadRequest(request, "blank", false, "Missing or blank parameter: blank");
        assertBadRequest(request, "zero", true, "Parameter must be positive: zero");
        assertBadRequest(request, "malformed", true, "Parameter must be an integer: malformed");
    }

    @Test
    public void quizFormsProduceTypedDtos() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("quiz")).thenReturn("7");
        when(request.getParameter("quizName")).thenReturn(" Quiz ");
        when(request.getParameter("subjectName")).thenReturn(" Java ");
        when(request.getParameter("complexity")).thenReturn(" Easy ");
        when(request.getParameter("time")).thenReturn("10");

        QuizDto created = QuizServlet.QuizForm.forCreate(request).toDto();
        QuizDto updated = QuizServlet.QuizForm.forUpdate(request).toDto();

        assertEquals(0, created.getId());
        assertEquals(7, updated.getId());
        assertEquals("Quiz", updated.getName());
        assertEquals("Java", updated.getSubjectName());
        assertEquals("Easy", updated.getComplexity());
        assertEquals(10, updated.getTimeToPass());
    }

    private static void assertBadRequest(HttpServletRequest request,
                                         String name,
                                         boolean integer,
                                         String expectedMessage) throws Exception {
        try {
            if (integer) {
                RequestParameters.positiveInt(request, name);
            } else {
                RequestParameters.requiredText(request, name);
            }
            fail("Expected BadRequestException");
        } catch (BadRequestException exception) {
            assertEquals(expectedMessage, exception.getMessage());
        }
    }
}
