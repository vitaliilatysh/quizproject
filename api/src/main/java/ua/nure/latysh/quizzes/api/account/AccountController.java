package ua.nure.latysh.quizzes.api.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Account")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "Read the current user's profile")
    public ProfileResponse profile(Authentication authentication) {
        return accountService.profile(authentication.getName());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change the current user's password")
    public void changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(authentication.getName(), request);
    }
}
