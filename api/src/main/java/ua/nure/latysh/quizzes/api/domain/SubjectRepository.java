package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Integer> {
    List<Subject> findAllByOrderByNameAsc();
}
