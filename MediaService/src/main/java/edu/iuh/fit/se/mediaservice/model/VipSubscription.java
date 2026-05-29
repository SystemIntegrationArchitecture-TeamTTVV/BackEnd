package edu.iuh.fit.se.mediaservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Stores VIP subscription state for a user's livestream tier.
 * <p>
 * VIP 0 = free (5-minute limit per session)
 * VIP 1 = Basic   (1.500.000 VND / month, 2-hour limit)
 * VIP 2 = Pro     (3.500.000 VND / month, 8-hour limit)
 * VIP 3 = Enterprise (8.000.000 VND / month, unlimited)
 */
@Document(collection = "vip_subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VipSubscription {
    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    /** 0 = Free, 1 = Basic, 2 = Pro, 3 = Enterprise */
    private int vipLevel = 0;

    /** Price paid for the current subscription (VND) */
    private long priceVnd = 0;

    /** ACTIVE | EXPIRED | CANCELLED */
    @Indexed
    private String status = "ACTIVE";

    private LocalDateTime activatedAt;

    /** activatedAt + 30 days — null for VIP 0 */
    private LocalDateTime expiresAt;

    /** Max live duration in minutes: 5, 120, 480, or -1 (unlimited) */
    private int maxLiveDurationMinutes = 5;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
