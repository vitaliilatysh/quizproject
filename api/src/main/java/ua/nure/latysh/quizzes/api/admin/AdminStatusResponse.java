package ua.nure.latysh.quizzes.api.admin;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"service", "access"})
public record AdminStatusResponse(String service, String access) {
}
