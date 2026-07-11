package com.wx.fbsir.business.smartbot.mapper;

import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

@Mapper
public interface DeliveryOutboxMapper {
    int insertOutbox(DeliveryOutbox outbox);

    DeliveryOutbox selectByEventKeyForUpdate(@Param("eventKey") String eventKey);

    Long selectClaimCandidateForUpdate(@Param("destinationType") String destinationType);

    int claimById(@Param("id") Long id,
                  @Param("destinationType") String destinationType,
                  @Param("leaseOwner") String leaseOwner,
                  @Param("leaseToken") String leaseToken,
                  @Param("leaseSeconds") long leaseSeconds);

    DeliveryOutbox selectByLeaseToken(@Param("leaseToken") String leaseToken);

    DeliveryOutbox selectActiveLeaseForUpdate(@Param("id") Long id,
                                              @Param("leaseToken") String leaseToken);

    DeliveryOutbox selectActiveLeaseForUpdateByDestination(@Param("id") Long id,
                                                           @Param("leaseToken") String leaseToken,
                                                           @Param("destinationType") String destinationType);

    int extendLease(@Param("id") Long id,
                    @Param("leaseToken") String leaseToken,
                    @Param("leaseSeconds") long leaseSeconds);

    int markConsumed(@Param("id") Long id,
                     @Param("leaseToken") String leaseToken);

    int scheduleRetry(@Param("id") Long id,
                      @Param("leaseToken") String leaseToken,
                      @Param("nextAttemptAt") Date nextAttemptAt,
                      @Param("lastError") String lastError);

    int markDead(@Param("id") Long id,
                 @Param("leaseToken") String leaseToken,
                 @Param("lastError") String lastError);
}
