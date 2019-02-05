package ua.nure.latysh.quizzes.entities;

public class Quiz {
    private int id;
    private String name;
    private int levelId;
    private int subjectId;
    private int timeToPass;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public int getTimeToPass() {
        return timeToPass;
    }

    public void setTimeToPass(int timeToPass) {
        this.timeToPass = timeToPass;
    }

    public int getLevelId() {
        return levelId;
    }

    public void setLevelId(int levelId) {
        this.levelId = levelId;
    }
}
