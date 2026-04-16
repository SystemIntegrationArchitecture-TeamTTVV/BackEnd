package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PollOption {
    private String optionId;
    private String text;
    private List<String> voterUserIds;
}
