package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

import java.util.List;

@Data
public class PollVoteRequest {
    private String userId;
    private List<String> optionIds;
}
