package edu.iuh.fit.se.mediaservice.saga;

import edu.iuh.fit.se.mediaservice.config.socket.SocketEventTypes;
import edu.iuh.fit.se.mediaservice.dto.SocketEventDTO;
import edu.iuh.fit.se.mediaservice.exception.ResourceNotFoundException;
import edu.iuh.fit.se.mediaservice.model.*;
import edu.iuh.fit.se.mediaservice.repository.*;
import edu.iuh.fit.se.mediaservice.service.SocketEmitterService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DonationSaga {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletEventRepository walletEventRepository;
    private final SocketEmitterService socketEmitterService;

    public enum Step {
        DEBIT_SENDER,
        CREDIT_RECEIVER,
        LOG_SENDER_TX,
        LOG_RECEIVER_TX,
        EMIT_EVENT
    }

    @Data
    @Builder
    public static class DonateCommand {
        private String senderId;
        private String senderName;
        private String receiverId;
        private String receiverName;
        private Gift gift;
        private String roomId;
        private String giftMessage;
    }

    public Map<String, Object> execute(DonateCommand cmd) {
        Step currentStep = Step.DEBIT_SENDER;
        Transaction senderTx = null;
        Transaction receiverTx = null;

        try {
            // Step 1: Debit sender wallet
            log.info("[SAGA] Starting DonationSaga step 1: Debit sender {}", cmd.getSenderId());
            debitSender(cmd.getSenderId(), cmd.getGift().getPrice());

            // Step 2: Credit receiver wallet
            currentStep = Step.CREDIT_RECEIVER;
            log.info("[SAGA] DonationSaga step 2: Credit receiver {}", cmd.getReceiverId());
            creditReceiver(cmd.getReceiverId(), cmd.getGift().getPrice());

            // Step 3: Log sender transaction
            currentStep = Step.LOG_SENDER_TX;
            log.info("[SAGA] DonationSaga step 3: Save sender transaction");
            senderTx = logSenderTransaction(cmd);

            // Step 4: Log receiver transaction
            currentStep = Step.LOG_RECEIVER_TX;
            log.info("[SAGA] DonationSaga step 4: Save receiver transaction");
            receiverTx = logReceiverTransaction(cmd);

            // Record WalletEvent audit logs (Observability & Event Sourcing)
            logWalletEvents(cmd, senderTx, receiverTx);

            // Step 5: Emit socket event (Best effort, does not trigger compensation if fails)
            currentStep = Step.EMIT_EVENT;
            log.info("[SAGA] DonationSaga step 5: Emit socket events");
            emitGiftEvent(cmd);

            // Construct and return success result
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("senderTxId", senderTx.getId());
            result.put("receiverTxId", receiverTx.getId());
            result.put("senderName", cmd.getSenderName());
            result.put("receiverName", cmd.getReceiverName());
            result.put("giftName", cmd.getGift().getName());
            result.put("price", cmd.getGift().getPrice());
            return result;

        } catch (Exception e) {
            log.error("[SAGA] Exception occurred in step {}: {}. Triggering compensating transactions.", currentStep, e.getMessage(), e);
            compensate(currentStep, cmd, senderTx, receiverTx);
            throw e;
        }
    }

    private void debitSender(String senderId, int amount) {
        Wallet senderWallet = walletRepository.findByUserId(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender wallet not found: " + senderId));
        if (senderWallet.getBalance() < amount) {
            throw new IllegalStateException("Insufficient balance. Need " + amount + " coins, have " + senderWallet.getBalance());
        }
        senderWallet.setBalance(senderWallet.getBalance() - amount);
        senderWallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(senderWallet);
    }

    private void creditReceiver(String receiverId, int amount) {
        Wallet receiverWallet = walletRepository.findByUserId(receiverId).orElseGet(() -> {
            Wallet wallet = new Wallet();
            wallet.setUserId(receiverId);
            wallet.setBalance(1000); // default balance
            wallet.setCreatedAt(LocalDateTime.now());
            wallet.setUpdatedAt(LocalDateTime.now());
            return walletRepository.save(wallet); // Save immediately to database to generate ID
        });
        receiverWallet.setBalance(receiverWallet.getBalance() + amount);
        receiverWallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(receiverWallet);
    }

    private Transaction logSenderTransaction(DonateCommand cmd) {
        Transaction tx = new Transaction();
        tx.setUserId(cmd.getSenderId());
        tx.setType("donate");
        tx.setAmount(cmd.getGift().getPrice());
        tx.setGiftId(cmd.getGift().getId());
        tx.setGiftName(cmd.getGift().getName());
        tx.setRoomId(cmd.getRoomId());
        tx.setSenderId(cmd.getSenderId());
        tx.setSenderName(cmd.getSenderName());
        tx.setReceiverId(cmd.getReceiverId());
        tx.setReceiverName(cmd.getReceiverName());
        tx.setGiftMessage(cmd.getGiftMessage());
        tx.setCreatedAt(LocalDateTime.now());
        return transactionRepository.save(tx);
    }

    private Transaction logReceiverTransaction(DonateCommand cmd) {
        Transaction tx = new Transaction();
        tx.setUserId(cmd.getReceiverId());
        tx.setType("receive");
        tx.setAmount(cmd.getGift().getPrice());
        tx.setGiftId(cmd.getGift().getId());
        tx.setGiftName(cmd.getGift().getName());
        tx.setRoomId(cmd.getRoomId());
        tx.setSenderId(cmd.getSenderId());
        tx.setSenderName(cmd.getSenderName());
        tx.setReceiverId(cmd.getReceiverId());
        tx.setReceiverName(cmd.getReceiverName());
        tx.setGiftMessage(cmd.getGiftMessage());
        tx.setCreatedAt(LocalDateTime.now());
        return transactionRepository.save(tx);
    }

    private void logWalletEvents(DonateCommand cmd, Transaction senderTx, Transaction receiverTx) {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // 1. Sender Debit Event
            Wallet senderWallet = walletRepository.findByUserId(cmd.getSenderId()).orElse(null);
            if (senderWallet != null) {
                WalletEvent debitEvent = new WalletEvent();
                debitEvent.setWalletId(senderWallet.getId());
                debitEvent.setUserId(cmd.getSenderId());
                debitEvent.setEventType("DEBIT");
                debitEvent.setAmount(cmd.getGift().getPrice());
                debitEvent.setBalanceBefore(senderWallet.getBalance() + cmd.getGift().getPrice());
                debitEvent.setBalanceAfter(senderWallet.getBalance());
                debitEvent.setReferenceType("DONATE");
                debitEvent.setReferenceId(senderTx != null ? senderTx.getId() : null);
                debitEvent.setCreatedAt(now);
                walletEventRepository.save(debitEvent);
                log.info("[SAGA-AUDIT] Recorded sender DEBIT WalletEvent: walletId={}", senderWallet.getId());
            }

            // 2. Receiver Credit Event
            Wallet receiverWallet = walletRepository.findByUserId(cmd.getReceiverId()).orElse(null);
            if (receiverWallet != null) {
                WalletEvent creditEvent = new WalletEvent();
                creditEvent.setWalletId(receiverWallet.getId());
                creditEvent.setUserId(cmd.getReceiverId());
                creditEvent.setEventType("CREDIT");
                creditEvent.setAmount(cmd.getGift().getPrice());
                creditEvent.setBalanceBefore(receiverWallet.getBalance() - cmd.getGift().getPrice());
                creditEvent.setBalanceAfter(receiverWallet.getBalance());
                creditEvent.setReferenceType("GIFT_RECEIVE");
                creditEvent.setReferenceId(receiverTx != null ? receiverTx.getId() : null);
                creditEvent.setCreatedAt(now);
                walletEventRepository.save(creditEvent);
                log.info("[SAGA-AUDIT] Recorded receiver CREDIT WalletEvent: walletId={}", receiverWallet.getId());
            }
        } catch (Exception e) {
            log.error("[SAGA-AUDIT] Failed to record WalletEvent audit logs: {}", e.getMessage(), e);
        }
    }

    private void emitGiftEvent(DonateCommand cmd) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("senderId", cmd.getSenderId());
            payload.put("senderName", cmd.getSenderName());
            payload.put("giftId", cmd.getGift().getId());
            payload.put("giftName", cmd.getGift().getName());
            payload.put("giftEmoji", cmd.getGift().getEmoji());
            payload.put("giftPrice", cmd.getGift().getPrice());
            payload.put("roomId", cmd.getRoomId());
            payload.put("receiverId", cmd.getReceiverId());
            payload.put("giftMessage", cmd.getGiftMessage());

            SocketEventDTO event = SocketEventDTO.of(
                    SocketEventTypes.LIVE_GIFT_RECEIVED, cmd.getSenderId(), payload);
            socketEmitterService.emitToAll(event);
        } catch (Exception e) {
            log.error("[SAGA] Failed to emit socket event LIVE_GIFT_RECEIVED: {}", e.getMessage());
        }
    }

    private void compensate(Step failedStep, DonateCommand cmd, Transaction senderTx, Transaction receiverTx) {
        switch (failedStep) {
            case EMIT_EVENT:
                // If emit fails, do not roll back transactions because money transfer is successful and recorded.
                break;
            case LOG_RECEIVER_TX:
                // Rollback Step 3: Delete sender transaction
                if (senderTx != null && senderTx.getId() != null) {
                    try {
                        log.info("[SAGA-COMPENSATE] Deleting sender transaction: {}", senderTx.getId());
                        transactionRepository.deleteById(senderTx.getId());
                    } catch (Exception e) {
                        log.error("[SAGA-COMPENSATE] Failed to delete sender transaction", e);
                    }
                }
            case LOG_SENDER_TX:
                // Rollback Step 2: Reverse receiver credit
                try {
                    log.info("[SAGA-COMPENSATE] Reversing receiver credit for {}", cmd.getReceiverId());
                    reverseCredit(cmd.getReceiverId(), cmd.getGift().getPrice());
                } catch (Exception e) {
                    log.error("[SAGA-COMPENSATE] Failed to reverse receiver credit", e);
                }
            case CREDIT_RECEIVER:
                // Rollback Step 1: Refund sender debit
                try {
                    log.info("[SAGA-COMPENSATE] Refunding sender debit for {}", cmd.getSenderId());
                    refundDebit(cmd.getSenderId(), cmd.getGift().getPrice());
                } catch (Exception e) {
                    log.error("[SAGA-COMPENSATE] Failed to refund sender debit", e);
                }
                break;
            default:
                break;
        }
    }

    private void reverseCredit(String userId, int amount) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver wallet not found: " + userId));
        wallet.setBalance(wallet.getBalance() - amount);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);
    }

    private void refundDebit(String userId, int amount) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender wallet not found: " + userId));
        wallet.setBalance(wallet.getBalance() + amount);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);
    }
}
