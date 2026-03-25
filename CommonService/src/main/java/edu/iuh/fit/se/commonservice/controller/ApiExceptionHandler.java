package edu.iuh.fit.se.commonservice.controller;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getReason());
        body.put("status", ex.getStatusCode().value());
        body.put("error", ex.getStatusCode().toString());
        body.put("path", request.getRequestURI());

        log.warn("API error {} {}: {}", ex.getStatusCode().value(), request.getRequestURI(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }
}

