package edu.iuh.fit.se.commonservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
public class CreateProductRequest {
    @NotBlank
    private String title;

    private String description;

    @NotNull
    @PositiveOrZero
    private BigDecimal price;

    private String currency = "USD";

    @NotBlank
    private String condition;

    @NotBlank
    private String location;

    private String address;

    private List<String> images;

    private List<String> tags;

    @NotBlank
    private String category;
}
