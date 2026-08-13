package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.entities.Level;
import ua.nure.latysh.quizzes.repositories.LevelRepository;
import ua.nure.latysh.quizzes.repositories.impl.LevelRepositoryImpl;

import java.util.List;
import java.util.Optional;

public class LevelService {
    private final LevelRepository levelRepository;

    public LevelService() {
        this(new LevelRepositoryImpl());
    }

    public LevelService(LevelRepository levelRepository) {
        this.levelRepository = levelRepository;
    }

    public Optional<Level> findAnswerById(String levelName) {
        return levelRepository.findByName(levelName);
    }

    public List<Level> findAllLevels(){
        return levelRepository.findAll();
    }
}
