package edu.iuh.fit.se.mediaservice.saga;

import edu.iuh.fit.se.mediaservice.config.socket.SocketEventTypes;
import edu.iuh.fit.se.mediaservice.dto.SocketEventDTO;
import edu.iuh.fit.se.mediaservice.exception.ResourceNotFoundException;
import edu.iuh.fit.se.mediaservice.model.*;
import edu.iuh.fit.se.mediaservice.repository.*;
import edu.iuh.fit.se.mediaservice.service.SocketEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class VnpayCallbackSaga {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WalletEventRepository walletEventRepository;
    private final SocketEmitterService socketEmitterService;

    public enum Step {
        CREDIT_WALLET,
        LOG_DEPOSIT_TX,
        UPDATE_PAYMENT_TX,
        EMIT_EVENT
    }

    public PaymentTransaction execute(PaymentTransaction pt) {
        Step currentStep = Step.CREDIT_WALLET;
        Transaction tx = null;

        try {
            // Step 1: Credit coins to wallet
            log.info("[SAGA-VNPAY] Step 1: Credit {} coins to user {}", pt.getCoinAmount(), pt.getUserId());
            creditWallet(pt.getUserId(), pt.getCoinAmount());

            // Step 2: Record transaction
            currentStep = Step.LOG_DEPOSIT_TX;
            log.info("[SAGA-VNPAY] Step 2: Record deposit transaction");
            tx = logDepositTransaction(pt);

            // Step 3: Update PaymentTransaction
            currentStep = Step.UPDATE_PAYMENT_TX;
            log.info("[SAGA-VNPAY] Step 3: Update payment transaction status to SUCCESS");
            pt.setStatus("SUCCESS");
            pt.setCoinsCredited(true);
            pt.setUpdatedAt(LocalDateTime.now());
            pt.setCompletedAt(LocalDateTime.now());
            pt = paymentTransactionRepository.save(pt);

            // Record WalletEvent audit logs (Observability & Event Sourcing)
            logWalletEvent(pt, tx);

            // Step 4: Emit socket event (Best effort)
            currentStep = Step.EMIT_EVENT;
            log.info("[SAGA-VNPAY] Step 4: Emit COIN_DEPOSITED event");
            emitCoinDepositedEvent(pt);

            return pt;
        } catch (Exception e) {
            log.error("[SAGA-VNPAY] Exception occurred in step {}: {}. Rolling back.", currentStep, e.getMessage(), e);
            compensate(currentStep, pt, tx);
            throw e;
        }
    }

    private void creditWallet(String userId, int amount) {
        Wallet wallet = walletRepository.findByUserId(userId).orElseGet(() -> {
            Wallet w = new Wallet();
            w.setUserId(userId);
            w.setBalance(1000); // starting
            w.setCreatedAt(LocalDateTime.now());
            w.setUpdatedAt(LocalDateTime.now());
            return w;
        });
        wallet.setBalance(wallet.getBalance() + amount);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);
    }

    private Transaction logDepositTransaction(PaymentTransaction pt) {
        Transaction tx = new Transaction();
        tx.setUserId(pt.getUserId());
        tx.setType("deposit");
        tx.setAmount(pt.getCoinAmount());
        tx.setStatus("success");
        tx.setCreatedAt(LocalDateTime.now());
        return transactionRepository.save(tx);
    }

    private void logWalletEvent(PaymentTransaction pt, Transaction tx) {
        try {
            Wallet wallet = walletRepository.findByUserId(pt.getUserId()).orElse(null);
            if (wallet != null) {
                WalletEvent creditEvent = new WalletEvent();
                creditEvent.setWalletId(wallet.getId());
                creditEvent.setUserId(pt.getUserId());
                creditEvent.setEventType("CREDIT");
                creditEvent.setAmount(pt.getCoinAmount());
                creditEvent.setBalanceBefore(wallet.getBalance() - pt.getCoinAmount());
                creditEvent.setBalanceAfter(wallet.getBalance());
                creditEvent.setReferenceType("DEPOSIT");
                creditEvent.setReferenceId(tx != null ? tx.getId() : pt.getId());
                creditEvent.setCreatedAt(LocalDateTime.now());
                walletEventRepository.save(creditEvent);
                log.info("[SAGA-VNPAY-AUDIT] Recorded deposit CREDIT WalletEvent: walletId={}", wallet.getId());
            }
        } catch (Exception e) {
            log.error("[SAGA-VNPAY-AUDIT] Failed to record WalletEvent audit logs: {}", e.getMessage(), e);
        }
    }

    private void emitCoinDepositedEvent(PaymentTransaction pt) {
        try {
            // Find current wallet balance to reflect accurately
            Wallet wallet = walletRepository.findByUserId(pt.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + pt.getUserId()));

            Map<String, Object> payload = new HashMap<>();
            payload.put("balance", wallet.getBalance());
            payload.put("coinAmount", pt.getCoinAmount());
            payload.put("packageName", pt.getCoinPackageName());
            payload.put("orderCode", pt.getOrderCode());

            SocketEventDTO event = SocketEventDTO.of(
                    SocketEventTypes.COIN_DEPOSITED, pt.getUserId(), payload);
            socketEmitterService.emitToUserById(pt.getUserId(), event);
        } catch (Exception e) {
            log.error("[SAGA-VNPAY] Failed to emit COIN_DEPOSITED event: {}", e.getMessage());
        }
    }

    private void compensate(Step failedStep, PaymentTransaction pt, Transaction tx) {
        switch (failedStep) {
            case EMIT_EVENT:
                // No compensation needed if socket emission fails
                break;
            case UPDATE_PAYMENT_TX:
                // Rollback Step 2: Delete deposit transaction record
                if (tx != null && tx.getId() != null) {
                    try {
                        log.info("[SAGA-VNPAY-COMPENSATE] Deleting deposit transaction: {}", tx.getId());
                        transactionRepository.deleteById(tx.getId());
                    } catch (Exception e) {
                        log.error("[SAGA-VNPAY-COMPENSATE] Failed to delete deposit transaction", e);
                    }
                }
            case LOG_DEPOSIT_TX:
                // Rollback Step 1: Reverse wallet credit
                try {
                    log.info("[SAGA-VNPAY-COMPENSATE] Reversing wallet credit for user {}", pt.getUserId());
                    reverseWalletCredit(pt.getUserId(), pt.getCoinAmount());
                } catch (Exception e) {
                    log.error("[SAGA-VNPAY-COMPENSATE] Failed to reverse wallet credit", e);
                }
                break;
            default:
                break;
        }
    }

    private void reverseWalletCredit(String userId, int amount) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for user: " + userId));
        wallet.setBalance(wallet.getBalance() - amount);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);
    }
}
