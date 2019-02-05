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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class UserService {
    private RoleRepository roleRepository = new RoleRepositoryImpl();
    private StatusRepository statusRepository = new StatusRepositoryImpl();
    private UserRepository userRepository;

    public UserService() {
        this.userRepository = new UserRepositoryImpl();
    }

    public User findByLoginAndPassword(String login, String password) {
        return userRepository.findByLoginAndPassword(login, password);
    }

    public User findUserByLogin(String login) {
        return userRepository.findByLogin(login);
    }

    public List<UserDto> findAllUsers() {
        List<User> users = userRepository.findAll();
        return convertUsersToUsersDto(users);
    }

    public User findUserById(int userId){
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
        Role role = roleRepository.findById(user.getRoleId());
        userDto.setRole(role.getRole());
        Status status = statusRepository.findById(user.getStatusId());
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
        User user = userRepository.findById(Integer.parseInt(userId));
        user.setStatusId(2);
        userRepository.update(user);
    }

    public void unblockUser(String userId) {
        User user = userRepository.findById(Integer.parseInt(userId));
        user.setStatusId(1);
        userRepository.update(user);
    }

    public void save(User user) {
        userRepository.save(user);
    }

    public void updateUserLoginDate(User user) {
        userRepository.updateLoginDate(user);
    }
}
