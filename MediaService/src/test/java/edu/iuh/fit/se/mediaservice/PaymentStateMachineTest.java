package edu.iuh.fit.se.mediaservice;

import edu.iuh.fit.se.mediaservice.statemachine.PaymentStateMachine;
import edu.iuh.fit.se.mediaservice.statemachine.PaymentStateMachine.PaymentState;
import edu.iuh.fit.se.mediaservice.statemachine.PaymentStateMachine.PaymentEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStateMachineTest {

    @Test
    void testValidTransitions() {
        // PENDING -> SUCCESS
        assertEquals(PaymentState.SUCCESS, PaymentStateMachine.transition(PaymentState.PENDING, PaymentEvent.PAY_SUCCESS));

        // PENDING -> CANCELLED
        assertEquals(PaymentState.CANCELLED, PaymentStateMachine.transition(PaymentState.PENDING, PaymentEvent.PAY_CANCEL));

        // PENDING -> FAILED
        assertEquals(PaymentState.FAILED, PaymentStateMachine.transition(PaymentState.PENDING, PaymentEvent.PAY_FAIL));

        // PENDING -> EXPIRED
        assertEquals(PaymentState.EXPIRED, PaymentStateMachine.transition(PaymentState.PENDING, PaymentEvent.TIMEOUT));
    }

    @Test
    void testInvalidTransitions() {
        // SUCCESS -> PAY_SUCCESS should throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            PaymentStateMachine.transition(PaymentState.SUCCESS, PaymentEvent.PAY_SUCCESS);
        });

        // FAILED -> TIMEOUT should throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            PaymentStateMachine.transition(PaymentState.FAILED, PaymentEvent.TIMEOUT);
        });
    }
}
