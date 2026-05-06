package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendSuggestionDTO {
    private String userId;
    private String fullName;
    private String username;
    private String avatar;
    /** Number of mutual friends */
    private int mutualFriendCount;
}
