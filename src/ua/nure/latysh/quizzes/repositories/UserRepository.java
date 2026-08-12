package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.User;

import java.util.Optional;

public interface UserRepository extends CrudRepository<User> {
    Optional<User> findByLogin(String login);
    void updateLoginDate(User user);
    void updatePassword(User user);

}
