package edu.iuh.fit.se.authservice.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusRequest {

    @Size(max = 80, message = "Status text must be 80 characters or fewer")
    private String statusText;

    @Size(max = 8, message = "Status emoji must be 8 characters or fewer")
    private String statusEmoji;
}
