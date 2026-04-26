package edu.iuh.fit.se.commonservice.dto.story;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
public class UserDTO {
    private String id;
    private String name;
    private String avatar;
}

