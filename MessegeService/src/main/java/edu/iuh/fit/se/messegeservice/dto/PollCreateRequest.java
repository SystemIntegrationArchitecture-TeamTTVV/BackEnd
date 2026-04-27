package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PollCreateRequest {
    private String userId;
    private String question;
    private List<String> options;
    private boolean multipleChoice;
    private boolean canAddOptions;
    private boolean hideResultsBeforeVote;
    private boolean hideVoters;
    private LocalDateTime deadline;
}
