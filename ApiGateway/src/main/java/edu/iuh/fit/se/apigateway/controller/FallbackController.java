package edu.iuh.fit.se.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FallbackController {

    @RequestMapping(path = "/fallback/common", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> commonFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "service", "CommonService",
                "message", "CommonService is temporarily unavailable. Please try again later."
        ));
    }

    @RequestMapping(path = "/fallback/message", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> messageFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "service", "MessegeService",
                "message", "MessegeService is temporarily unavailable. Please try again later."
        ));
    }

    @RequestMapping(path = "/fallback/auth", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> authFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "service", "AuthService",
                "message", "AuthService is temporarily unavailable. Please try again later."
        ));
    }
}

