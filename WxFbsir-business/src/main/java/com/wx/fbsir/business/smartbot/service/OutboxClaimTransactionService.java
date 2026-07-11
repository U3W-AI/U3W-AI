package com.wx.fbsir.business.smartbot.service;

import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import com.wx.fbsir.business.smartbot.mapper.DeliveryOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** One short, independently committed queue-claim transaction. */
@Service
public class OutboxClaimTransactionService {

    private final DeliveryOutboxMapper outboxMapper;

    public OutboxClaimTransactionService(DeliveryOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Optional<DeliveryOutbox> claimOnce(String leaseOwner, String leaseToken,
                                               long leaseSeconds) {
        return claimOnce("INTERNAL_DISPATCHER", leaseOwner, leaseToken, leaseSeconds);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Optional<DeliveryOutbox> claimOnce(String destinationType, String leaseOwner, String leaseToken,
                                               long leaseSeconds) {
        Long id = outboxMapper.selectClaimCandidateForUpdate(destinationType);
        if (id == null) {
            return Optional.empty();
        }
        int claimed = outboxMapper.claimById(
            id, destinationType, leaseOwner, leaseToken, leaseSeconds);
        if (claimed != 1) {
            throw new IllegalStateException("outbox claim affected an unexpected number of rows");
        }
        DeliveryOutbox outbox = outboxMapper.selectByLeaseToken(leaseToken);
        if (outbox == null || outbox.getId() == null || !leaseToken.equals(outbox.getLeaseToken())) {
            throw new IllegalStateException("claimed outbox row cannot be read by ownership token");
        }
        return Optional.of(outbox);
    }
}
