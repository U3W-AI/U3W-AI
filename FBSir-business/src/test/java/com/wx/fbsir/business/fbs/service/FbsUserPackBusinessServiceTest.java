package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import com.wx.fbsir.business.fbs.dto.business.user_pack.UserPackPageRequest;
import com.wx.fbsir.business.fbs.dto.business.user_pack.UserPackStatsResponse;
import com.wx.fbsir.business.fbs.mapper.FbsUserPackMapper;
import com.wx.fbsir.business.fbs.service.business.impl.FbsUserPackBusinessServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FbsUserPackBusinessService 单元测试
 *
 * 覆盖 tasks.md §9.3 用户权益测试（共2个）
 *
 * @author FBSir
 * @date 2026-04-08
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户权益查询服务测试")
class FbsUserPackBusinessServiceTest {

    @Mock
    private FbsUserPackMapper userPackMapper;

    @InjectMocks
    private FbsUserPackBusinessServiceImpl service;

    // ---- 常量 ----
    private static final Long USER_ID = 1001L;

    // ---- Fixture ----
    private FbsUserPack buildUserPack(int status) {
        FbsUserPack up = new FbsUserPack();
        up.setId(1L);
        up.setUserId(USER_ID);
        up.setPackId(2001L);
        up.setStatus(status);
        up.setPackName("测试场景包");
        up.setDelFlag("0");
        return up;
    }

    // ========================================================================
    // §9.3 用户权益测试
    // ========================================================================

    @Nested
    @DisplayName("§9.3.1-9.3.2 用户权益查询")
    class UserPackQueryTests {

        @Test
        @DisplayName("§9.3.1 getUserPackPage — 分页")
        void getUserPackPage() {
            when(userPackMapper.selectUserPackList(any(FbsUserPack.class)))
                    .thenReturn(Arrays.asList(buildUserPack(1), buildUserPack(2)));

            var request = new UserPackPageRequest();
            request.setUserId(USER_ID);
            List<FbsUserPack> result = service.getUserPackPage(request);

            assertEquals(2, result.size());
            verify(userPackMapper).selectUserPackList(any(FbsUserPack.class));
        }

        @Test
        @DisplayName("§9.3.2 getUserPackStats — 统计正确")
        void getUserPackStats() {
            // totalCount=5, activeCount=3, expiredCount=1, revokedCount=1
            when(userPackMapper.countByUserIdAndStatus(USER_ID, null)).thenReturn(5L);
            when(userPackMapper.countByUserIdAndStatus(USER_ID, 1)).thenReturn(3L);
            when(userPackMapper.countByUserIdAndStatus(USER_ID, 2)).thenReturn(1L);
            when(userPackMapper.countByUserIdAndStatus(USER_ID, 3)).thenReturn(1L);

            UserPackStatsResponse result = service.getUserPackStats(USER_ID);

            assertEquals(USER_ID, result.getUserId());
            assertEquals(5L, result.getTotalCount());
            assertEquals(3L, result.getActiveCount());
            assertEquals(1L, result.getExpiredCount());
            assertEquals(1L, result.getRevokedCount());
        }
    }
}
