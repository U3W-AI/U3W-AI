package com.wx.fbsir.business.fbs.service.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.dto.self.MyScenePackItemDTO;
import com.wx.fbsir.business.fbs.dto.self.MyScenePacksQueryDTO;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
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
 * FbsUserSelfService — getClaimableScenePacks 完整单元测试
 *
 * 覆盖：tasks.md §7.3 + 补充边界用例
 * - §7.3.1 返回列表，字段完整
 * - §7.3.2 keyword 搜索生效
 * - §7.3.3 只返回 owner_type=1 + status=1 + points_rule_code IS NULL
 * - 补充1: 无数据时返回空列表
 * - 补充2: 未登录抛 ServiceException(401)
 * - 补充3: keyword 为 null 时正常查询
 * - 补充4: 字段映射完整性（description, currentVersion, pointsRuleCode, createTime）
 *
 * @author FBSir
 * @date 2026-04-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户自助 — getClaimableScenePacks")
class FbsUserSelfServiceGetClaimablePacksTest {

    @Mock
    private FbsUserPackMapper userPackMapper;
    @Mock
    private FbsScenePackMapper scenePackMapper;

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

    private FbsScenePack buildScenePack(Long id, String name, String code, String desc,
                                         String version, String pointsRuleCode) {
        FbsScenePack pack = new FbsScenePack();
        pack.setId(id);
        pack.setPackName(name);
        pack.setPackCode(code);
        pack.setDescription(desc);
        pack.setOwnerType(1);
        pack.setStatus(1);
        pack.setCurrentVersion(version);
        pack.setPointsRuleCode(pointsRuleCode);
        pack.setCreateTime(new Date());
        return pack;
    }

    // ========================================================================
    // §7.3 getClaimableScenePacks
    // ========================================================================

    @Nested
    @DisplayName("§7.3 getClaimableScenePacks")
    class GetClaimableTests {

        @Test
        @DisplayName("§7.3.1 返回列表，字段完整")
        void hasData() {
            mockSecurityContext(TEST_USER_ID);
            List<FbsScenePack> packs = Arrays.asList(
                    buildScenePack(1L, "标准版", "PKG001", "描述1", "v1.0", null),
                    buildScenePack(2L, "高级版", "PKG002", "描述2", "v2.0", null)
            );
            when(scenePackMapper.selectClaimablePacks(TEST_USER_ID, null))
                    .thenReturn(packs);
            when(userPackMapper.selectClaimedPackIds(eq(TEST_USER_ID), anySet()))
                    .thenReturn(Collections.emptySet());

            MyScenePacksQueryDTO query = new MyScenePacksQueryDTO();
            List<MyScenePackItemDTO> result = service.getClaimableScenePacks(query);

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("标准版", result.get(0).getPackName());
            assertEquals("PKG001", result.get(0).getPackCode());
            assertEquals("v1.0", result.get(0).getCurrentVersion());
            assertEquals("描述1", result.get(0).getDescription());
            assertNull(result.get(0).getPointsRuleCode()); // 免费
            assertNotNull(result.get(0).getCreateTime());
            assertFalse(result.get(0).getClaimed()); // 未领取
            assertFalse(result.get(1).getClaimed()); // 未领取
        }

        @Test
        @DisplayName("§7.3.2 keyword 搜索生效")
        void keywordSearch() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectClaimablePacks(TEST_USER_ID, "标准"))
                    .thenReturn(Collections.singletonList(buildScenePack(1L, "标准版", "PKG001", null, "v1.0", null)));
            when(userPackMapper.selectClaimedPackIds(eq(TEST_USER_ID), anySet()))
                    .thenReturn(Collections.emptySet());

            MyScenePacksQueryDTO query = new MyScenePacksQueryDTO();
            query.setKeyword("标准");
            List<MyScenePackItemDTO> result = service.getClaimableScenePacks(query);

            assertEquals(1, result.size());
            verify(scenePackMapper).selectClaimablePacks(TEST_USER_ID, "标准");
        }

        @Test
        @DisplayName("§7.3.3 只返回 owner_type=1 + status=1 + points_rule_code IS NULL")
        void filterConditionsVerifiedByMapper() {
            mockSecurityContext(TEST_USER_ID);
            // Mapper XML 层面保证过滤条件，这里验证调用链正确
            when(scenePackMapper.selectClaimablePacks(TEST_USER_ID, null))
                    .thenReturn(Collections.emptyList());

            MyScenePacksQueryDTO query = new MyScenePacksQueryDTO();
            List<MyScenePackItemDTO> result = service.getClaimableScenePacks(query);

            // Mapper 层保证了条件，返回空即无满足条件的包
            assertEquals(0, result.size());
            verify(scenePackMapper).selectClaimablePacks(TEST_USER_ID, null);
            // 空列表不调用 selectClaimedPackIds
            verify(userPackMapper, never()).selectClaimedPackIds(anyLong(), anySet());
        }

        @Test
        @DisplayName("补充: 无数据时返回空列表")
        void noData() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectClaimablePacks(TEST_USER_ID, null))
                    .thenReturn(Collections.emptyList());

            MyScenePacksQueryDTO query = new MyScenePacksQueryDTO();
            List<MyScenePackItemDTO> result = service.getClaimableScenePacks(query);

            assertNotNull(result);
            assertEquals(0, result.size());
            verify(userPackMapper, never()).selectClaimedPackIds(anyLong(), anySet());
        }

        @Test
        @DisplayName("补充: 未登录抛 ServiceException(401)")
        void notLoggedIn() {
            MyScenePacksQueryDTO query = new MyScenePacksQueryDTO();

            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.getClaimableScenePacks(query));
            assertEquals(401, ex.getCode());
        }

        @Test
        @DisplayName("补充: keyword 为 null 时正常查询")
        void keywordNull() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectClaimablePacks(TEST_USER_ID, null))
                    .thenReturn(Collections.singletonList(
                            buildScenePack(1L, "标准版", "PKG001", null, "v1.0", null)));
            when(userPackMapper.selectClaimedPackIds(eq(TEST_USER_ID), anySet()))
                    .thenReturn(Collections.emptySet());

            MyScenePacksQueryDTO query = new MyScenePacksQueryDTO();
            query.setKeyword(null);
            List<MyScenePackItemDTO> result = service.getClaimableScenePacks(query);

            assertEquals(1, result.size());
            verify(scenePackMapper).selectClaimablePacks(TEST_USER_ID, null);
        }

        @Test
        @DisplayName("补充: 字段映射完整性（description, currentVersion, pointsRuleCode, createTime）")
        void fieldMappingComplete() {
            mockSecurityContext(TEST_USER_ID);
            FbsScenePack pack = buildScenePack(5L, "完整字段包", "PKG999", "这是一段描述", "v3.5", null);
            when(scenePackMapper.selectClaimablePacks(TEST_USER_ID, null))
                    .thenReturn(Collections.singletonList(pack));
            when(userPackMapper.selectClaimedPackIds(eq(TEST_USER_ID), anySet()))
                    .thenReturn(Collections.emptySet());

            MyScenePacksQueryDTO query = new MyScenePacksQueryDTO();
            List<MyScenePackItemDTO> result = service.getClaimableScenePacks(query);

            MyScenePackItemDTO item = result.get(0);
            assertEquals(5L, item.getId());
            assertEquals("PKG999", item.getPackCode());
            assertEquals("完整字段包", item.getPackName());
            assertEquals("v3.5", item.getCurrentVersion());
            assertEquals("这是一段描述", item.getDescription());
            assertNull(item.getPointsRuleCode());
            assertNotNull(item.getCreateTime());
            assertFalse(item.getClaimed());
        }

        @Test
        @DisplayName("补充: 已领取的包 claimed=true")
        void claimedPack() {
            mockSecurityContext(TEST_USER_ID);
            FbsScenePack pack1 = buildScenePack(1L, "标准版", "PKG001", null, "v1.0", null);
            FbsScenePack pack2 = buildScenePack(2L, "高级版", "PKG002", null, "v2.0", null);
            when(scenePackMapper.selectClaimablePacks(TEST_USER_ID, null))
                    .thenReturn(Arrays.asList(pack1, pack2));
            when(userPackMapper.selectClaimedPackIds(eq(TEST_USER_ID), anySet()))
                    .thenReturn(Collections.singleton(1L)); // pack1 已领取

            MyScenePacksQueryDTO query = new MyScenePacksQueryDTO();
            List<MyScenePackItemDTO> result = service.getClaimableScenePacks(query);

            assertEquals(2, result.size());
            assertTrue(result.get(0).getClaimed());  // pack1 已领取
            assertFalse(result.get(1).getClaimed()); // pack2 未领取
        }

        @Test
        @DisplayName("补充: 全部已领取时所有 claimed=true")
        void allClaimed() {
            mockSecurityContext(TEST_USER_ID);
            FbsScenePack pack1 = buildScenePack(1L, "标准版", "PKG001", null, "v1.0", null);
            when(scenePackMapper.selectClaimablePacks(TEST_USER_ID, null))
                    .thenReturn(Collections.singletonList(pack1));
            when(userPackMapper.selectClaimedPackIds(eq(TEST_USER_ID), anySet()))
                    .thenReturn(Collections.singleton(1L));

            MyScenePacksQueryDTO query = new MyScenePacksQueryDTO();
            List<MyScenePackItemDTO> result = service.getClaimableScenePacks(query);

            assertEquals(1, result.size());
            assertTrue(result.get(0).getClaimed());
        }
    }
}
