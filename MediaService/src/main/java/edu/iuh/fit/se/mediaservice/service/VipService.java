package edu.iuh.fit.se.mediaservice.service;

import edu.iuh.fit.se.mediaservice.config.socket.SocketEventTypes;
import edu.iuh.fit.se.mediaservice.dto.SocketEventDTO;
import edu.iuh.fit.se.mediaservice.model.PaymentTransaction;
import edu.iuh.fit.se.mediaservice.model.VipSubscription;
import edu.iuh.fit.se.mediaservice.repository.PaymentTransactionRepository;
import edu.iuh.fit.se.mediaservice.repository.VipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Manages VIP livestream subscriptions: tiers, purchases, and duration limits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VipService {

    private final VipSubscriptionRepository vipSubscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SocketEmitterService socketEmitterService;

    // ═══════════════════════════════════════════════════════════════════════════
    // ═══ Static VIP Configuration ══════════════════════════════════════════════
    // ═══════════════════════════════════════════════════════════════════════════

    public static final int VIP_FREE = 0;
    public static final int VIP_BASIC = 1;
    public static final int VIP_PRO = 2;
    public static final int VIP_ENTERPRISE = 3;

    /** Returns static package definitions for all VIP tiers */
    public List<Map<String, Object>> getVipPackages() {
        return List.of(
                buildPackage(VIP_FREE, "Mặc định", 0, 5,
                        List.of("Phát trực tiếp tối đa 5 phút", "Chất lượng cơ bản", "Chat trực tiếp")),
                buildPackage(VIP_BASIC, "Cơ Bản (Basic)", 1_500_000, 120,
                        List.of("Phát trực tiếp tối đa 2 giờ", "Badge VIP ⭐", "Hỗ trợ ưu tiên")),
                buildPackage(VIP_PRO, "Nâng Cao (Pro)", 3_500_000, 480,
                        List.of("Phát trực tiếp tối đa 8 giờ", "Badge VIP 💎", "Hỗ trợ ưu tiên 24/7", "Phòng chờ duyệt viewer")),
                buildPackage(VIP_ENTERPRISE, "Doanh Nghiệp (Enterprise)", 8_000_000, -1,
                        List.of("Phát trực tiếp không giới hạn", "Badge VIP 👑", "Hỗ trợ chuyên biệt", "API tích hợp", "Tùy chỉnh thương hiệu"))
        );
    }

    private Map<String, Object> buildPackage(int level, String name, long priceVnd,
                                              int maxMinutes, List<String> features) {
        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("level", level);
        pkg.put("name", name);
        pkg.put("priceVnd", priceVnd);
        pkg.put("maxLiveDurationMinutes", maxMinutes);
        pkg.put("features", features);
        return pkg;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ═══ VIP Info ══════════════════════════════════════════════════════════════
    // ═══════════════════════════════════════════════════════════════════════════

    /** Returns the effective VIP info for a user (auto-expires if past date) */
    public VipSubscription getVipInfo(String userId) {
        Optional<VipSubscription> opt = vipSubscriptionRepository.findByUserId(userId);
        if (opt.isEmpty()) {
            return defaultVip(userId);
        }

        VipSubscription sub = opt.get();

        // Auto-expire if past expiry date
        if ("ACTIVE".equals(sub.getStatus()) && sub.getExpiresAt() != null
                && sub.getExpiresAt().isBefore(LocalDateTime.now())) {
            sub.setStatus("EXPIRED");
            sub.setVipLevel(VIP_FREE);
            sub.setMaxLiveDurationMinutes(5);
            sub.setUpdatedAt(LocalDateTime.now());
            vipSubscriptionRepository.save(sub);
            log.info("⏰ VIP expired for userId={}, downgraded to FREE", userId);
        }

        return sub;
    }

    /** Returns the max live duration in minutes for a user */
    public int getMaxLiveDuration(String userId) {
        return getVipInfo(userId).getMaxLiveDurationMinutes();
    }

    /** Returns the effective VIP level for a user */
    public int getVipLevel(String userId) {
        return getVipInfo(userId).getVipLevel();
    }

    private VipSubscription defaultVip(String userId) {
        VipSubscription sub = new VipSubscription();
        sub.setUserId(userId);
        sub.setVipLevel(VIP_FREE);
        sub.setStatus("ACTIVE");
        sub.setMaxLiveDurationMinutes(5);
        sub.setPriceVnd(0);
        return sub;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ═══ VIP Purchase (VNPAY flow) ════════════════════════════════════════════
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Step 1: Create a payment order for a VIP package.
     * Reuses the existing PaymentTransaction model with a VIP-specific orderCode prefix.
     */
    public PaymentTransaction createVipPaymentOrder(String userId, int vipLevel, String ipAddress) {
        if (vipLevel < VIP_BASIC || vipLevel > VIP_ENTERPRISE) {
            throw new IllegalArgumentException("Invalid VIP level: " + vipLevel);
        }

        long priceVnd = getVipPrice(vipLevel);
        String packageName = getVipName(vipLevel);
        int maxMinutes = getMaxMinutesForLevel(vipLevel);

        // Generate unique order code with VIP prefix
        String orderCode = "VIP" + System.currentTimeMillis() + (int) (Math.random() * 1000);

        PaymentTransaction pt = new PaymentTransaction();
        pt.setOrderCode(orderCode);
        pt.setUserId(userId);
        pt.setCoinPackageId("VIP_" + vipLevel);
        pt.setCoinPackageName(packageName);
        pt.setCoinAmount(0); // no coins — this is a VIP purchase
        pt.setAmountVnd(priceVnd);
        pt.setPaymentProvider("VNPAY");
        pt.setStatus("PENDING");
        pt.setCoinsCredited(false);
        pt.setIpAddress(ipAddress);
        pt.setCreatedAt(LocalDateTime.now());
        pt.setUpdatedAt(LocalDateTime.now());

        PaymentTransaction saved = paymentTransactionRepository.save(pt);
        log.info("💎 VIP payment order created: orderCode={}, userId={}, level={}, amount={}₫",
                orderCode, userId, vipLevel, priceVnd);
        return saved;
    }

    /**
     * Step 2: Process VNPAY callback for VIP purchase.
     * On success, activates the VIP subscription for 30 days.
     */
    public PaymentTransaction processVipPaymentCallback(String orderCode, String vnpResponseCode,
                                                         String vnpTransactionNo, String vnpBankCode,
                                                         String vnpCardType, String vnpPayDate) {
        PaymentTransaction pt = paymentTransactionRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + orderCode));

        // Idempotency check
        if (!"PENDING".equals(pt.getStatus())) {
            log.warn("⚠️ VIP payment already processed: orderCode={}, status={}", orderCode, pt.getStatus());
            return pt;
        }

        pt.setVnpResponseCode(vnpResponseCode);
        pt.setVnpTransactionNo(vnpTransactionNo);
        pt.setVnpBankCode(vnpBankCode);
        pt.setVnpCardType(vnpCardType);
        pt.setVnpPayDate(vnpPayDate);
        pt.setUpdatedAt(LocalDateTime.now());
        pt.setCompletedAt(LocalDateTime.now());

        if ("00".equals(vnpResponseCode)) {
            pt.setStatus("SUCCESS");
            pt.setCoinsCredited(true); // reuse flag to mean "applied"

            // Determine VIP level from coinPackageId
            int vipLevel = extractVipLevel(pt.getCoinPackageId());
            activateVip(pt.getUserId(), vipLevel, pt.getAmountVnd());

            log.info("✅ VIP payment SUCCESS: orderCode={}, userId={}, level={}",
                    orderCode, pt.getUserId(), vipLevel);

            // Emit socket event
            emitVipUpgraded(pt.getUserId(), vipLevel);
        } else if ("24".equals(vnpResponseCode)) {
            pt.setStatus("CANCELLED");
            log.info("❌ VIP payment CANCELLED: orderCode={}", orderCode);
        } else {
            pt.setStatus("FAILED");
            log.info("❌ VIP payment FAILED: orderCode={}, responseCode={}", orderCode, vnpResponseCode);
        }

        return paymentTransactionRepository.save(pt);
    }

    /** Activates VIP subscription for a user */
    private void activateVip(String userId, int vipLevel, long priceVnd) {
        LocalDateTime now = LocalDateTime.now();
        int maxMinutes = getMaxMinutesForLevel(vipLevel);

        VipSubscription sub = vipSubscriptionRepository.findByUserId(userId)
                .orElseGet(() -> {
                    VipSubscription s = new VipSubscription();
                    s.setUserId(userId);
                    s.setCreatedAt(now);
                    return s;
                });

        sub.setVipLevel(vipLevel);
        sub.setPriceVnd(priceVnd);
        sub.setStatus("ACTIVE");
        sub.setActivatedAt(now);
        sub.setExpiresAt(now.plusDays(30));
        sub.setMaxLiveDurationMinutes(maxMinutes);
        sub.setUpdatedAt(now);

        vipSubscriptionRepository.save(sub);
        log.info("👑 VIP activated: userId={}, level={}, expiresAt={}", userId, vipLevel, sub.getExpiresAt());
    }

    private void emitVipUpgraded(String userId, int vipLevel) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("vipLevel", vipLevel);
            payload.put("vipName", getVipName(vipLevel));
            SocketEventDTO event = SocketEventDTO.of(SocketEventTypes.VIP_UPGRADED, userId, payload);
            socketEmitterService.emitToUserById(userId, event);
        } catch (Exception e) {
            log.error("Failed to emit VIP_UPGRADED: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ═══ Helpers ═══════════════════════════════════════════════════════════════
    // ═══════════════════════════════════════════════════════════════════════════

    private int extractVipLevel(String coinPackageId) {
        if (coinPackageId == null) return VIP_FREE;
        try {
            return Integer.parseInt(coinPackageId.replace("VIP_", ""));
        } catch (NumberFormatException e) {
            return VIP_FREE;
        }
    }

    private long getVipPrice(int level) {
        return switch (level) {
            case VIP_BASIC -> 1_500_000;
            case VIP_PRO -> 3_500_000;
            case VIP_ENTERPRISE -> 8_000_000;
            default -> 0;
        };
    }

    private String getVipName(int level) {
        return switch (level) {
            case VIP_BASIC -> "Gói Livestream Cơ Bản (Basic)";
            case VIP_PRO -> "Gói Livestream Nâng Cao (Pro)";
            case VIP_ENTERPRISE -> "Gói Livestream Doanh Nghiệp (Enterprise)";
            default -> "Mặc định (Free)";
        };
    }

    public static int getMaxMinutesForLevel(int level) {
        return switch (level) {
            case VIP_BASIC -> 120;
            case VIP_PRO -> 480;
            case VIP_ENTERPRISE -> -1; // unlimited
            default -> 5;
        };
    }
}
