package edu.iuh.fit.se.commonservice.exception;

import edu.iuh.fit.se.commonservice.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public final class ApiErrorMapper {
    private ApiErrorMapper() {
    }

    public static ErrorResponseDTO fromStatusCode(HttpStatusCode statusCode, String message, HttpServletRequest request) {
        return ErrorResponseDTO.builder()
                .status(statusCode.value())
                .error(statusCode.toString())
                .message(message)
                .path(request.getRequestURI())
                .build();
    }

    public static ErrorResponseDTO fromStatus(HttpStatus status, String message, HttpServletRequest request) {
        return ErrorResponseDTO.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
    }
}

