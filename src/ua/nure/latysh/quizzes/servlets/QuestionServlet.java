package ua.nure.latysh.quizzes.servlets;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.entities.*;
import ua.nure.latysh.quizzes.services.*;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns = {"/questions", "/questions/edit", "/questions/add", "/questions/view"})
public class QuestionServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(QuestionServlet.class);
    private QuizService quizService = new QuizService();
    private QuestionService questionService = new QuestionService();
    private AnswerService answerService = new AnswerService();
    private AttemptService attemptService = new AttemptService();
    private UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {

        HttpSession session = request.getSession(true);

        session.getAttribute("quizTime");
        session.getAttribute("quizId");
        session.getAttribute("questions");
        session.getAttribute("answersPerQuestion");
        request.getRequestDispatcher("questions.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        String action = request.getParameter("action");
        User user = (User) request.getSession().getAttribute("user");
        switch (action) {
            case "add":
                String quizId = request.getParameter("quiz");
                request.setAttribute("quiz", quizId);
                request.setAttribute("action", "create");
                request.getRequestDispatcher("addQuestion.jsp").forward(request, response);

                logger.info(user.getLogin() + " opened add question page");
                break;
            case "addQuestion":
                addQuestions(request, response);
                logger.info(user.getLogin() + " added new question");
                break;
            case "run":
                runQuestions(request, response);
                logger.info(user.getLogin() + " started quiz");
                break;
            case "view":
                viewQuesions(request, response);
                logger.info(user.getLogin() + " view the question list");
                break;
            case "edit":
                editQuestion(request, response);
                logger.info(user.getLogin() + " opened edit question page");
                break;
            case "editQuestion":
                saveQuestion(request, response);
                break;
            case "delete":
                deleteQuestion(request, response);
                break;
        }
    }

    private void saveQuestion(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        int quizId = Integer.parseInt(request.getParameter("quiz"));
        int questionId = Integer.parseInt(request.getParameter("questionId"));
        String question = request.getParameter("question");
        String answerA = request.getParameter("answerA");
        String answerB = request.getParameter("answerB");
        String answerC = request.getParameter("answerC");
        String answerD = request.getParameter("answerD");

        String correctAnswerA = request.getParameter("correctAnswerA");
        String correctAnswerB = request.getParameter("correctAnswerB");
        String correctAnswerC = request.getParameter("correctAnswerC");
        String correctAnswerD = request.getParameter("correctAnswerD");

        Locale lang = (Locale) request.getSession().getAttribute("lang");
        ResourceBundle mybundle = ResourceBundle.getBundle("messages", lang);

        Question foundQuestion = questionService.findQuestionById(questionId);
        foundQuestion.setQuestion(question);

        List<Answer> answers = answerService.findAnswersByQuestionId(questionId);

        answers.get(0).setAnswer(answerA);
        answers.get(1).setAnswer(answerB);
        answers.get(2).setAnswer(answerC);
        answers.get(3).setAnswer(answerD);

        if (correctAnswerA == null & correctAnswerB == null & correctAnswerC == null & correctAnswerD == null) {
            request.setAttribute("quiz", quizId);
            request.setAttribute("question", question);
            request.setAttribute("questionId", questionId);
            request.setAttribute("answerA", answers.get(0).getAnswer());
            request.setAttribute("answerB", answers.get(1).getAnswer());
            request.setAttribute("answerC", answers.get(2).getAnswer());
            request.setAttribute("answerD", answers.get(3).getAnswer());
            request.setAttribute("checkboxAnswersMessage", mybundle.getString("validation.add.question.correct"));
            request.getRequestDispatcher("editQuestion.jsp").forward(request, response);
        } else {

            if (correctAnswerA != null && correctAnswerA.equalsIgnoreCase("A")) {
                answers.get(0).setCorrect(true);
            } else {
                answers.get(0).setCorrect(false);
            }

            if (correctAnswerB != null && correctAnswerB.equalsIgnoreCase("B")) {
                answers.get(1).setCorrect(true);
            } else {
                answers.get(1).setCorrect(false);
            }

            if (correctAnswerC != null && correctAnswerC.equalsIgnoreCase("C")) {
                answers.get(2).setCorrect(true);
            } else {
                answers.get(2).setCorrect(false);
            }

            if (correctAnswerD != null && correctAnswerD.equalsIgnoreCase("D")) {
                answers.get(3).setCorrect(true);
            } else {
                answers.get(3).setCorrect(false);
            }

            questionService.updateQuestion(foundQuestion);
            Question savedQuestion = questionService.findQuestionById(foundQuestion.getId());

            for (Answer answer : answers) {
                answerService.updateAnswer(answer);
            }

            List<Question> questions = questionService.findQuestionsByQuizId(quizId);

            request.setAttribute("questions", questions);
            request.setAttribute("quiz", quizId);
            request.getRequestDispatcher("listQuestions.jsp").forward(request, response);
        }
    }

    private void deleteQuestion(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        int questionId = Integer.parseInt(request.getParameter("question"));
        Question question = questionService.findQuestionById(questionId);
        int quizId = question.getQuizId();
        questionService.deleteQuestion(question);

        List<Question> questions = questionService.findQuestionsByQuizId(quizId);
        request.setAttribute("questions", questions);
        request.getRequestDispatcher("listQuestions.jsp").forward(request, response);
    }

    private void editQuestion(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int questionId = Integer.parseInt(request.getParameter("question"));
        Question question = questionService.findQuestionById(questionId);
        List<Answer> answers = answerService.findAnswersByQuestionId(questionId);

        request.setAttribute("answerA", answers.get(0).getAnswer());
        request.setAttribute("answerB", answers.get(1).getAnswer());
        request.setAttribute("answerC", answers.get(2).getAnswer());
        request.setAttribute("answerD", answers.get(3).getAnswer());

        request.setAttribute("correctAnswerA", answers.get(0).isCorrect());
        request.setAttribute("correctAnswerB", answers.get(1).isCorrect());
        request.setAttribute("correctAnswerC", answers.get(2).isCorrect());
        request.setAttribute("correctAnswerD", answers.get(3).isCorrect());

        request.setAttribute("quiz", question.getQuizId());
        request.setAttribute("questionId", questionId);
        request.setAttribute("question", question.getQuestion());
        request.getRequestDispatcher("editQuestion.jsp").forward(request, response);
    }

    private void viewQuesions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String quizId = request.getParameter("quiz");
        List<Question> questions = questionService.findQuestionsByQuizId(Integer.parseInt(quizId));

        request.setAttribute("questions", questions);
        request.setAttribute("quiz", quizId);
        request.getRequestDispatcher("listQuestions.jsp").forward(request, response);
    }

    private void runQuestions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int quizId = Integer.parseInt(request.getParameter("quiz"));
        Quiz quiz = quizService.findQuizById(quizId);
        int minutesForQuiz = quiz.getTimeToPass() * 60;

        List<Answer> answers;
//        String userLogin = null;
//        Cookie[] cookie = request.getCookies();
////        for (Cookie cookie1 : cookie) {
////            if (cookie1.getName().equalsIgnoreCase("user")) {
////                userLogin = cookie1.getValue();
////            }
////        }
        User sessionUser = (User) request.getSession().getAttribute("user");
        User user = userService.findUserByLogin(sessionUser.getLogin());

        Attempt attempt = new Attempt();
        attempt.setUserId(user.getId());
        attempt.setScore(0);
        attempt.setQuizId(quizId);
        attemptService.saveAttempt(attempt);

        Map<Question, List<Answer>> answersPerQuestion = new LinkedHashMap<>();

        List<Question> questions = questionService.findQuestionsByQuizId(quizId);
        for (Question currentQuestion : questions) {
            answers = answerService.findAnswersByQuestionId(currentQuestion.getId());
            if (answers.size() != 0) {
                if (!answersPerQuestion.containsKey(currentQuestion)) {
                    answersPerQuestion.put(currentQuestion, new ArrayList<>());
                    answersPerQuestion.get(currentQuestion).addAll(answers);
                }
            }
        }

        HttpSession currentSession = request.getSession(true);
        currentSession.setAttribute("quizTime", minutesForQuiz);
        currentSession.setAttribute("quizId", quizId);
        currentSession.setAttribute("questions", questions);
        currentSession.setAttribute("answersPerQuestion", answersPerQuestion);

        response.sendRedirect("questions");
    }

    private void addQuestions(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        int quizId = Integer.parseInt(request.getParameter("quiz"));
        String question = request.getParameter("question").trim();
        List<String> answers = new ArrayList<>();
        List<String> correctAnswers = new ArrayList<>();

        String answerA = request.getParameter("answerA");
        String answerB = request.getParameter("answerB");
        String answerC = request.getParameter("answerC");
        String answerD = request.getParameter("answerD");

        answers.add(answerA);
        answers.add(answerB);
        answers.add(answerC);
        answers.add(answerD);

        String correctAnswerA = request.getParameter("correctAnswerA");
        String correctAnswerB = request.getParameter("correctAnswerB");
        String correctAnswerC = request.getParameter("correctAnswerC");
        String correctAnswerD = request.getParameter("correctAnswerD");

        if (correctAnswerA != null) {
            correctAnswers.add(correctAnswerA);
        }
        if (correctAnswerB != null) {
            correctAnswers.add(correctAnswerB);
        }
        if (correctAnswerC != null) {
            correctAnswers.add(correctAnswerC);
        }
        if (correctAnswerD != null) {
            correctAnswers.add(correctAnswerD);
        }

        Locale lang = (Locale) request.getSession().getAttribute("lang");
        ResourceBundle mybundle = ResourceBundle.getBundle("messages", lang);

        List<Answer> answerList = new ArrayList<>();

        for (String answer : answers) {
            Answer newAnswer = new Answer();
            newAnswer.setAnswer(answer.trim());
            answerList.add(newAnswer);
        }

        if (correctAnswerA == null && correctAnswerB == null && correctAnswerC == null && correctAnswerD == null) {
            request.setAttribute("quiz", quizId);
            request.setAttribute("question", question);
            request.setAttribute("answerA", answers.get(0));
            request.setAttribute("answerB", answers.get(1));
            request.setAttribute("answerC", answers.get(2));
            request.setAttribute("answerD", answers.get(3));
            request.setAttribute("checkboxAnswersMessage", mybundle.getString("validation.add.question.correct"));
            request.getRequestDispatcher("addQuestion.jsp").forward(request, response);
        } else {
            if (correctAnswerA != null && correctAnswerA.equalsIgnoreCase("A")) {
                answerList.get(0).setCorrect(true);
            } else {
                answerList.get(0).setCorrect(false);
            }
            if (correctAnswerB != null && correctAnswerB.equalsIgnoreCase("B")) {
                answerList.get(1).setCorrect(true);
            } else {
                answerList.get(1).setCorrect(false);
            }
            if (correctAnswerC != null && correctAnswerC.equalsIgnoreCase("C")) {
                answerList.get(2).setCorrect(true);
            } else {
                answerList.get(2).setCorrect(false);
            }
            if (correctAnswerD != null && correctAnswerD.equalsIgnoreCase("D")) {
                answerList.get(3).setCorrect(true);
            } else {
                answerList.get(3).setCorrect(false);
            }


            Question savedQuestion = questionService.addQuestion(question, quizId);

            for (Answer answer : answerList) {
                answer.setQuestionId(savedQuestion.getId());
                answerService.saveAnswer(answer);
            }

            List<Question> questions = questionService.findQuestionsByQuizId(quizId);
            request.setAttribute("questions", questions);
            request.setAttribute("quiz", quizId);
            request.getRequestDispatcher("listQuestions.jsp").forward(request, response);

            request.setAttribute("quiz", quizId);
            request.setAttribute("question", question);
            request.setAttribute("checkboxAnswersMessage", mybundle.getString("validation.add.question.correct"));
            request.setAttribute("action", "create");
            request.getRequestDispatcher("addQuestion.jsp").forward(request, response);
        }
    }


}
