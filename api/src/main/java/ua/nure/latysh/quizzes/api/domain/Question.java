package ua.nure.latysh.quizzes.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "questions")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "question")
    private String text;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    public Question() {
    }

    public Question(String question, Quiz quiz) {
        this.text = question;
        this.quiz = quiz;
    }

    public Integer getId() {
        return id;
    }

    public String getQuestion() {
        return text;
    }

    public void setQuestion(String question) {
        this.text = question;
    }

    public Quiz getQuiz() {
        return quiz;
    }
}
