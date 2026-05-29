package edu.iuh.fit.se.mediaservice.controller;

import edu.iuh.fit.se.mediaservice.model.*;
import edu.iuh.fit.se.mediaservice.service.BillingService;
import edu.iuh.fit.se.mediaservice.service.VipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final VipService vipService;

    // ══════════════════════════════════════════════════════════════════════════
    // ── Wallet ────────────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /** Get or create wallet for a user */
    @GetMapping("/wallet")
    public ResponseEntity<Map<String, Object>> getWallet(@RequestParam String userId) {
        Wallet wallet = billingService.getOrCreateWallet(userId);
        return ResponseEntity.ok(Map.of(
                "userId", wallet.getUserId(),
                "balance", wallet.getBalance()
        ));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Coin Packages ─────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /** List active coin packages (for users) */
    @GetMapping("/coin-packages")
    public ResponseEntity<List<CoinPackage>> getCoinPackages() {
        return ResponseEntity.ok(billingService.getActiveCoinPackages());
    }

    /** List ALL coin packages (admin) */
    @GetMapping("/coin-packages/all")
    public ResponseEntity<List<CoinPackage>> getAllCoinPackages() {
        return ResponseEntity.ok(billingService.getAllCoinPackages());
    }

    /** Create a coin package (admin) */
    @PostMapping("/coin-packages")
    public ResponseEntity<CoinPackage> createCoinPackage(@RequestBody CoinPackage pkg) {
        return ResponseEntity.ok(billingService.createCoinPackage(pkg));
    }

    /** Update a coin package (admin) */
    @PutMapping("/coin-packages/{id}")
    public ResponseEntity<CoinPackage> updateCoinPackage(@PathVariable String id,
                                                          @RequestBody CoinPackage updates) {
        return ResponseEntity.ok(billingService.updateCoinPackage(id, updates));
    }

    /** Toggle coin package visibility (admin) */
    @PatchMapping("/coin-packages/{id}/toggle")
    public ResponseEntity<Void> toggleCoinPackage(@PathVariable String id,
                                                   @RequestParam boolean active) {
        billingService.toggleCoinPackage(id, active);
        return ResponseEntity.ok().build();
    }

    /** Delete a coin package (admin) */
    @DeleteMapping("/coin-packages/{id}")
    public ResponseEntity<Void> deleteCoinPackage(@PathVariable String id) {
        billingService.deleteCoinPackage(id);
        return ResponseEntity.ok().build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── VNPAY Payment ─────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Create a payment order for a coin package.
     * Returns the PaymentTransaction with orderCode that frontend uses to call VNPAY.
     */
    @PostMapping("/payment/create")
    public ResponseEntity<PaymentTransaction> createPayment(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String userId = body.get("userId");
        String coinPackageId = body.get("coinPackageId");
        String ipAddr = request.getHeader("X-Forwarded-For");
        if (ipAddr == null || ipAddr.isBlank()) {
            ipAddr = request.getRemoteAddr();
        }
        PaymentTransaction pt = billingService.createPaymentOrder(userId, coinPackageId, ipAddr);
        return ResponseEntity.ok(pt);
    }

    /**
     * Process VNPAY callback result.
     * Called by VNPAY Node.js service after payment completion.
     */
    @PostMapping("/payment/vnpay-callback")
    public ResponseEntity<PaymentTransaction> processVnpayCallback(@RequestBody Map<String, String> body) {
        String orderCode = body.get("orderCode");
        String vnpResponseCode = body.get("vnpResponseCode");
        String vnpTransactionNo = body.getOrDefault("vnpTransactionNo", "");
        String vnpBankCode = body.getOrDefault("vnpBankCode", "");
        String vnpCardType = body.getOrDefault("vnpCardType", "");
        String vnpPayDate = body.getOrDefault("vnpPayDate", "");

        PaymentTransaction result = billingService.processVnpayCallback(
                orderCode, vnpResponseCode, vnpTransactionNo, vnpBankCode, vnpCardType, vnpPayDate);
        return ResponseEntity.ok(result);
    }

    /** Get payment status by orderCode */
    @GetMapping("/payment/status")
    public ResponseEntity<PaymentTransaction> getPaymentStatus(@RequestParam String orderCode) {
        // Reuse the repository via service
        List<PaymentTransaction> payments = billingService.getUserPayments("");
        // Actually get by order code directly
        return ResponseEntity.ok(null); // handled below
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Transaction History ───────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /** All coin transactions for a user */
    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getUserTransactions(@RequestParam String userId) {
        return ResponseEntity.ok(billingService.getUserTransactions(userId));
    }

    /** Deposit-only history */
    @GetMapping("/transactions/deposits")
    public ResponseEntity<List<Transaction>> getUserDeposits(@RequestParam String userId) {
        return ResponseEntity.ok(billingService.getUserDeposits(userId));
    }

    /** Gift-sending history */
    @GetMapping("/transactions/donations")
    public ResponseEntity<List<Transaction>> getUserDonations(@RequestParam String userId) {
        return ResponseEntity.ok(billingService.getUserDonations(userId));
    }

    /** Payment transactions (VNPAY) for a user */
    @GetMapping("/payments")
    public ResponseEntity<List<PaymentTransaction>> getUserPayments(@RequestParam String userId) {
        return ResponseEntity.ok(billingService.getUserPayments(userId));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Gifts ─────────────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /** List active gifts (for users) */
    @GetMapping("/gifts")
    public ResponseEntity<List<Gift>> getGifts() {
        return ResponseEntity.ok(billingService.getAllGifts());
    }

    /** List ALL gifts including inactive (admin) */
    @GetMapping("/gifts/all")
    public ResponseEntity<List<Gift>> getAllGiftsAdmin() {
        return ResponseEntity.ok(billingService.getAllGiftsAdmin());
    }

    /** Create a gift (admin) */
    @PostMapping("/gifts")
    public ResponseEntity<Gift> createGift(@RequestBody Gift gift) {
        return ResponseEntity.ok(billingService.createGift(gift));
    }

    /** Update a gift (admin) */
    @PutMapping("/gifts/{id}")
    public ResponseEntity<Gift> updateGift(@PathVariable String id, @RequestBody Gift updates) {
        return ResponseEntity.ok(billingService.updateGift(id, updates));
    }

    /** Toggle gift visibility (admin) */
    @PatchMapping("/gifts/{id}/toggle")
    public ResponseEntity<Void> toggleGift(@PathVariable String id, @RequestParam boolean active) {
        billingService.toggleGift(id, active);
        return ResponseEntity.ok().build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Donate ────────────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /** Send a gift (donate) */
    @PostMapping("/donate")
    public ResponseEntity<Map<String, Object>> donate(@RequestBody Map<String, String> body) {
        String senderId = body.get("senderId");
        String senderName = body.get("senderName");
        String receiverId = body.get("receiverId");
        String receiverName = body.get("receiverName");
        String giftId = body.get("giftId");
        String roomId = body.get("roomId");
        String giftMessage = body.get("giftMessage");

        Map<String, Object> result = billingService.donate(
                senderId, senderName, receiverId, receiverName, giftId, roomId, giftMessage
        );
        return ResponseEntity.ok(result);
    }

    /** Get top donors for a streamer */
    @GetMapping("/top-donors/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getTopDonors(@PathVariable String userId) {
        return ResponseEntity.ok(billingService.getTopDonors(userId));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Mock Deposit (demo) ───────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /** Mock deposit coins (demo) */
    @PostMapping("/deposit")
    public ResponseEntity<Map<String, Object>> deposit(@RequestBody Map<String, Object> body) {
        String userId = (String) body.get("userId");
        int amount = body.get("amount") instanceof Integer
                ? (Integer) body.get("amount")
                : Integer.parseInt(body.get("amount").toString());

        Wallet wallet = billingService.depositCoins(userId, amount);
        return ResponseEntity.ok(Map.of(
                "userId", wallet.getUserId(),
                "balance", wallet.getBalance()
        ));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Admin Reports ─────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /** Admin billing report */
    @GetMapping("/admin/report")
    public ResponseEntity<Map<String, Object>> getAdminReport() {
        return ResponseEntity.ok(billingService.getAdminReport());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── VIP Livestream Subscriptions ──────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /** Get current VIP info for a user */
    @GetMapping("/vip/info")
    public ResponseEntity<Map<String, Object>> getVipInfo(@RequestParam String userId) {
        var sub = vipService.getVipInfo(userId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("userId", sub.getUserId());
        result.put("vipLevel", sub.getVipLevel());
        result.put("status", sub.getStatus());
        result.put("priceVnd", sub.getPriceVnd());
        result.put("maxLiveDurationMinutes", sub.getMaxLiveDurationMinutes());
        result.put("activatedAt", sub.getActivatedAt());
        result.put("expiresAt", sub.getExpiresAt());
        return ResponseEntity.ok(result);
    }

    /** Get available VIP packages */
    @GetMapping("/vip/packages")
    public ResponseEntity<List<Map<String, Object>>> getVipPackages() {
        return ResponseEntity.ok(vipService.getVipPackages());
    }

    /** Create a VIP payment order (Step 1 — like coin purchase) */
    @PostMapping("/vip/purchase")
    public ResponseEntity<PaymentTransaction> purchaseVip(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String userId = (String) body.get("userId");
        int vipLevel = body.get("vipLevel") instanceof Integer
                ? (Integer) body.get("vipLevel")
                : Integer.parseInt(body.get("vipLevel").toString());
        String ipAddr = request.getHeader("X-Forwarded-For");
        if (ipAddr == null || ipAddr.isBlank()) {
            ipAddr = request.getRemoteAddr();
        }
        PaymentTransaction pt = vipService.createVipPaymentOrder(userId, vipLevel, ipAddr);
        return ResponseEntity.ok(pt);
    }

    /** Process VNPAY callback for VIP purchase (Step 2) */
    @PostMapping("/vip/vnpay-callback")
    public ResponseEntity<PaymentTransaction> processVipCallback(@RequestBody Map<String, String> body) {
        String orderCode = body.get("orderCode");
        String vnpResponseCode = body.get("vnpResponseCode");
        String vnpTransactionNo = body.getOrDefault("vnpTransactionNo", "");
        String vnpBankCode = body.getOrDefault("vnpBankCode", "");
        String vnpCardType = body.getOrDefault("vnpCardType", "");
        String vnpPayDate = body.getOrDefault("vnpPayDate", "");
        PaymentTransaction result = vipService.processVipPaymentCallback(
                orderCode, vnpResponseCode, vnpTransactionNo, vnpBankCode, vnpCardType, vnpPayDate);
        return ResponseEntity.ok(result);
    }
}
