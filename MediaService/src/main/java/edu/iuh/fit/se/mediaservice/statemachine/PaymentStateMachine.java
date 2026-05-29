package edu.iuh.fit.se.mediaservice.statemachine;

import java.util.Map;
import java.util.Set;

public class PaymentStateMachine {

    public enum PaymentState {
        PENDING, SUCCESS, FAILED, CANCELLED, EXPIRED
    }

    public enum PaymentEvent {
        PAY_SUCCESS, PAY_FAIL, PAY_CANCEL, TIMEOUT
    }

    private static final Map<PaymentState, Set<PaymentEvent>> VALID_TRANSITIONS = Map.of(
            PaymentState.PENDING, Set.of(PaymentEvent.PAY_SUCCESS, PaymentEvent.PAY_FAIL,
                                         PaymentEvent.PAY_CANCEL, PaymentEvent.TIMEOUT)
    );

    /**
     * Transitions from current state using the given event.
     * Throws IllegalStateException if the transition is invalid.
     */
    public static PaymentState transition(PaymentState current, PaymentEvent event) {
        if (current == null) {
            throw new IllegalArgumentException("Current state cannot be null");
        }
        if (event == null) {
            throw new IllegalArgumentException("Payment event cannot be null");
        }

        Set<PaymentEvent> allowedEvents = VALID_TRANSITIONS.get(current);
        if (allowedEvents == null || !allowedEvents.contains(event)) {
            throw new IllegalStateException("Invalid state transition: Cannot apply event " + event + " to state " + current);
        }

        return switch (event) {
            case PAY_SUCCESS -> PaymentState.SUCCESS;
            case PAY_FAIL -> PaymentState.FAILED;
            case PAY_CANCEL -> PaymentState.CANCELLED;
            case TIMEOUT -> PaymentState.EXPIRED;
        };
    }
}
