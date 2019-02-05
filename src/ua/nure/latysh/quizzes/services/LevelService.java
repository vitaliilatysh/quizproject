package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.entities.Level;
import ua.nure.latysh.quizzes.repositories.LevelRepository;
import ua.nure.latysh.quizzes.repositories.impl.LevelRepositoryImpl;

import java.util.List;

public class LevelService {
    private LevelRepository levelRepository;

    public LevelService() {
        this.levelRepository = new LevelRepositoryImpl();
    }

    public Level findAnswerById(String levelName) {
        return levelRepository.findByName(levelName);
    }

    public List<Level> findAllLevels(){
        return levelRepository.findAll();
    }
}
