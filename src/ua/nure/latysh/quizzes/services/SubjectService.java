package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.repositories.impl.SubjectRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.SubjectRepository;

import java.util.List;
import java.util.Optional;

public class SubjectService {

    private final SubjectRepository subjectRepository;

    public SubjectService() {
        this(new SubjectRepositoryImpl());
    }

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    public List<Subject> getAllSubjects(){
        return subjectRepository.findAll();
    }

    public boolean addSubject(Subject subject){
        return subjectRepository.save(subject);
    }

    public void deleteSubject(Subject subject){
        subjectRepository.delete(subject);
    }

    public Optional<Subject> findSubjectById(int subjectId){
        return subjectRepository.findById(subjectId);
    }

    public void updateSubject(Subject subject){
        subjectRepository.update(subject);
    }
}
