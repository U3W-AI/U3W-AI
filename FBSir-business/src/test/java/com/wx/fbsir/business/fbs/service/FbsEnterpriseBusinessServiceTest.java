package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprise;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterpriseMember;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprisePack;
import com.wx.fbsir.business.fbs.dto.business.enterprise.*;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMemberMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterprisePackMapper;
import com.wx.fbsir.business.fbs.service.business.impl.FbsEnterpriseBusinessServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FbsEnterpriseBusinessService 单元测试
 *
 * @author FBSir
 * @date 2026-04-09
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("企业组织管理服务测试")
class FbsEnterpriseBusinessServiceTest {

    @Mock
    private FbsEnterpriseMapper enterpriseMapper;

    @Mock
    private FbsEnterpriseMemberMapper memberMapper;

    @Mock
    private FbsEnterprisePackMapper packMapper;

    @InjectMocks
    private FbsEnterpriseBusinessServiceImpl service;

    // ---- 常量 ----
    private static final Long ENT_ID = 1L;
    private static final String CREATED_BY = "admin";

    // ---- Fixture ----
    private FbsEnterprise buildEnterprise(int status) {
        FbsEnterprise ent = new FbsEnterprise();
        ent.setId(ENT_ID);
        ent.setEnterpriseCode("ENT_12345678");
        ent.setEnterpriseName("悟空科技");
        ent.setStatus(status);
        ent.setDelFlag("0");
        return ent;
    }

    // ========================================================================
    // §3.1 企业创建/查询测试
    // ========================================================================

    @Nested
    @DisplayName("§3.1 企业创建/查询")
    class EnterpriseCrudTests {

        @Test
        @DisplayName("§3.1.1 createEnterprise — 创建成功")
        void createSuccess() {
            when(enterpriseMapper.selectByName("悟空科技")).thenReturn(null);
            doAnswer(invocation -> {
                FbsEnterprise e = invocation.getArgument(0);
                e.setId(ENT_ID);
                return 1;
            }).when(enterpriseMapper).insertEnterprise(any(FbsEnterprise.class));

            var request = new EnterpriseCreateRequest();
            request.setEnterpriseName("悟空科技");
            request.setContactName("张三");
            request.setContactPhone("13800138000");

            Long id = service.createEnterprise(request, CREATED_BY);

            assertNotNull(id);
            ArgumentCaptor<FbsEnterprise> captor = ArgumentCaptor.forClass(FbsEnterprise.class);
            verify(enterpriseMapper).insertEnterprise(captor.capture());
            assertEquals("悟空科技", captor.getValue().getEnterpriseName());
            assertEquals(1, captor.getValue().getStatus());
            assertTrue(captor.getValue().getEnterpriseCode().startsWith("ENT_"));
        }

        @Test
        @DisplayName("§3.1.2 createEnterprise — 企业名称重复，抛出异常")
        void createDuplicateName() {
            when(enterpriseMapper.selectByName("悟空科技")).thenReturn(buildEnterprise(1));

            var request = new EnterpriseCreateRequest();
            request.setEnterpriseName("悟空科技");

            assertThrows(IllegalArgumentException.class,
                () -> service.createEnterprise(request, CREATED_BY));
        }

        @Test
        @DisplayName("§3.1.3 getEnterpriseDetail — 查询成功")
        void getDetailSuccess() {
            FbsEnterprise ent = buildEnterprise(1);
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(ent);
            when(memberMapper.selectActiveByEnterpriseId(ENT_ID)).thenReturn(
                Arrays.asList(new FbsEnterpriseMember(), new FbsEnterpriseMember()));
            when(packMapper.selectByEnterpriseId(ENT_ID)).thenReturn(
                Arrays.asList(buildPack(1), buildPack(1), buildPack(3)));

            EnterpriseDetailResponse detail = service.getEnterpriseDetail(ENT_ID);

            assertNotNull(detail);
            assertEquals(ENT_ID, detail.getId());
            assertEquals("正常", detail.getStatusDesc());
            assertEquals(2, detail.getMemberCount());
            assertEquals(2, detail.getPackCount()); // 只统计 status=1
        }

        @Test
        @DisplayName("§3.1.4 getEnterpriseDetail — 企业不存在，返回null")
        void getDetailNotFound() {
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(null);

            assertNull(service.getEnterpriseDetail(ENT_ID));
        }

        @Test
        @DisplayName("§3.1.5 getEnterprisePage — 分页筛选")
        void getEnterprisePage() {
            FbsEnterprise ent = buildEnterprise(1);
            when(enterpriseMapper.selectEnterpriseList(any(FbsEnterprise.class)))
                .thenReturn(Arrays.asList(ent));

            var request = new EnterprisePageRequest();
            request.setStatus(1);
            List<FbsEnterprise> result = service.getEnterprisePage(request);

            assertEquals(1, result.size());
            verify(enterpriseMapper).selectEnterpriseList(any(FbsEnterprise.class));
        }
    }

    // ========================================================================
    // §3.2 企业禁用测试
    // ========================================================================

    @Nested
    @DisplayName("§3.2 企业禁用")
    class EnterpriseDisableTests {

        @Test
        @DisplayName("§3.2.1 disableEnterprise — 正常企业禁用成功")
        void disableSuccess() {
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(1));
            when(enterpriseMapper.updateEnterprise(any(FbsEnterprise.class))).thenReturn(1);

            boolean result = service.disableEnterprise(ENT_ID, CREATED_BY);

            assertTrue(result);
            ArgumentCaptor<FbsEnterprise> captor = ArgumentCaptor.forClass(FbsEnterprise.class);
            verify(enterpriseMapper).updateEnterprise(captor.capture());
            assertEquals(2, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("§3.2.2 disableEnterprise — 已禁用企业禁用失败（Fail-Closed）")
        void disableAlreadyDisabled() {
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(2));

            boolean result = service.disableEnterprise(ENT_ID, CREATED_BY);

            assertFalse(result);
            verify(enterpriseMapper, never()).updateEnterprise(any());
        }

        @Test
        @DisplayName("§3.2.3 disableEnterprise — 企业不存在，禁用失败")
        void disableNotFound() {
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(null);

            boolean result = service.disableEnterprise(ENT_ID, CREATED_BY);

            assertFalse(result);
        }

        // ---- 新增 §E1 ----
        @Test
        @DisplayName("§E1.2 disableEnterprise — status=3（已删除）重复禁用返回 false")
        void disableAlreadyDeleted() {
            // 实现：Fail-Closed 条件为 status != 1，status=3 同样被拒绝
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(3));

            boolean result = service.disableEnterprise(ENT_ID, CREATED_BY);

            assertFalse(result);
            verify(enterpriseMapper, never()).updateEnterprise(any());
        }
    }

    // ---- 新增 §E1 ----
    @Nested
    @DisplayName("§E1.5-E1.6 getEnterprisePage 筛选透传")
    class EnterprisePageFilterTests {

        @Test
        @DisplayName("§E1.5 getEnterprisePage — enterpriseName + status 双重筛选透传正确")
        void getEnterprisePageWithNameFilter() {
            FbsEnterprise ent = buildEnterprise(1);
            when(enterpriseMapper.selectEnterpriseList(any(FbsEnterprise.class)))
                .thenReturn(Arrays.asList(ent));

            var request = new EnterprisePageRequest();
            request.setEnterpriseName("悟空科技");
            request.setStatus(1);

            List<FbsEnterprise> result = service.getEnterprisePage(request);

            assertEquals(1, result.size());
            ArgumentCaptor<FbsEnterprise> captor = ArgumentCaptor.forClass(FbsEnterprise.class);
            verify(enterpriseMapper).selectEnterpriseList(captor.capture());
            assertEquals("悟空科技", captor.getValue().getEnterpriseName());
            assertEquals(1, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("§E1.6 getEnterprisePage — 仅 enterpriseName（无 status）")
        void getEnterprisePageNameOnly() {
            when(enterpriseMapper.selectEnterpriseList(any(FbsEnterprise.class)))
                .thenReturn(Collections.emptyList());

            var request = new EnterprisePageRequest();
            request.setEnterpriseName("不存在的企业");

            List<FbsEnterprise> result = service.getEnterprisePage(request);

            assertTrue(result.isEmpty());
            ArgumentCaptor<FbsEnterprise> captor = ArgumentCaptor.forClass(FbsEnterprise.class);
            verify(enterpriseMapper).selectEnterpriseList(captor.capture());
            assertEquals("不存在的企业", captor.getValue().getEnterpriseName());
            assertNull(captor.getValue().getStatus()); // status 不过滤
        }
    }

    // ---- 辅助 ----
    private FbsEnterprisePack buildPack(int status) {
        FbsEnterprisePack pack = new FbsEnterprisePack();
        pack.setId(1L);
        pack.setEnterpriseId(ENT_ID);
        pack.setStatus(status);
        return pack;
    }
}
