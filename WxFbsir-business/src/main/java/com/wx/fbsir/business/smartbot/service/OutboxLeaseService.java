package com.wx.fbsir.business.smartbot.service;

import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import com.wx.fbsir.business.smartbot.mapper.DeliveryOutboxMapper;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Database-backed claim/fencing state for a replaceable outbox worker. */
@Service
public class OutboxLeaseService {

    private static final Pattern OWNER = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern ERROR_SECRET = Pattern.compile(
        "(?i)(authorization|token|secret|api[_-]?key|response_url)\\s*[:=]\\s*([^\\s&]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[^\\s]+");
    private static final long MIN_LEASE_SECONDS = 1;
    private static final long MAX_LEASE_SECONDS = 900;
    private static final int MAX_CLAIM_ATTEMPTS = 3;

    private final OutboxClaimTransactionService claimTransactionService;
    private final DeliveryOutboxMapper outboxMapper;

    public OutboxLeaseService(OutboxClaimTransactionService claimTransactionService,
                              DeliveryOutboxMapper outboxMapper) {
        this.claimTransactionService = claimTransactionService;
        this.outboxMapper = outboxMapper;
    }

    public Optional<DeliveryOutbox> claimNext(String leaseOwner, Duration leaseDuration) {
        return claimNextInternal(leaseOwner, leaseDuration);
    }

    public Optional<DeliveryOutbox> claimNext(String destinationType, String leaseOwner,
                                               Duration leaseDuration) {
        if (!"INTERNAL_DISPATCHER".equals(destinationType) && !"WEBHOOK_HUB".equals(destinationType)) {
            throw new IllegalArgumentException("unsupported outbox destination type");
        }
        validateOwner(leaseOwner);
        long seconds = validateDuration(leaseDuration);
        String token = UUID.randomUUID().toString();
        for (int attempt = 1; attempt <= MAX_CLAIM_ATTEMPTS; attempt++) {
            try {
                return claimTransactionService.claimOnce(destinationType, leaseOwner, token, seconds);
            } catch (TransientDataAccessException ex) {
                if (attempt == MAX_CLAIM_ATTEMPTS) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("outbox claim retry loop exhausted");
    }

    private Optional<DeliveryOutbox> claimNextInternal(String leaseOwner, Duration leaseDuration) {
        validateOwner(leaseOwner);
        long seconds = validateDuration(leaseDuration);
        String token = UUID.randomUUID().toString();
        for (int attempt = 1; attempt <= MAX_CLAIM_ATTEMPTS; attempt++) {
            try {
                return claimTransactionService.claimOnce(leaseOwner, token, seconds);
            } catch (TransientDataAccessException ex) {
                if (attempt == MAX_CLAIM_ATTEMPTS) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("outbox claim retry loop exhausted");
    }

    public void extendLease(Long id, String leaseToken, Duration leaseDuration) {
        validateLease(id, leaseToken);
        ensureLease(outboxMapper.extendLease(id, leaseToken, validateDuration(leaseDuration)));
    }

    public void markConsumed(Long id, String leaseToken) {
        validateLease(id, leaseToken);
        ensureLease(outboxMapper.markConsumed(id, leaseToken));
    }

    public void scheduleRetry(Long id, String leaseToken, Date nextAttemptAt, String lastError) {
        validateLease(id, leaseToken);
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("nextAttemptAt is required");
        }
        ensureLease(outboxMapper.scheduleRetry(id, leaseToken, nextAttemptAt, sanitizeError(lastError)));
    }

    public void markDead(Long id, String leaseToken, String lastError) {
        validateLease(id, leaseToken);
        ensureLease(outboxMapper.markDead(id, leaseToken, sanitizeError(lastError)));
    }

    private void validateOwner(String owner) {
        if (!StringUtils.hasText(owner) || !OWNER.matcher(owner).matches()) {
            throw new IllegalArgumentException("leaseOwner is invalid");
        }
    }

    private long validateDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("lease duration must be positive");
        }
        long seconds = duration.getSeconds();
        if (seconds < MIN_LEASE_SECONDS || seconds > MAX_LEASE_SECONDS) {
            throw new IllegalArgumentException("lease duration must be between 1 and 900 seconds");
        }
        return seconds;
    }

    private void validateLease(Long id, String token) {
        if (id == null || id <= 0 || !StringUtils.hasText(token) || token.length() != 36) {
            throw new IllegalArgumentException("outbox lease identity is invalid");
        }
    }

    private String sanitizeError(String error) {
        if (!StringUtils.hasText(error)) {
            return null;
        }
        String sanitized = error.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ").trim();
        sanitized = ERROR_SECRET.matcher(sanitized).replaceAll("$1=[REDACTED]");
        sanitized = BEARER.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
    }

    private void ensureLease(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("outbox lease lost or already completed");
        }
    }
}
