package ua.nure.latysh.quizzes.services;

import java.util.NoSuchElementException;
import java.util.Optional;

public enum RequiredEntity {
    ;

    public static <T> T get(Optional<T> entity, String description) {
        return entity.orElseThrow(() -> new NoSuchElementException(description + " not found"));
    }
}
