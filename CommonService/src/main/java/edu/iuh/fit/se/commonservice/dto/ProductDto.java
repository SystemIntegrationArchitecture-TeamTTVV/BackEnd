package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private String id;
    private String title;
    private String description;
    private SimpleUserDto seller;
    private BigDecimal price;
    private String currency;
    private String condition;
    private String location;
    private String address;
    private List<String> images;
    private List<String> tags;
    private String category;
    private boolean isSold;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
