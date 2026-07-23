package com.wx.fbsir.business.fbs.service.impl;

import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the legacy skill-consume mutation inside one transaction without
 * holding that connection while the v2 writer opens its isolated transaction.
 */
@Service
public class SkillConsumeLegacyTransactionExecutor {

    @Transactional(rollbackFor = Exception.class)
    public ConsumeResult execute(Supplier<ConsumeResult> work) {
        return Objects.requireNonNull(work, "work").get();
    }
}
