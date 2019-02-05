package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.repositories.impl.SubjectRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.SubjectRepository;

import java.util.List;

public class SubjectService {

    private SubjectRepository subjectRepository;

    public SubjectService() {
        this.subjectRepository = new SubjectRepositoryImpl();
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

    public Subject findSubjectById(int subjectId){
        return subjectRepository.findById(subjectId);
    }

    public void updateSubject(Subject subject){
        subjectRepository.update(subject);
    }
}
