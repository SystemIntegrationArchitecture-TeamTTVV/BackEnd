package edu.iuh.fit.se.commonservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Document(collection = "stories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stories {

    @Id
    private String id;

    // ===== User đăng story =====
    @Indexed
    private String userId;        // ref logic (giống User, Friend)

    private String userName;      // snapshot để FE load nhanh
    private String userAvatar;

    // ===== Nội dung story =====
    private String contentType;   // image | text | video (KHÔNG enum)

    private String content;       // image/video URL hoặc text

    private String background;    // chỉ dùng cho text story

    private Integer duration;
    // ===== Thời gian =====
    @Indexed
    private LocalDateTime createdAt;


    @Indexed(expireAfterSeconds = 0)
    private LocalDateTime expiredAt;// Mongo tự xoá sau 24h

    // ===== Trạng thái =====
    private Boolean active;
}
