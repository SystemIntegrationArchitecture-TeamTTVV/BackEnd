package edu.iuh.fit.se.mediaservice.payment;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentStrategyFactory {

    private final Map<String, PaymentStrategy> strategies = new ConcurrentHashMap<>();
    private final PaymentStrategy defaultStrategy;

    public PaymentStrategyFactory(List<PaymentStrategy> strategyList) {
        if (strategyList == null || strategyList.isEmpty()) {
            throw new IllegalStateException("No payment strategies registered");
        }
        PaymentStrategy vnpay = null;
        for (PaymentStrategy strategy : strategyList) {
            strategies.put(strategy.getProviderName().toUpperCase(), strategy);
            if ("VNPAY".equalsIgnoreCase(strategy.getProviderName())) {
                vnpay = strategy;
            }
        }
        // Fallback to the first registered strategy if VNPAY is not found
        this.defaultStrategy = (vnpay != null) ? vnpay : strategyList.getFirst();
    }

    /**
     * Resolve the payment strategy by provider name (case-insensitive)
     */
    public PaymentStrategy getStrategy(String provider) {
        if (provider == null || provider.isBlank()) {
            return defaultStrategy;
        }
        return strategies.getOrDefault(provider.toUpperCase(), defaultStrategy);
    }
}
