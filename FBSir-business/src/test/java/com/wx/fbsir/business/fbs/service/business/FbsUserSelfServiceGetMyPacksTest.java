package com.wx.fbsir.business.fbs.service.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import com.wx.fbsir.business.fbs.dto.self.MyPackItemDTO;
import com.wx.fbsir.business.fbs.dto.self.MyPacksQueryDTO;
import com.wx.fbsir.business.fbs.mapper.FbsUserPackMapper;
import com.wx.fbsir.business.fbs.service.business.impl.FbsUserSelfServiceBusinessServiceImpl;
import com.wx.fbsir.common.core.domain.model.LoginUser;
import com.wx.fbsir.common.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FbsUserSelfService — getMyPacks 完整单元测试
 *
 * 覆盖：tasks.md §7.1 + 补充边界用例
 * - §7.1.1 有数据时返回列表，字段完整
 * - §7.1.2 无数据时返回空列表
 * - §7.1.3 status 过滤生效
 * - §7.1.4 sourceType 过滤生效
 * - §7.1.5 未登录抛 ServiceException(401, "SESSION_REQUIRED")
 * - 补充1: packId 过滤生效
 * - 补充2: statusDesc 全枚举覆盖（2=已过期, 3=已撤销）
 * - 补充3: sourceTypeDesc 全枚举覆盖（2=企业分发）
 *
 * @author FBSir
 * @date 2026-04-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户自助 — getMyPacks")
class FbsUserSelfServiceGetMyPacksTest {

    @Mock
    private FbsUserPackMapper userPackMapper;

    @InjectMocks
    private FbsUserSelfServiceBusinessServiceImpl service;

    private static final Long TEST_USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(loginUser);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);
    }

    private FbsUserPack buildPack(Long id, Long packId, String packName, String packCode,
                                   Integer status, Integer sourceType) {
        return buildPack(id, packId, packName, packCode, status, sourceType, 1);
    }

    private FbsUserPack buildPack(Long id, Long packId, String packName, String packCode,
                                   Integer status, Integer sourceType, Integer packStatus) {
        FbsUserPack pack = new FbsUserPack();
        pack.setId(id);
        pack.setUserId(TEST_USER_ID);
        pack.setPackId(packId);
        pack.setPackName(packName);
        pack.setPackCode(packCode);
        pack.setStatus(status);
        pack.setSourceType(sourceType);
        pack.setPackStatus(packStatus);
        pack.setActivatedAt(new Date());
        pack.setCreateTime(new Date());
        return pack;
    }

    // ========================================================================
    // getMyPacks
    // ========================================================================

    @Nested
    @DisplayName("§7.1 getMyPacks")
    class GetMyPacksTests {

        @Test
        @DisplayName("§7.1.1 有数据时返回列表，字段完整")
        void hasData_fieldsComplete() {
            mockSecurityContext(TEST_USER_ID);
            List<FbsUserPack> packs = Arrays.asList(
                    buildPack(1L, 10L, "标准版", "PKG001", 1, 1),
                    buildPack(2L, 11L, "高级版", "PKG002", 1, 2)
            );
            when(userPackMapper.selectMyPacks(any(FbsUserPack.class))).thenReturn(packs);

            MyPacksQueryDTO query = new MyPacksQueryDTO();
            List<MyPackItemDTO> result = service.getMyPacks(query);

            assertNotNull(result);
            assertEquals(2, result.size());

            MyPackItemDTO item1 = result.get(0);
            assertEquals(1L, item1.getId());
            assertEquals(10L, item1.getPackId());
            assertEquals("标准版", item1.getPackName());
            assertEquals("PKG001", item1.getPackCode());
            assertEquals("平台分发", item1.getSourceTypeDesc());
            assertEquals("有效", item1.getStatusDesc());
            assertNotNull(item1.getActivatedAt());
            assertNotNull(item1.getCreateTime());

            MyPackItemDTO item2 = result.get(1);
            assertEquals(11L, item2.getPackId());
            assertEquals("高级版", item2.getPackName());
            assertEquals("企业分发", item2.getSourceTypeDesc());
        }

        @Test
        @DisplayName("§7.1.2 无数据时返回空列表")
        void noData() {
            mockSecurityContext(TEST_USER_ID);
            when(userPackMapper.selectMyPacks(any(FbsUserPack.class))).thenReturn(Collections.emptyList());

            MyPacksQueryDTO query = new MyPacksQueryDTO();
            List<MyPackItemDTO> result = service.getMyPacks(query);

            assertNotNull(result);
            assertEquals(0, result.size());
        }

        @Test
        @DisplayName("§7.1.3 status 过滤生效")
        void statusFilter() {
            mockSecurityContext(TEST_USER_ID);
            when(userPackMapper.selectMyPacks(any(FbsUserPack.class)))
                    .thenReturn(Collections.singletonList(buildPack(1L, 10L, "标准版", "PKG001", 1, 1)));

            MyPacksQueryDTO query = new MyPacksQueryDTO();
            query.setStatus(1);
            List<MyPackItemDTO> result = service.getMyPacks(query);

            assertEquals(1, result.size());
            assertEquals("有效", result.get(0).getStatusDesc());
            verify(userPackMapper).selectMyPacks(argThat(filter -> filter.getStatus() == 1));
        }

        @Test
        @DisplayName("§7.1.4 sourceType 过滤生效")
        void sourceTypeFilter() {
            mockSecurityContext(TEST_USER_ID);
            when(userPackMapper.selectMyPacks(any(FbsUserPack.class)))
                    .thenReturn(Collections.singletonList(buildPack(1L, 10L, "标准版", "PKG001", 1, 3)));

            MyPacksQueryDTO query = new MyPacksQueryDTO();
            query.setSourceType(3);
            List<MyPackItemDTO> result = service.getMyPacks(query);

            assertEquals(1, result.size());
            assertEquals("用户激活", result.get(0).getSourceTypeDesc());
            verify(userPackMapper).selectMyPacks(argThat(filter -> filter.getSourceType() == 3));
        }

        @Test
        @DisplayName("§7.1.5 未登录抛 ServiceException(401, 'SESSION_REQUIRED')")
        void notLoggedIn() {
            // SecurityUtils.getUserId() 会抛 ServiceException("获取用户ID异常", 401)
            // BusinessService 中 if (userId == null) 兜底也会抛 401
            MyPacksQueryDTO query = new MyPacksQueryDTO();

            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.getMyPacks(query));
            // 两种可能：SecurityUtils 抛 401，或 BusinessService 抛 401
            assertEquals(401, ex.getCode());
        }

        @Test
        @DisplayName("补充: packId 过滤生效")
        void packIdFilter() {
            mockSecurityContext(TEST_USER_ID);
            when(userPackMapper.selectMyPacks(any(FbsUserPack.class)))
                    .thenReturn(Collections.singletonList(buildPack(1L, 10L, "标准版", "PKG001", 1, 1)));

            MyPacksQueryDTO query = new MyPacksQueryDTO();
            query.setPackId(10L);
            List<MyPackItemDTO> result = service.getMyPacks(query);

            assertEquals(1, result.size());
            assertEquals(10L, result.get(0).getPackId());
            verify(userPackMapper).selectMyPacks(argThat(filter -> filter.getPackId().equals(10L)));
        }

        @Test
        @DisplayName("补充: statusDesc 全枚举覆盖（2=已过期, 3=已撤销, 场景包已下架）")
        void statusDesc_allStatuses() {
            mockSecurityContext(TEST_USER_ID);
            List<FbsUserPack> packs = Arrays.asList(
                    buildPack(1L, 10L, "包A", "PKG001", 2, 1),  // 已过期
                    buildPack(2L, 11L, "包B", "PKG002", 3, 1),   // 已撤销
                    buildPack(3L, 12L, "包C", "PKG003", 1, 1, 2) // 场景包已下架（status=1 但 packStatus=2）
            );
            when(userPackMapper.selectMyPacks(any(FbsUserPack.class))).thenReturn(packs);

            MyPacksQueryDTO query = new MyPacksQueryDTO();
            List<MyPackItemDTO> result = service.getMyPacks(query);

            assertEquals("已过期", result.get(0).getStatusDesc());
            assertEquals("已撤销", result.get(1).getStatusDesc());
            assertEquals("场景包已下架", result.get(2).getStatusDesc());
        }

        @Test
        @DisplayName("补充: 场景包已删除时 statusDesc 显示\"场景包已删除\"")
        void packDeleted_showsDeletedDesc() {
            mockSecurityContext(TEST_USER_ID);
            FbsUserPack pack = buildPack(1L, 10L, "包A", "PKG001", 1, 1);
            pack.setPackStatus(null); // LEFT JOIN 场景包不存在 → packStatus=null
            when(userPackMapper.selectMyPacks(any(FbsUserPack.class)))
                    .thenReturn(Collections.singletonList(pack));

            MyPacksQueryDTO query = new MyPacksQueryDTO();
            List<MyPackItemDTO> result = service.getMyPacks(query);

            assertEquals("场景包已删除", result.get(0).getStatusDesc());
        }

        @Test
        @DisplayName("补充: 场景包已下架但权益已过期时，显示\"已过期\"而非\"场景包已下架\"")
        void offlinePack_expiredRight_showsExpiredDesc() {
            mockSecurityContext(TEST_USER_ID);
            FbsUserPack pack = buildPack(1L, 10L, "包A", "PKG001", 2, 1, 2); // status=2(已过期), packStatus=2(已下架)
            when(userPackMapper.selectMyPacks(any(FbsUserPack.class)))
                    .thenReturn(Collections.singletonList(pack));

            MyPacksQueryDTO query = new MyPacksQueryDTO();
            List<MyPackItemDTO> result = service.getMyPacks(query);

            assertEquals("已过期", result.get(0).getStatusDesc());
        }

        @Test
        @DisplayName("补充: sourceTypeDesc 全枚举覆盖（2=企业分发）+ 未知类型")
        void sourceTypeDesc_allTypes() {
            mockSecurityContext(TEST_USER_ID);
            FbsUserPack unknownPack = buildPack(1L, 10L, "包A", "PKG001", 1, 99); // 未知类型
            when(userPackMapper.selectMyPacks(any(FbsUserPack.class)))
                    .thenReturn(Collections.singletonList(unknownPack));

            MyPacksQueryDTO query = new MyPacksQueryDTO();
            List<MyPackItemDTO> result = service.getMyPacks(query);

            assertEquals("未知", result.get(0).getSourceTypeDesc());
        }
    }
}
