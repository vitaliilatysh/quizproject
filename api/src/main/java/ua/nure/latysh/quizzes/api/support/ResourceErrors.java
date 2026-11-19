package ua.nure.latysh.quizzes.api.support;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * How the administration services say "not there" and "already taken".
 *
 * <p>Shared so the wording is one decision rather than four, and so the
 * database's account of a collision is translated in one place.
 */
public final class ResourceErrors {
    private ResourceErrors() {
    }

    public static ResourceNotFoundException missing(String resource, int id) {
        return new ResourceNotFoundException(resource + " " + id + " was not found");
    }

    private static ResourceConflictException duplicate(String resource, String name) {
        return new ResourceConflictException(resource + " named '" + name + "' already exists");
    }

    /**
     * Saves, reporting a unique-constraint collision as a conflict.
     *
     * <p>The check belongs to the database, not to a read before the write: two
     * requests naming the same subject can both find nothing and both proceed,
     * and only the index decides which one loses. This turns that decision into
     * the 409 the caller should see — and it flushes, because a violation that
     * surfaces at commit is too late for any handler to translate.
     */
    public static void saveUnique(String resource, String name, Runnable save) {
        try {
            save.run();
        } catch (DataIntegrityViolationException _) {
            throw duplicate(resource, name);
        }
    }
}
