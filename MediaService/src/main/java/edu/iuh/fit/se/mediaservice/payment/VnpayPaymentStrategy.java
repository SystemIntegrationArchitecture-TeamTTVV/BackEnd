package edu.iuh.fit.se.mediaservice.payment;

import edu.iuh.fit.se.mediaservice.model.PaymentTransaction;
import edu.iuh.fit.se.mediaservice.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class VnpayPaymentStrategy implements PaymentStrategy {

    private final PaymentTransactionRepository paymentTransactionRepository;

    @Override
    public PaymentTransaction createOrder(String userId, String packageId, String packageName, long amountVnd, String ipAddress, boolean isVip) {
        String prefix = isVip ? "VIP" : "COIN";
        String orderCode = prefix + System.currentTimeMillis() + (int)(Math.random() * 1000);

        PaymentTransaction pt = new PaymentTransaction();
        pt.setOrderCode(orderCode);
        pt.setUserId(userId);
        pt.setCoinPackageId(packageId);
        pt.setCoinPackageName(packageName);
        pt.setCoinAmount(0); // Custom amount can be set in the service later
        pt.setAmountVnd(amountVnd);
        pt.setPaymentProvider(getProviderName());
        pt.setStatus("PENDING");
        pt.setCoinsCredited(false);
        pt.setIpAddress(ipAddress);
        pt.setCreatedAt(LocalDateTime.now());
        pt.setUpdatedAt(LocalDateTime.now());

        PaymentTransaction saved = paymentTransactionRepository.save(pt);
        log.info("💳 [VNPAY] Payment order created: orderCode={}, userId={}, amount={} VND, isVip={}",
                orderCode, userId, amountVnd, isVip);
        return saved;
    }

    @Override
    public PaymentTransaction processCallback(PaymentTransaction pt, String responseCode, String transactionNo, String bankCode, String cardType, String payDate) {
        pt.setVnpResponseCode(responseCode);
        pt.setVnpTransactionNo(transactionNo);
        pt.setVnpBankCode(bankCode);
        pt.setVnpCardType(cardType);
        pt.setVnpPayDate(payDate);
        pt.setUpdatedAt(LocalDateTime.now());
        pt.setCompletedAt(LocalDateTime.now());
        return pt;
    }

    @Override
    public String getProviderName() {
        return "VNPAY";
    }
}
