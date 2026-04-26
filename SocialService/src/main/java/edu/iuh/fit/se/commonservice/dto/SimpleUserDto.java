// ...existing code...
package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleUserDto {
    private String id;
    private String fullName;
    private String avatar;
}
// ...existing code...
