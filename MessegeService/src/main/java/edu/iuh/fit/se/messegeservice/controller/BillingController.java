package edu.iuh.fit.se.messegeservice.controller;

import edu.iuh.fit.se.messegeservice.model.Gift;
import edu.iuh.fit.se.messegeservice.model.Wallet;
import edu.iuh.fit.se.messegeservice.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    /** Get or create wallet for a user */
    @GetMapping("/wallet")
    public ResponseEntity<Map<String, Object>> getWallet(@RequestParam String userId) {
        Wallet wallet = billingService.getOrCreateWallet(userId);
        return ResponseEntity.ok(Map.of(
                "userId", wallet.getUserId(),
                "balance", wallet.getBalance()
        ));
    }

    /** List all available gifts */
    @GetMapping("/gifts")
    public ResponseEntity<List<Gift>> getGifts() {
        return ResponseEntity.ok(billingService.getAllGifts());
    }

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
}
