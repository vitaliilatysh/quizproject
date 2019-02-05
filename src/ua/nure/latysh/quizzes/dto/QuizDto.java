package ua.nure.latysh.quizzes.dto;

public class QuizDto {
    private int id;
    private String name;
    private String complexity;
    private String subjectName;
    private int timeToPass;
    private int totalQuestionsNumber;

    public int getTotalQuestionsNumber() {
        return totalQuestionsNumber;
    }

    public void setTotalQuestionsNumber(int totalQuestionsNumber) {
        this.totalQuestionsNumber = totalQuestionsNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComplexity() {
        return complexity;
    }

    public void setComplexity(String complexity) {
        this.complexity = complexity;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public int getTimeToPass() {
        return timeToPass;
    }

    public void setTimeToPass(int timeToPass) {
        this.timeToPass = timeToPass;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
