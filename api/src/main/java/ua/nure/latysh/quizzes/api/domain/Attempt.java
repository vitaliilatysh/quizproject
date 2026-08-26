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

import java.time.Instant;

@Entity
@Table(name = "attempts")
public class Attempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private int score;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "end_time")
    private Instant endTime;

    private boolean completed;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    public Attempt() {
    }

    public Attempt(Instant startTime, Instant expiresAt, Quiz quiz, UserAccount user) {
        this.score = 0;
        this.startTime = startTime;
        this.expiresAt = expiresAt;
        this.completed = false;
        this.quiz = quiz;
        this.user = user;
    }

    public Integer getId() {
        return id;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public UserAccount getUser() {
        return user;
    }
}
