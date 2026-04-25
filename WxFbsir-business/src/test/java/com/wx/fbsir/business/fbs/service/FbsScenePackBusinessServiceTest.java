package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.dto.business.scene_pack.ScenePackCreateRequest;
import com.wx.fbsir.business.fbs.dto.business.scene_pack.ScenePackUpdateRequest;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.service.business.impl.FbsScenePackBusinessServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FbsScenePackBusinessService 单元测试
 *
 * 覆盖 tasks.md §9.1 场景包运营测试（共4个）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("场景包运营服务测试")
class FbsScenePackBusinessServiceTest {

    @Mock
    private FbsScenePackMapper scenePackMapper;

    @InjectMocks
    private FbsScenePackBusinessServiceImpl service;

    // ---- 常量 ----
    private static final Long PACK_ID = 2001L;
    private static final Long USER_ID = 1001L;
    private static final String CREATED_BY = "admin";

    // ---- Fixture ----
    private FbsScenePack buildPack(int status) {
        FbsScenePack pack = new FbsScenePack();
        pack.setId(PACK_ID);
        pack.setPackCode("PACK_TEST_001");
        pack.setPackName("测试场景包");
        pack.setStatus(status);
        pack.setCurrentVersion("1.0.0");
        pack.setDelFlag("0");
        return pack;
    }

    // ========================================================================
    // §9.1 场景包运营测试
    // ========================================================================

    @Nested
    @DisplayName("§9.1.1-9.1.4 场景包运营")
    class ScenePackOperationTests {

        @Test
        @DisplayName("§9.1.1 publishScenePack — 草稿发布成功")
        void publishDraftSuccess() {
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildPack(0));
            when(scenePackMapper.updateScenePack(any(FbsScenePack.class))).thenReturn(1);

            boolean result = service.publishScenePack(PACK_ID, CREATED_BY);

            assertTrue(result);
            ArgumentCaptor<FbsScenePack> captor = ArgumentCaptor.forClass(FbsScenePack.class);
            verify(scenePackMapper).updateScenePack(captor.capture());
            assertEquals(1, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("§9.1.2 publishScenePack — 非草稿状态发布失败（Fail-Closed）")
        void publishNonDraftFail() {
            // 已发布状态不可再发布
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildPack(1));
            // updateScenePack 不应被调用
            boolean result = service.publishScenePack(PACK_ID, CREATED_BY);

            assertFalse(result);
            verify(scenePackMapper, never()).updateScenePack(any());
        }

        @Test
        @DisplayName("§9.1.3 unpublishScenePack — 已发布下架成功")
        void unpublishPublishedSuccess() {
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildPack(1));
            when(scenePackMapper.updateScenePack(any(FbsScenePack.class))).thenReturn(1);

            boolean result = service.unpublishScenePack(PACK_ID, CREATED_BY);

            assertTrue(result);
            ArgumentCaptor<FbsScenePack> captor = ArgumentCaptor.forClass(FbsScenePack.class);
            verify(scenePackMapper).updateScenePack(captor.capture());
            assertEquals(2, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("§9.1.4 getScenePackPage — 分页筛选")
        void getScenePackPage() {
            FbsScenePack pack1 = buildPack(1);
            FbsScenePack pack2 = buildPack(0);
            when(scenePackMapper.selectScenePackList(any(FbsScenePack.class)))
                    .thenReturn(Arrays.asList(pack1, pack2));

            var request = new com.wx.fbsir.business.fbs.dto.business.scene_pack.ScenePackPageRequest();
            request.setStatus(1);
            List<FbsScenePack> result = service.getScenePackPage(request);

            assertEquals(2, result.size());
            verify(scenePackMapper).selectScenePackList(any(FbsScenePack.class));
        }
    }
}
