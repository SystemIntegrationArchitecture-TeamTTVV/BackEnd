// ...existing code...
package edu.iuh.fit.se.commonservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
public class UpdateProductRequest {
    private String title;
    private String description;
    private BigDecimal price;
    private String currency;
    private String condition;
    private String location;
    private String address;
    private List<String> images;
    private List<String> tags;
    private String category;
    private Boolean isActive;
    private Boolean isSold;
}
// ...existing code...
