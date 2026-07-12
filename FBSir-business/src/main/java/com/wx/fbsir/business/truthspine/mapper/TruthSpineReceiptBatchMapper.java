package com.wx.fbsir.business.truthspine.mapper;

import com.wx.fbsir.business.truthspine.domain.TruthSpineReceiptBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TruthSpineReceiptBatchMapper {
    TruthSpineReceiptBatch selectByWorkloadAndIdempotencyKeyForUpdate(@Param("workloadId") String workloadId,
                                                                       @Param("idempotencyKey") String idempotencyKey);

    /**
     * Inserts once by the immutable workload-scoped idempotency key. A duplicate is deliberately
     * a no-op so the service can re-read and compare the evidence identity instead of accepting a
     * key collision across retries.
     */
    int insertIfAbsent(TruthSpineReceiptBatch batch);
}
