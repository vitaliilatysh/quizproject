package ua.nure.latysh.quizzes.servlets;

import javax.servlet.http.HttpServletRequest;

final class RequestParameters {

    private RequestParameters() {
    }

    static String requiredText(HttpServletRequest request, String name) throws BadRequestException {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Missing or blank parameter: " + name);
        }
        return value.trim();
    }

    static int positiveInt(HttpServletRequest request, String name) throws BadRequestException {
        String value = requiredText(request, name);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new BadRequestException("Parameter must be positive: " + name);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Parameter must be an integer: " + name);
        }
    }
}
