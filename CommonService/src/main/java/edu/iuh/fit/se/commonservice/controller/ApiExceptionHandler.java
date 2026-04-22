package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.ErrorResponseDTO;
import edu.iuh.fit.se.commonservice.exception.ApiErrorMapper;
import edu.iuh.fit.se.commonservice.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDTO> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        log.warn("API warning {} {}: {}", ex.getStatusCode().value(), request.getRequestURI(), ex.getReason());
        ErrorResponseDTO error = ApiErrorMapper.fromStatusCode(ex.getStatusCode(), ex.getReason(), request);
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        log.warn("Validation error at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponseDTO error = ApiErrorMapper.fromStatus(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        log.warn("Resource not found at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponseDTO error = ApiErrorMapper.fromStatus(HttpStatus.NOT_FOUND, ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGlobalException(
            Exception ex,
            HttpServletRequest request
    ) {
        // Client disconnected before response completed — harmless, log WARN only
        if (isClientDisconnect(ex)) {
            log.warn("Client disconnected before response completed at {}: {}",
                    request.getRequestURI(), ex.getMessage());
            return null;
        }

        log.error("Unhandled system error at {}: ", request.getRequestURI(), ex);
        ErrorResponseDTO error = ApiErrorMapper.fromStatus(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã có lỗi nghiêm trọng xảy ra trên hệ thống",
                request
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Detect client-abort / broken-pipe scenarios.
     * These occur when the browser cancels the request (navigation, component unmount, etc.).
     */
    private boolean isClientDisconnect(Throwable ex) {
        if (ex instanceof IOException) {
            return true;
        }
        // Tomcat wraps it as ClientAbortException (subclass of IOException)
        String className = ex.getClass().getName();
        if (className.contains("ClientAbortException")) {
            return true;
        }
        // Check root cause
        Throwable cause = ex.getCause();
        if (cause instanceof IOException) {
            String msg = cause.getMessage();
            return msg != null && (msg.contains("connection was aborted")
                    || msg.contains("Broken pipe")
                    || msg.contains("Connection reset"));
        }
        return false;
    }
}

