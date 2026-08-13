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

    static String boundedText(HttpServletRequest request, String name, int maxLength)
            throws BadRequestException {
        String value = requiredText(request, name);
        if (value.length() > maxLength) {
            throw new BadRequestException("Parameter is too long: " + name);
        }
        return value;
    }

    static int positiveInt(HttpServletRequest request, String name) throws BadRequestException {
        String value = requiredText(request, name);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new BadRequestException("Parameter must be positive: " + name);
            }
            return parsed;
        } catch (NumberFormatException _) {
            throw new BadRequestException("Parameter must be an integer: " + name);
        }
    }
}

