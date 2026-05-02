package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.config.socket.SocketEventTypes;
import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.exception.ResourceNotFoundException;
import edu.iuh.fit.se.messegeservice.model.Gift;
import edu.iuh.fit.se.messegeservice.model.Transaction;
import edu.iuh.fit.se.messegeservice.model.Wallet;
import edu.iuh.fit.se.messegeservice.repository.GiftRepository;
import edu.iuh.fit.se.messegeservice.repository.TransactionRepository;
import edu.iuh.fit.se.messegeservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final WalletRepository walletRepository;
    private final GiftRepository giftRepository;
    private final TransactionRepository transactionRepository;
    private final MongoTemplate mongoTemplate;
    private final SocketEmitterService socketEmitterService;

    // ── Wallet ──────────────────────────────────────────────────────────────

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

    // ── Gifts ───────────────────────────────────────────────────────────────

    public List<Gift> getAllGifts() {
        List<Gift> gifts = giftRepository.findAll();
        if (!gifts.isEmpty()) {
            return gifts;
        }
        List<Gift> defaults = createDefaultGifts();
        giftRepository.saveAll(defaults);
        log.info("Seeded {} default gifts for livestream donate testing", defaults.size());
        return giftRepository.findAll();
    }

    // ── Donate ──────────────────────────────────────────────────────────────

    public Map<String, Object> donate(String senderId, String senderName,
                                       String receiverId, String receiverName,
                                       String giftId, String roomId,
                                       String giftMessage) {
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Cannot donate to yourself");
        }

        Gift gift = giftRepository.findById(giftId)
                .orElseThrow(() -> new ResourceNotFoundException("Gift not found: " + giftId));

        Wallet senderWallet = getOrCreateWallet(senderId);
        if (senderWallet.getBalance() < gift.getPrice()) {
            throw new IllegalStateException("Insufficient balance. Need " + gift.getPrice() + " coins, have " + senderWallet.getBalance());
        }

        // Atomic balance update
        senderWallet.setBalance(senderWallet.getBalance() - gift.getPrice());
        senderWallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(senderWallet);

        Wallet receiverWallet = getOrCreateWallet(receiverId);
        receiverWallet.setBalance(receiverWallet.getBalance() + gift.getPrice());
        receiverWallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(receiverWallet);

        // Create transactions
        Transaction senderTx = new Transaction();
        senderTx.setUserId(senderId);
        senderTx.setType("donate");
        senderTx.setAmount(gift.getPrice());
        senderTx.setGiftId(giftId);
        senderTx.setGiftName(gift.getName());
        senderTx.setRoomId(roomId);
        senderTx.setSenderId(senderId);
        senderTx.setSenderName(senderName);
        senderTx.setReceiverId(receiverId);
        senderTx.setReceiverName(receiverName);
        senderTx.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(senderTx);

        Transaction receiverTx = new Transaction();
        receiverTx.setUserId(receiverId);
        receiverTx.setType("receive");
        receiverTx.setAmount(gift.getPrice());
        receiverTx.setGiftId(giftId);
        receiverTx.setGiftName(gift.getName());
        receiverTx.setRoomId(roomId);
        receiverTx.setSenderId(senderId);
        receiverTx.setSenderName(senderName);
        receiverTx.setReceiverId(receiverId);
        receiverTx.setReceiverName(receiverName);
        receiverTx.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(receiverTx);

        log.info("🎁 Donate: {} → {} | gift={} price={}", senderName, receiverName, gift.getName(), gift.getPrice());

        // Emit socket event for gift received
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("senderId", senderId);
            payload.put("senderName", senderName);
            payload.put("giftId", giftId);
            payload.put("giftName", gift.getName());
            payload.put("giftEmoji", gift.getEmoji());
            payload.put("giftPrice", gift.getPrice());
            payload.put("roomId", roomId);
            payload.put("receiverId", receiverId);
            payload.put("giftMessage", giftMessage);

            SocketEventDTO event = SocketEventDTO.of(
                    SocketEventTypes.LIVE_GIFT_RECEIVED, senderId, payload);
            socketEmitterService.emitToAll(event);
        } catch (Exception e) {
            log.error("Failed to emit LIVE_GIFT_RECEIVED: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("senderBalance", senderWallet.getBalance());
        result.put("gift", gift);
        result.put("transaction", senderTx);
        return result;
    }

    // ── Top Donors ──────────────────────────────────────────────────────────

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

    // ── Deposit (mock for demo) ─────────────────────────────────────────────

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

    private List<Gift> createDefaultGifts() {
        return List.of(
                new Gift(null, "Hoa hồng", 10, "🌹", null, "popular"),
                new Gift(null, "Cà phê", 20, "☕", null, "popular"),
                new Gift(null, "Trà sữa", 30, "🧋", null, "popular"),
                new Gift(null, "Ngôi sao", 50, "⭐", null, "popular"),
                new Gift(null, "Kim cương", 100, "💎", null, "premium"),
                new Gift(null, "Siêu xe", 500, "🏎️", null, "premium"),
                new Gift(null, "Tên lửa", 1000, "🚀", null, "premium"),
                new Gift(null, "Lâu đài", 5000, "🏰", null, "premium")
        );
    }
}
