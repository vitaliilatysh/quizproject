package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.dto.UserDto;
import ua.nure.latysh.quizzes.entities.Role;
import ua.nure.latysh.quizzes.entities.Status;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.repositories.RoleRepository;
import ua.nure.latysh.quizzes.repositories.StatusRepository;
import ua.nure.latysh.quizzes.repositories.UserRepository;
import ua.nure.latysh.quizzes.repositories.impl.RoleRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.impl.StatusRepositoryImpl;
import ua.nure.latysh.quizzes.repositories.impl.UserRepositoryImpl;
import ua.nure.latysh.quizzes.security.PasswordHasher;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class UserService {
    private final RoleRepository roleRepository;
    private final StatusRepository statusRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public UserService() {
        this(new UserRepositoryImpl(), new RoleRepositoryImpl(), new StatusRepositoryImpl(), new PasswordHasher());
    }

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       StatusRepository statusRepository) {
        this(userRepository, roleRepository, statusRepository, new PasswordHasher());
    }

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       StatusRepository statusRepository,
                       PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.statusRepository = statusRepository;
        this.passwordHasher = passwordHasher;
    }

    public User findByLoginAndPassword(String login, String password) {
        Optional<User> foundUser = userRepository.findByLogin(login);
        if (foundUser.isEmpty()) {
            return null;
        }
        User user = foundUser.get();

        String storedPassword = user.getPassword();
        boolean matches = passwordHasher.isEncoded(storedPassword)
                ? passwordHasher.matches(password, storedPassword)
                : passwordHasher.matchesLegacy(password, storedPassword);
        if (!matches) {
            return null;
        }

        if (!passwordHasher.isEncoded(storedPassword)) {
            user.setPassword(passwordHasher.hash(password));
            userRepository.updatePassword(user);
        }
        user.setPassword(null);
        return user;
    }

    public Optional<User> findUserByLogin(String login) {
        return userRepository.findByLogin(login);
    }

    public List<UserDto> findAllUsers() {
        List<User> users = userRepository.findAll();
        return convertUsersToUsersDto(users);
    }

    public Optional<User> findUserById(int userId){
        return userRepository.findById(userId);
    }
    public UserDto convertUserToUserDto(User user) {
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setFirstName(user.getFirstName());
        userDto.setLastName(user.getLastName());
        Date date = user.getRegisterDateTime();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
        Date loginDate = user.getLoginDateTime();
        userDto.setRegisterDateTime(simpleDateFormat.format(date));
        userDto.setLoginDateTime(simpleDateFormat.format(loginDate));
        Role role = RequiredEntity.get(roleRepository.findById(user.getRoleId()), "Role " + user.getRoleId());
        userDto.setRole(role.getRole());
        Status status = RequiredEntity.get(statusRepository.findById(user.getStatusId()),
                "Status " + user.getStatusId());
        userDto.setStatus(status.getStatus());
        return userDto;
    }

    private List<UserDto> convertUsersToUsersDto(List<User> users) {
        List<UserDto> userDtos = new ArrayList<>();
        for (User user : users) {
            UserDto userDto = convertUserToUserDto(user);
            userDtos.add(userDto);
        }

        return userDtos;
    }

    public void blockUser(String userId) {
        User user = RequiredEntity.get(userRepository.findById(Integer.parseInt(userId)), "User " + userId);
        user.setStatusId(2);
        userRepository.update(user);
    }

    public void unblockUser(String userId) {
        User user = RequiredEntity.get(userRepository.findById(Integer.parseInt(userId)), "User " + userId);
        user.setStatusId(1);
        userRepository.update(user);
    }

    public void save(User user) {
        if (!passwordHasher.isEncoded(user.getPassword())) {
            user.setPassword(passwordHasher.hash(user.getPassword()));
        }
        userRepository.save(user);
    }

    public void updateUserLoginDate(User user) {
        userRepository.updateLoginDate(user);
    }
}
