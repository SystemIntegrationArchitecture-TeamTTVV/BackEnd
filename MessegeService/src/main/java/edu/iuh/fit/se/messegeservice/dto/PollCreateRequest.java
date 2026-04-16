package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

import java.util.List;

@Data
public class PollCreateRequest {
    private String userId;
    private String question;
    private List<String> options;
    private boolean multipleChoice;
}
