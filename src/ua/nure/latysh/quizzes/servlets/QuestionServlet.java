package ua.nure.latysh.quizzes.servlets;

import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.services.AttemptService;
import ua.nure.latysh.quizzes.services.QuestionService;
import ua.nure.latysh.quizzes.services.QuizService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ResourceBundle;

@WebServlet(urlPatterns = {"/questions", "/questions/edit", "/questions/add", "/questions/view"})
public class QuestionServlet extends HttpServlet {

    private static final String QUESTIONS_VIEW = "/WEB-INF/views/questions.jsp";
    private static final String LIST_VIEW = "/WEB-INF/views/listQuestions.jsp";
    private static final String ADD_VIEW = "/WEB-INF/views/addQuestion.jsp";
    private static final String EDIT_VIEW = "/WEB-INF/views/editQuestion.jsp";
    private static final String QUESTIONS = "questions";
    private static final String QUIZ = "quiz";
    private static final String QUESTION = "question";
    private static final String QUESTION_ID = "questionId";
    private static final String QUIZ_NOT_FOUND = "Quiz not found: ";
    private static final int QUESTION_MAX_LENGTH = 250;
    private static final int ANSWER_MAX_LENGTH = 50;
    private static final List<String> ANSWER_SUFFIXES = List.of("A", "B", "C", "D");

    private final QuizService quizService;
    private final QuestionService questionService;
    private final AttemptService attemptService;

    public QuestionServlet() {
        this(new QuizService(), new QuestionService(), new AttemptService());
    }

    QuestionServlet(QuizService quizService, QuestionService questionService,
                    AttemptService attemptService) {
        this.quizService = quizService;
        this.questionService = questionService;
        this.attemptService = attemptService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        ServletResponseHandler.forward(request.getRequestDispatcher(QUESTIONS_VIEW), request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        String action = request.getParameter("action");
        try {
            if (action == null) {
                throw new BadRequestException("Missing parameter: action");
            }
            switch (action) {
                case "add" -> showAddForm(request, response);
                case "addQuestion" -> createQuestion(request, response);
                case "run" -> runQuestions(request, response);
                case "view" -> listQuestions(request, response);
                case "edit" -> showEditForm(request, response);
                case "editQuestion" -> updateQuestion(request, response);
                case "delete" -> deleteQuestion(request, response);
                default -> throw new BadRequestException("Unknown action: " + action);
            }
        } catch (BadRequestException exception) {
            ServletResponseHandler.sendError(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (NoSuchElementException exception) {
            ServletResponseHandler.sendError(response, HttpServletResponse.SC_NOT_FOUND, exception.getMessage());
        } catch (RuntimeException exception) {
            ServletResponseHandler.internalError(response, exception);
        }
    }

    private void showAddForm(HttpServletRequest request, HttpServletResponse response)
            throws BadRequestException {
        int quizId = RequestParameters.positiveInt(request, QUIZ);
        if (quizService.findQuizById(quizId).isEmpty()) {
            ServletResponseHandler.sendError(response, HttpServletResponse.SC_NOT_FOUND,
                    QUIZ_NOT_FOUND + quizId);
            return;
        }
        request.setAttribute(QUIZ, quizId);
        request.setAttribute("action", "create");
        ServletResponseHandler.forward(request.getRequestDispatcher(ADD_VIEW), request, response);
    }

    private void createQuestion(HttpServletRequest request, HttpServletResponse response)
            throws BadRequestException {
        QuestionForm form = QuestionForm.from(request, false);
        if (!form.hasCorrectAnswer()) {
            showInvalidForm(request, response, form, ADD_VIEW);
            return;
        }
        questionService.createQuestion(form.question(), form.quizId(), form.toAnswers(List.of()));
        showQuestionList(request, response, form.quizId());
    }

    private void updateQuestion(HttpServletRequest request, HttpServletResponse response)
            throws BadRequestException {
        QuestionForm form = QuestionForm.from(request, true);
        if (!form.hasCorrectAnswer()) {
            showInvalidForm(request, response, form, EDIT_VIEW);
            return;
        }
        QuestionService.QuestionDetails details = questionService.getQuestionDetails(form.questionId());
        if (details.question().getQuizId() != form.quizId()) {
            throw new BadRequestException("Question does not belong to quiz: " + form.quizId());
        }
        List<Integer> answerIds = details.answers().stream().map(Answer::getId).toList();
        questionService.updateQuestion(form.questionId(), form.question(), form.toAnswers(answerIds));
        showQuestionList(request, response, form.quizId());
    }

    private void deleteQuestion(HttpServletRequest request, HttpServletResponse response)
            throws BadRequestException {
        int questionId = RequestParameters.positiveInt(request, QUESTION);
        Optional<Question> question = questionService.findQuestionById(questionId);
        if (question.isEmpty()) {
            ServletResponseHandler.sendError(response, HttpServletResponse.SC_NOT_FOUND,
                    "Question not found: " + questionId);
            return;
        }
        questionService.deleteQuestion(question.get());
        showQuestionList(request, response, question.get().getQuizId());
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws BadRequestException {
        int questionId = RequestParameters.positiveInt(request, QUESTION);
        QuestionService.QuestionDetails details = questionService.getQuestionDetails(questionId);
        Question question = details.question();
        request.setAttribute(QUIZ, question.getQuizId());
        request.setAttribute(QUESTION_ID, question.getId());
        request.setAttribute(QUESTION, question.getQuestion());
        setAnswerAttributes(request, details.answers());
        ServletResponseHandler.forward(request.getRequestDispatcher(EDIT_VIEW), request, response);
    }

    private void listQuestions(HttpServletRequest request, HttpServletResponse response)
            throws BadRequestException {
        int quizId = RequestParameters.positiveInt(request, QUIZ);
        if (quizService.findQuizById(quizId).isEmpty()) {
            ServletResponseHandler.sendError(response, HttpServletResponse.SC_NOT_FOUND,
                    QUIZ_NOT_FOUND + quizId);
            return;
        }
        showQuestionList(request, response, quizId);
    }

    private void showQuestionList(HttpServletRequest request, HttpServletResponse response, int quizId) {
        request.setAttribute(QUESTIONS, questionService.findQuestionsByQuizId(quizId));
        request.setAttribute(QUIZ, quizId);
        ServletResponseHandler.forward(request.getRequestDispatcher(LIST_VIEW), request, response);
    }

    private void runQuestions(HttpServletRequest request, HttpServletResponse response)
            throws BadRequestException {
        int quizId = RequestParameters.positiveInt(request, QUIZ);
        Quiz quiz = quizService.findQuizById(quizId)
                .orElseThrow(() -> new NoSuchElementException(QUIZ_NOT_FOUND + quizId));
        List<Question> questions = questionService.findQuestionsByQuizId(quizId);
        Map<Question, List<Answer>> answersPerQuestion = new LinkedHashMap<>();
        for (Question question : questions) {
            List<Answer> answers = questionService.getQuestionDetails(question.getId()).answers();
            if (!answers.isEmpty()) {
                answersPerQuestion.put(question, answers);
            }
        }
        User user = (User) request.getSession().getAttribute("user");
        Attempt attempt = attemptService.startAttempt(user, quiz);

        HttpSession session = request.getSession(true);
        session.setAttribute("quizTime", quiz.getTimeToPass() * 60);
        session.setAttribute("quizId", quizId);
        session.setAttribute("attemptId", attempt.getId());
        session.setAttribute("quizExpiresAt", attempt.getExpiresAt().getTime());
        request.setAttribute(QUESTIONS, questions);
        request.setAttribute("answersPerQuestion", answersPerQuestion);
        ServletResponseHandler.forward(request.getRequestDispatcher(QUESTIONS_VIEW), request, response);
    }

    private void showInvalidForm(HttpServletRequest request, HttpServletResponse response,
                                 QuestionForm form, String view) {
        request.setAttribute(QUIZ, form.quizId());
        request.setAttribute(QUESTION_ID, form.questionId());
        request.setAttribute(QUESTION, form.question());
        setAnswerAttributes(request, form.toAnswers(List.of()));
        request.setAttribute("checkboxAnswersMessage", validationMessage(request));
        ServletResponseHandler.forward(request.getRequestDispatcher(view), request, response);
    }

    private void setAnswerAttributes(HttpServletRequest request, List<Answer> answers) {
        for (int index = 0; index < ANSWER_SUFFIXES.size(); index++) {
            String suffix = ANSWER_SUFFIXES.get(index);
            Answer answer = answers.get(index);
            request.setAttribute("answer" + suffix, answer.getAnswer());
            request.setAttribute("correctAnswer" + suffix, answer.isCorrect());
        }
    }

    private String validationMessage(HttpServletRequest request) {
        Locale locale = (Locale) request.getSession().getAttribute("lang");
        return ResourceBundle.getBundle("messages", locale == null ? Locale.getDefault() : locale)
                .getString("validation.add.question.correct");
    }

    record QuestionForm(int quizId, int questionId, String question,
                        List<String> answers, List<Boolean> correct) {

        static QuestionForm from(HttpServletRequest request, boolean update) throws BadRequestException {
            int quizId = RequestParameters.positiveInt(request, QUIZ);
            int questionId = update ? RequestParameters.positiveInt(request, QUESTION_ID) : 0;
            String question = RequestParameters.boundedText(request, QUESTION, QUESTION_MAX_LENGTH);
            List<String> answers = new ArrayList<>(ANSWER_SUFFIXES.size());
            List<Boolean> correct = new ArrayList<>(ANSWER_SUFFIXES.size());
            for (String suffix : ANSWER_SUFFIXES) {
                answers.add(RequestParameters.boundedText(request, "answer" + suffix, ANSWER_MAX_LENGTH));
                correct.add(isSelected(request, "correctAnswer" + suffix, suffix));
            }
            return new QuestionForm(quizId, questionId, question, List.copyOf(answers), List.copyOf(correct));
        }

        private static boolean isSelected(HttpServletRequest request, String name, String expected)
                throws BadRequestException {
            String value = request.getParameter(name);
            if (value == null) {
                return false;
            }
            if (!expected.equalsIgnoreCase(value)) {
                throw new BadRequestException("Invalid parameter: " + name);
            }
            return true;
        }

        boolean hasCorrectAnswer() {
            return correct.stream().anyMatch(Boolean::booleanValue);
        }

        List<Answer> toAnswers(List<Integer> ids) {
            if (!ids.isEmpty() && ids.size() != ANSWER_SUFFIXES.size()) {
                throw new IllegalArgumentException("A question must have exactly four persisted answers");
            }
            List<Answer> result = new ArrayList<>(ANSWER_SUFFIXES.size());
            for (int index = 0; index < ANSWER_SUFFIXES.size(); index++) {
                Answer answer = new Answer();
                if (!ids.isEmpty()) {
                    answer.setId(ids.get(index));
                }
                answer.setAnswer(answers.get(index));
                answer.setCorrect(correct.get(index));
                result.add(answer);
            }
            return result;
        }
    }
}
