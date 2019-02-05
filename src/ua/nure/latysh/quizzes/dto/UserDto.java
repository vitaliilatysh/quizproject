package ua.nure.latysh.quizzes.dto;

import java.util.Date;

public class UserDto {
    private int id;
    private String firstName;
    private String lastName;
    private String registerDateTime;
    private String loginDateTime;
    private String status;
    private String role;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getRegisterDateTime() {
        return registerDateTime;
    }

    public void setRegisterDateTime(String registerDateTime) {
        this.registerDateTime = registerDateTime;
    }

    public String getLoginDateTime() {
        return loginDateTime;
    }

    public void setLoginDateTime(String loginDateTime) {
        this.loginDateTime = loginDateTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
