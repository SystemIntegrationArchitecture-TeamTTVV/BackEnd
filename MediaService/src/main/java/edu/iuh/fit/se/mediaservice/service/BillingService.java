package edu.iuh.fit.se.mediaservice.service;

import edu.iuh.fit.se.mediaservice.config.socket.SocketEventTypes;
import edu.iuh.fit.se.mediaservice.dto.SocketEventDTO;
import edu.iuh.fit.se.mediaservice.exception.ResourceNotFoundException;
import edu.iuh.fit.se.mediaservice.model.*;
import edu.iuh.fit.se.mediaservice.repository.*;
import edu.iuh.fit.se.mediaservice.saga.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final WalletRepository walletRepository;
    private final GiftRepository giftRepository;
    private final TransactionRepository transactionRepository;
    private final CoinPackageRepository coinPackageRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final MongoTemplate mongoTemplate;
    private final SocketEmitterService socketEmitterService;
    private final DonationSaga donationSaga;
    private final VnpayCallbackSaga vnpayCallbackSaga;

    // ══════════════════════════════════════════════════════════════════════════
    // ── Wallet ────────────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    public Wallet getOrCreateWallet(String userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            Wallet wallet = new Wallet();
            wallet.setUserId(userId);
            wallet.setBalance(1000); // starting balance for demo
            wallet.setCreatedAt(LocalDateTime.now());
            wallet.setUpdatedAt(LocalDateTime.now());
            return walletRepository.save(wallet);
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Coin Packages ─────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /** Get active coin packages for users */
    public List<CoinPackage> getActiveCoinPackages() {
        List<CoinPackage> packages = coinPackageRepository.findByActiveTrueOrderBySortOrderAsc();
        if (!packages.isEmpty()) {
            return packages;
        }
        // Seed defaults if empty
        seedDefaultCoinPackages();
        return coinPackageRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    /** Get ALL coin packages for admin */
    public List<CoinPackage> getAllCoinPackages() {
        List<CoinPackage> packages = coinPackageRepository.findAllByOrderBySortOrderAsc();
        if (packages.isEmpty()) {
            seedDefaultCoinPackages();
            return coinPackageRepository.findAllByOrderBySortOrderAsc();
        }
        return packages;
    }

    public CoinPackage createCoinPackage(CoinPackage pkg) {
        pkg.setCreatedAt(LocalDateTime.now());
        pkg.setUpdatedAt(LocalDateTime.now());
        log.info("📦 Created coin package: {} ({} coins, {} VND)", pkg.getName(), pkg.getCoins(), pkg.getPriceVnd());
        return coinPackageRepository.save(pkg);
    }

    public CoinPackage updateCoinPackage(String id, CoinPackage updates) {
        CoinPackage pkg = coinPackageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CoinPackage not found: " + id));
        if (updates.getName() != null) pkg.setName(updates.getName());
        if (updates.getCoins() > 0) pkg.setCoins(updates.getCoins());
        pkg.setBonusCoins(updates.getBonusCoins());
        if (updates.getPriceVnd() > 0) pkg.setPriceVnd(updates.getPriceVnd());
        if (updates.getDescription() != null) pkg.setDescription(updates.getDescription());
        pkg.setActive(updates.isActive());
        pkg.setSortOrder(updates.getSortOrder());
        pkg.setUpdatedAt(LocalDateTime.now());
        return coinPackageRepository.save(pkg);
    }

    public void toggleCoinPackage(String id, boolean active) {
        CoinPackage pkg = coinPackageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CoinPackage not found: " + id));
        pkg.setActive(active);
        pkg.setUpdatedAt(LocalDateTime.now());
        coinPackageRepository.save(pkg);
    }

    public void deleteCoinPackage(String id) {
        coinPackageRepository.deleteById(id);
    }

    private void seedDefaultCoinPackages() {
        List<CoinPackage> defaults = List.of(
                new CoinPackage(null, "Gói Tiết Kiệm", 100, 0, 10000, "100 xu", true, 1, null, null),
                new CoinPackage(null, "Gói Phổ Thông", 500, 50, 50000, "500 xu + 50 xu bonus", true, 2, null, null),
                new CoinPackage(null, "Gói Cao Cấp", 1000, 150, 100000, "1000 xu + 150 xu bonus", true, 3, null, null),
                new CoinPackage(null, "Gói VIP", 5000, 1000, 500000, "5000 xu + 1000 xu bonus", true, 4, null, null)
        );
        LocalDateTime now = LocalDateTime.now();
        defaults.forEach(p -> { p.setCreatedAt(now); p.setUpdatedAt(now); });
        coinPackageRepository.saveAll(defaults);
        log.info("📦 Seeded {} default coin packages", defaults.size());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── VNPAY Payment Flow ────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Step 1: Create a payment order for a coin package.
     * Returns a PaymentTransaction in PENDING status.
     * Frontend will use the orderCode to call VNPAY service.
     */
    public PaymentTransaction createPaymentOrder(String userId, String coinPackageId, String ipAddress) {
        CoinPackage pkg = coinPackageRepository.findById(coinPackageId)
                .orElseThrow(() -> new ResourceNotFoundException("CoinPackage not found: " + coinPackageId));

        if (!pkg.isActive()) {
            throw new IllegalStateException("Gói xu này hiện không khả dụng");
        }

        // Generate unique order code: COIN + timestamp + random
        String orderCode = "COIN" + System.currentTimeMillis() + (int)(Math.random() * 1000);

        PaymentTransaction pt = new PaymentTransaction();
        pt.setOrderCode(orderCode);
        pt.setUserId(userId);
        pt.setCoinPackageId(coinPackageId);
        pt.setCoinPackageName(pkg.getName());
        pt.setCoinAmount(pkg.getCoins() + pkg.getBonusCoins());
        pt.setAmountVnd(pkg.getPriceVnd());
        pt.setPaymentProvider("VNPAY");
        pt.setStatus("PENDING");
        pt.setCoinsCredited(false);
        pt.setIpAddress(ipAddress);
        pt.setCreatedAt(LocalDateTime.now());
        pt.setUpdatedAt(LocalDateTime.now());

        PaymentTransaction saved = paymentTransactionRepository.save(pt);
        log.info("💳 Payment order created: orderCode={}, userId={}, amount={} VND, coins={}",
                orderCode, userId, pkg.getPriceVnd(), pt.getCoinAmount());
        return saved;
    }

    /**
     * Step 2: Process VNPAY callback (IPN or Return URL verification).
     * Idempotent — will NOT credit coins twice.
     */
    public PaymentTransaction processVnpayCallback(String orderCode, String vnpResponseCode,
                                                    String vnpTransactionNo, String vnpBankCode,
                                                    String vnpCardType, String vnpPayDate) {
        PaymentTransaction pt = paymentTransactionRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + orderCode));

        // Idempotency: if already processed, skip
        if (pt.isCoinsCredited()) {
            log.warn("⚠️ Payment already processed (idempotent skip): orderCode={}", orderCode);
            return pt;
        }

        // If already in terminal state (not PENDING), skip
        if (!"PENDING".equals(pt.getStatus())) {
            log.warn("⚠️ Payment already in terminal state {}: orderCode={}", pt.getStatus(), orderCode);
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
            // Orchestrate success flow using VnpayCallbackSaga
            pt = vnpayCallbackSaga.execute(pt);
        } else if ("24".equals(vnpResponseCode)) {
            // ── CANCELLED ──
            pt.setStatus("CANCELLED");
            log.info("❌ VNPAY payment CANCELLED: orderCode={}", orderCode);
            pt = paymentTransactionRepository.save(pt);
        } else {
            // ── FAILED ──
            pt.setStatus("FAILED");
            log.info("❌ VNPAY payment FAILED: orderCode={}, responseCode={}", orderCode, vnpResponseCode);
            pt = paymentTransactionRepository.save(pt);
        }

        return pt;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Transaction History ───────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /** All transactions for a user (deposit, donate, receive) */
    public List<Transaction> getUserTransactions(String userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Deposit-only transactions */
    public List<Transaction> getUserDeposits(String userId) {
        return transactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, "deposit");
    }

    /** Gift-sending transactions */
    public List<Transaction> getUserDonations(String userId) {
        return transactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, "donate");
    }

    /** Payment transactions for a user */
    public List<PaymentTransaction> getUserPayments(String userId) {
        return paymentTransactionRepository.findByUserId(userId, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Gifts ─────────────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    public List<Gift> getAllGifts() {
        List<Gift> gifts = giftRepository.findByActiveTrueOrderBySortOrderAsc();
        if (!gifts.isEmpty()) {
            return gifts;
        }
        List<Gift> defaults = createDefaultGifts();
        giftRepository.saveAll(defaults);
        log.info("Seeded {} default gifts for livestream donate testing", defaults.size());
        return giftRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    /** Admin: Get all gifts including inactive */
    public List<Gift> getAllGiftsAdmin() {
        List<Gift> gifts = giftRepository.findAllByOrderBySortOrderAsc();
        if (gifts.isEmpty()) {
            giftRepository.saveAll(createDefaultGifts());
            return giftRepository.findAllByOrderBySortOrderAsc();
        }
        return gifts;
    }

    public Gift createGift(Gift gift) {
        gift.setCreatedAt(LocalDateTime.now());
        gift.setUpdatedAt(LocalDateTime.now());
        return giftRepository.save(gift);
    }

    public Gift updateGift(String id, Gift updates) {
        Gift gift = giftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gift not found: " + id));
        if (updates.getName() != null) gift.setName(updates.getName());
        if (updates.getPrice() > 0) gift.setPrice(updates.getPrice());
        if (updates.getEmoji() != null) gift.setEmoji(updates.getEmoji());
        if (updates.getImageUrl() != null) gift.setImageUrl(updates.getImageUrl());
        if (updates.getCategory() != null) gift.setCategory(updates.getCategory());
        gift.setActive(updates.isActive());
        gift.setSortOrder(updates.getSortOrder());
        gift.setUpdatedAt(LocalDateTime.now());
        return giftRepository.save(gift);
    }

    public void toggleGift(String id, boolean active) {
        Gift gift = giftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gift not found: " + id));
        gift.setActive(active);
        gift.setUpdatedAt(LocalDateTime.now());
        giftRepository.save(gift);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Donate ────────────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    public Map<String, Object> donate(String senderId, String senderName,
                                       String receiverId, String receiverName,
                                       String giftId, String roomId,
                                       String giftMessage) {
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Cannot donate to yourself");
        }

        Gift gift = giftRepository.findById(giftId)
                .orElseThrow(() -> new ResourceNotFoundException("Gift not found: " + giftId));

        if (!gift.isActive()) {
            throw new IllegalStateException("Quà tặng này hiện không khả dụng");
        }

        // Run the DonationSaga in a retry loop to handle OptimisticLockingFailureException
        int maxAttempts = 3;
        int attempt = 0;
        while (true) {
            try {
                attempt++;
                DonationSaga.DonateCommand cmd = DonationSaga.DonateCommand.builder()
                        .senderId(senderId)
                        .senderName(senderName)
                        .receiverId(receiverId)
                        .receiverName(receiverName)
                        .gift(gift)
                        .roomId(roomId)
                        .giftMessage(giftMessage)
                        .build();

                Map<String, Object> sagaResult = donationSaga.execute(cmd);

                // Construct result map matching original contract
                Wallet updatedSenderWallet = getOrCreateWallet(senderId);
                String senderTxId = (String) sagaResult.get("senderTxId");
                Transaction senderTx = transactionRepository.findById(senderTxId).orElse(null);

                Map<String, Object> result = new HashMap<>();
                result.put("senderBalance", updatedSenderWallet.getBalance());
                result.put("gift", gift);
                result.put("transaction", senderTx);
                return result;

            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                log.warn("[DONATE-RETRY] OptimisticLockingFailureException on attempt {} of {}: {}", attempt, maxAttempts, e.getMessage());
                if (attempt >= maxAttempts) {
                    log.error("[DONATE-RETRY] Failed to complete donate transaction after {} attempts due to concurrent updates", maxAttempts);
                    throw e;
                }
                // Small backoff before retrying
                try {
                    Thread.sleep(50 + (int) (Math.random() * 50));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Top Donors ────────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    public List<Map<String, Object>> getTopDonors(String receiverId) {
        MatchOperation match = Aggregation.match(
                Criteria.where("receiverId").is(receiverId)
                        .and("type").is("receive")
                        .and("status").is("success")
        );
        GroupOperation group = Aggregation.group("senderId")
                .first("senderName").as("senderName")
                .sum("amount").as("totalCoins");
        SortOperation sort = Aggregation.sort(Sort.Direction.DESC, "totalCoins");
        LimitOperation limit = Aggregation.limit(5);

        Aggregation aggregation = Aggregation.newAggregation(match, group, sort, limit);
        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "transactions", Map.class);

        List<Map<String, Object>> donors = new ArrayList<>();
        for (Map raw : results.getMappedResults()) {
            Map<String, Object> donor = new HashMap<>();
            donor.put("senderId", raw.get("_id"));
            donor.put("senderName", raw.get("senderName"));
            donor.put("totalCoins", raw.get("totalCoins"));
            donors.add(donor);
        }
        return donors;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Deposit (mock for demo / fallback) ────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    public Wallet depositCoins(String userId, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Wallet wallet = getOrCreateWallet(userId);
        wallet.setBalance(wallet.getBalance() + amount);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setType("deposit");
        tx.setAmount(amount);
        tx.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        log.info("💰 Deposit: userId={} amount={} newBalance={}", userId, amount, wallet.getBalance());

        // Emit socket event
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("balance", wallet.getBalance());
            payload.put("coinAmount", amount);

            SocketEventDTO event = SocketEventDTO.of(
                    SocketEventTypes.COIN_DEPOSITED, userId, payload);
            socketEmitterService.emitToUserById(userId, event);
        } catch (Exception e) {
            log.error("Failed to emit COIN_DEPOSITED: {}", e.getMessage());
        }

        return wallet;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Admin Reports ─────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Generate admin billing report.
     */
    public Map<String, Object> getAdminReport() {
        Map<String, Object> report = new HashMap<>();

        // Total revenue from successful payments
        List<PaymentTransaction> allSuccess = paymentTransactionRepository
                .findByUserId(null, Sort.unsorted()); // we'll use aggregation instead

        // Use aggregation for total revenue
        try {
            MatchOperation matchSuccess = Aggregation.match(Criteria.where("status").is("SUCCESS"));
            GroupOperation groupTotal = Aggregation.group()
                    .sum("amountVnd").as("totalRevenue")
                    .count().as("totalTransactions");
            Aggregation revenueAgg = Aggregation.newAggregation(matchSuccess, groupTotal);
            AggregationResults<Map> revenueResults = mongoTemplate.aggregate(revenueAgg, "payment_transactions", Map.class);
            if (!revenueResults.getMappedResults().isEmpty()) {
                Map raw = revenueResults.getMappedResults().get(0);
                report.put("totalRevenue", raw.get("totalRevenue"));
                report.put("totalSuccessPayments", raw.get("totalTransactions"));
            } else {
                report.put("totalRevenue", 0);
                report.put("totalSuccessPayments", 0);
            }
        } catch (Exception e) {
            report.put("totalRevenue", 0);
            report.put("totalSuccessPayments", 0);
        }

        // Total transactions (all types)
        report.put("totalPayments", paymentTransactionRepository.count());

        // Total gift transactions
        report.put("totalGiftTransactions", transactionRepository.countByType("donate"));

        // Top depositors
        try {
            MatchOperation matchDeposit = Aggregation.match(Criteria.where("status").is("SUCCESS"));
            GroupOperation groupDeposit = Aggregation.group("userId")
                    .sum("amountVnd").as("totalSpent")
                    .count().as("count");
            SortOperation sortDeposit = Aggregation.sort(Sort.Direction.DESC, "totalSpent");
            LimitOperation limitDeposit = Aggregation.limit(10);
            Aggregation depositAgg = Aggregation.newAggregation(matchDeposit, groupDeposit, sortDeposit, limitDeposit);
            AggregationResults<Map> depositResults = mongoTemplate.aggregate(depositAgg, "payment_transactions", Map.class);

            List<Map<String, Object>> topDepositors = new ArrayList<>();
            for (Map raw : depositResults.getMappedResults()) {
                Map<String, Object> depositor = new HashMap<>();
                depositor.put("userId", raw.get("_id"));
                depositor.put("totalSpent", raw.get("totalSpent"));
                depositor.put("count", raw.get("count"));
                topDepositors.add(depositor);
            }
            report.put("topDepositors", topDepositors);
        } catch (Exception e) {
            report.put("topDepositors", List.of());
        }

        // Top streamers receiving gifts
        try {
            MatchOperation matchReceive = Aggregation.match(
                    Criteria.where("type").is("receive").and("status").is("success"));
            GroupOperation groupReceive = Aggregation.group("receiverId")
                    .first("receiverName").as("receiverName")
                    .sum("amount").as("totalCoins")
                    .count().as("giftCount");
            SortOperation sortReceive = Aggregation.sort(Sort.Direction.DESC, "totalCoins");
            LimitOperation limitReceive = Aggregation.limit(10);
            Aggregation receiveAgg = Aggregation.newAggregation(matchReceive, groupReceive, sortReceive, limitReceive);
            AggregationResults<Map> receiveResults = mongoTemplate.aggregate(receiveAgg, "transactions", Map.class);

            List<Map<String, Object>> topStreamers = new ArrayList<>();
            for (Map raw : receiveResults.getMappedResults()) {
                Map<String, Object> streamer = new HashMap<>();
                streamer.put("receiverId", raw.get("_id"));
                streamer.put("receiverName", raw.get("receiverName"));
                streamer.put("totalCoins", raw.get("totalCoins"));
                streamer.put("giftCount", raw.get("giftCount"));
                topStreamers.add(streamer);
            }
            report.put("topStreamers", topStreamers);
        } catch (Exception e) {
            report.put("topStreamers", List.of());
        }

        return report;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Default Gifts Seed ────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    private List<Gift> createDefaultGifts() {
        LocalDateTime now = LocalDateTime.now();
        List<Gift> gifts = List.of(
                new Gift(null, "Hoa hồng", 10, "🌹", null, "popular", true, 1, now, now),
                new Gift(null, "Cà phê", 20, "☕", null, "popular", true, 2, now, now),
                new Gift(null, "Trà sữa", 30, "🧋", null, "popular", true, 3, now, now),
                new Gift(null, "Ngôi sao", 50, "⭐", null, "popular", true, 4, now, now),
                new Gift(null, "Kim cương", 100, "💎", null, "premium", true, 5, now, now),
                new Gift(null, "Siêu xe", 500, "🏎️", null, "premium", true, 6, now, now),
                new Gift(null, "Tên lửa", 1000, "🚀", null, "premium", true, 7, now, now),
                new Gift(null, "Lâu đài", 5000, "🏰", null, "premium", true, 8, now, now)
        );
        return gifts;
    }
}
