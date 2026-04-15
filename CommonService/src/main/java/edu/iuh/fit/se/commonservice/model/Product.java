package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    private String id;

    private String title;
    private String description;

    @DBRef
    private User seller;
    private String sellerId;

    private BigDecimal price;
    private String currency = "USD";
    private String condition; // Mới, Như mới, Đã dùng tốt

    private String location; // Thành phố
    private String address;  // Địa chỉ chi tiết

    private List<String> images; // URLs ảnh thật
    private List<String> tags;
    private String category; // Electronics, Vehicles, Property...

    // Use wrapper Boolean to keep consistency with other models (User)
    private Boolean isSold = Boolean.FALSE;
    private Boolean isActive = Boolean.TRUE;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Explicit getters/setters for boolean fields to avoid Lombok naming ambiguity
    public Boolean getIsSold() { return this.isSold; }
    public void setIsSold(Boolean isSold) { this.isSold = isSold; }

    public Boolean getIsActive() { return this.isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
