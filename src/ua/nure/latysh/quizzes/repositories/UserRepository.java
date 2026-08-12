package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.User;

public interface UserRepository extends CrudRepository<User> {
    User findByLogin(String login);
    void updateLoginDate(User user);
    void updatePassword(User user);

}
