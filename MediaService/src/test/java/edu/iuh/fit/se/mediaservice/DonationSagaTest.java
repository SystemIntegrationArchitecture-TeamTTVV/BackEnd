package edu.iuh.fit.se.mediaservice;

import edu.iuh.fit.se.mediaservice.model.Gift;
import edu.iuh.fit.se.mediaservice.model.Transaction;
import edu.iuh.fit.se.mediaservice.model.Wallet;
import edu.iuh.fit.se.mediaservice.repository.TransactionRepository;
import edu.iuh.fit.se.mediaservice.repository.WalletEventRepository;
import edu.iuh.fit.se.mediaservice.repository.WalletRepository;
import edu.iuh.fit.se.mediaservice.saga.DonationSaga;
import edu.iuh.fit.se.mediaservice.saga.DonationSaga.DonateCommand;
import edu.iuh.fit.se.mediaservice.service.SocketEmitterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DonationSagaTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletEventRepository walletEventRepository;

    @Mock
    private SocketEmitterService socketEmitterService;

    @InjectMocks
    private DonationSaga donationSaga;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testDonateSuccess() {
        Gift gift = new Gift();
        gift.setId("gift123");
        gift.setName("Rose");
        gift.setPrice(100);

        DonateCommand cmd = DonateCommand.builder()
                .senderId("sender")
                .senderName("Alice")
                .receiverId("receiver")
                .receiverName("Bob")
                .gift(gift)
                .roomId("room")
                .giftMessage("Hello")
                .build();

        Wallet senderWallet = new Wallet();
        senderWallet.setUserId("sender");
        senderWallet.setBalance(500);

        Wallet receiverWallet = new Wallet();
        receiverWallet.setUserId("receiver");
        receiverWallet.setBalance(200);

        Transaction mockTx = new Transaction();
        mockTx.setId("tx123");

        when(walletRepository.findByUserId("sender")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserId("receiver")).thenReturn(Optional.of(receiverWallet));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(mockTx);

        var result = donationSaga.execute(cmd);

        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertEquals(400, senderWallet.getBalance()); // 500 - 100
        assertEquals(300, receiverWallet.getBalance()); // 200 + 100

        verify(walletRepository, times(2)).save(any(Wallet.class));
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(walletEventRepository, times(2)).save(any());
        verify(socketEmitterService, times(1)).emitToAll(any());
    }

    @Test
    void testCompensationOnStep3Failure() {
        Gift gift = new Gift();
        gift.setId("gift123");
        gift.setName("Rose");
        gift.setPrice(100);

        DonateCommand cmd = DonateCommand.builder()
                .senderId("sender")
                .senderName("Alice")
                .receiverId("receiver")
                .receiverName("Bob")
                .gift(gift)
                .roomId("room")
                .giftMessage("Hello")
                .build();

        Wallet senderWallet = new Wallet();
        senderWallet.setUserId("sender");
        senderWallet.setBalance(500);

        Wallet receiverWallet = new Wallet();
        receiverWallet.setUserId("receiver");
        receiverWallet.setBalance(200);

        when(walletRepository.findByUserId("sender")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserId("receiver")).thenReturn(Optional.of(receiverWallet));

        // Simulate database write failure on step 3 (sender transaction log)
        when(transactionRepository.save(any(Transaction.class))).thenThrow(new RuntimeException("DB Failure"));

        assertThrows(RuntimeException.class, () -> donationSaga.execute(cmd));

        // Balances should be compensated (Alice refunded, Bob debited back)
        assertEquals(500, senderWallet.getBalance()); // original balance restored
        assertEquals(200, receiverWallet.getBalance()); // original balance restored
    }
}
