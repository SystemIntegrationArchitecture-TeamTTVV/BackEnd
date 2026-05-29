package edu.iuh.fit.se.mediaservice.payment;

import edu.iuh.fit.se.mediaservice.model.PaymentTransaction;

public interface PaymentStrategy {
    /**
     * Create a payment order.
     */
    PaymentTransaction createOrder(String userId, String packageId, String packageName, long amountVnd, String ipAddress, boolean isVip);

    /**
     * Process provider payment callback.
     */
    PaymentTransaction processCallback(PaymentTransaction pt, String responseCode, String transactionNo, String bankCode, String cardType, String payDate);

    /**
     * Get the payment provider name (e.g. "VNPAY").
     */
    String getProviderName();
}
