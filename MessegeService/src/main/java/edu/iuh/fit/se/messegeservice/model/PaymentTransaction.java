package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Records VNPAY payment transactions for coin purchases.
 * Separated from the in-app Transaction model to isolate payment provider logic.
 */
@Document(collection = "payment_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {
    @Id
    private String id;

    @Indexed(unique = true)
    private String orderCode;            // unique order code sent to VNPAY (vnp_TxnRef)

    @Indexed
    private String userId;

    private String coinPackageId;
    private String coinPackageName;
    private int coinAmount;              // coins to credit (coins + bonus)
    private long amountVnd;              // payment amount in VND

    /** VNPAY payment provider */
    private String paymentProvider = "VNPAY";

    /**
     * PENDING  → Created, waiting for VNPAY redirect
     * SUCCESS  → VNPAY confirmed payment success, coins credited
     * FAILED   → VNPAY returned failure or timeout
     * CANCELLED → User cancelled on VNPAY page
     * EXPIRED  → Payment session expired
     */
    @Indexed
    private String status = "PENDING";

    /** VNPAY transaction number (vnp_TransactionNo) */
    private String vnpTransactionNo;
    /** VNPAY response code */
    private String vnpResponseCode;
    /** VNPAY bank code */
    private String vnpBankCode;
    /** VNPAY card type */
    private String vnpCardType;
    /** VNPAY order info */
    private String vnpOrderInfo;
    /** VNPAY pay date */
    private String vnpPayDate;

    /** Whether coins have been credited (idempotency flag) */
    private boolean coinsCredited = false;

    /** IP address of user when creating payment */
    private String ipAddress;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
