package com.wx.fbsir.business.fbs.controller;

import com.wx.fbsir.business.fbs.controller.business.FbsMyApiKeyBusinessController;
import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import com.wx.fbsir.business.fbs.service.business.IFbsApiKeyBusinessService;
import com.wx.fbsir.common.core.domain.AjaxResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FbsMyApiKeyBusinessController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class FbsMyApiKeyBusinessControllerTest {

    @Mock
    private IFbsApiKeyBusinessService apiKeyService;

    @InjectMocks
    private FbsMyApiKeyBusinessController controller;

    // ==== list ====

    @Test
    void testList_success() {
        // Arrange
        FbsApiKey key1 = createTestKey(1L, "fbs_abc123", "测试密钥1", 1);
        FbsApiKey key2 = createTestKey(2L, "fbs_def456", "测试密钥2", 1);
        List<FbsApiKey> keys = Arrays.asList(key1, key2);

        when(apiKeyService.listMyKeys()).thenReturn(keys);

        // Act
        AjaxResult result = controller.list();

        // Assert
        assertNotNull(result);
        assertEquals(200, result.get("code"));
        verify(apiKeyService).listMyKeys();
    }

    @Test
    void testList_empty() {
        // Arrange
        when(apiKeyService.listMyKeys()).thenReturn(Collections.emptyList());

        // Act
        AjaxResult result = controller.list();

        // Assert
        assertNotNull(result);
        assertEquals(200, result.get("code"));
        verify(apiKeyService).listMyKeys();
    }

    // ==== create ====

    @Test
    void testCreate_success() {
        // Arrange
        FbsApiKey createdKey = createTestKey(1L, "fbs_abc123xyz789", "我的测试密钥", 1);
        createdKey.setUserId(100L);

        when(apiKeyService.createByUser("我的测试密钥")).thenReturn(createdKey);

        Map<String, Object> params = new HashMap<>();
        params.put("name", "我的测试密钥");

        // Act
        AjaxResult result = controller.create(params);

        // Assert
        assertNotNull(result);
        assertEquals(200, result.get("code"));
        verify(apiKeyService).createByUser("我的测试密钥");
    }

    @Test
    void testCreate_withEmptyName() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("name", "");

        // Act
        AjaxResult result = controller.create(params);

        // Assert
        assertNotNull(result);
        assertEquals(500, result.get("code"));
        verify(apiKeyService, never()).createByUser(anyString());
    }

    @Test
    void testCreate_withNullName() {
        // Arrange
        Map<String, Object> params = new HashMap<>();

        // Act
        AjaxResult result = controller.create(params);

        // Assert
        assertNotNull(result);
        assertEquals(500, result.get("code"));
        verify(apiKeyService, never()).createByUser(anyString());
    }

    @Test
    void testCreate_withException() {
        // Arrange
        when(apiKeyService.createByUser(anyString()))
                .thenThrow(new RuntimeException("创建失败"));

        Map<String, Object> params = new HashMap<>();
        params.put("name", "测试");

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            controller.create(params);
        });

        verify(apiKeyService).createByUser("测试");
    }

    // ==== toggle ====

    @Test
    void testToggle_enable() {
        // Arrange
        doNothing().when(apiKeyService).toggleStatus(1L, 1);

        // Act
        AjaxResult result = controller.toggle(1L, 1);

        // Assert
        assertNotNull(result);
        assertEquals(200, result.get("code"));
        verify(apiKeyService).toggleStatus(1L, 1);
    }

    @Test
    void testToggle_disable() {
        // Arrange
        doNothing().when(apiKeyService).toggleStatus(1L, 0);

        // Act
        AjaxResult result = controller.toggle(1L, 0);

        // Assert
        assertNotNull(result);
        assertEquals(200, result.get("code"));
        verify(apiKeyService).toggleStatus(1L, 0);
    }

    @Test
    void testToggle_notFound() {
        // Arrange
        doThrow(new RuntimeException("API Key 不存在"))
                .when(apiKeyService).toggleStatus(999L, 1);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            controller.toggle(999L, 1);
        });

        verify(apiKeyService).toggleStatus(999L, 1);
    }

    @Test
    void testToggle_forbidden() {
        // Arrange
        doThrow(new RuntimeException("无权操作此 API Key"))
                .when(apiKeyService).toggleStatus(1L, 1);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            controller.toggle(1L, 1);
        });

        verify(apiKeyService).toggleStatus(1L, 1);
    }

    // ==== delete ====

    @Test
    void testDelete_success() {
        // Arrange
        doNothing().when(apiKeyService).deleteById(1L);

        // Act
        AjaxResult result = controller.delete(1L);

        // Assert
        assertNotNull(result);
        assertEquals(200, result.get("code"));
        verify(apiKeyService).deleteById(1L);
    }

    @Test
    void testDelete_notFound() {
        // Arrange
        doThrow(new RuntimeException("API Key 不存在"))
                .when(apiKeyService).deleteById(999L);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            controller.delete(999L);
        });

        verify(apiKeyService).deleteById(999L);
    }

    @Test
    void testDelete_forbidden() {
        // Arrange
        doThrow(new RuntimeException("无权删除此 API Key"))
                .when(apiKeyService).deleteById(1L);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            controller.delete(1L);
        });

        verify(apiKeyService).deleteById(1L);
    }

    // ==== Helper Methods ====

    private FbsApiKey createTestKey(Long id, String apiKey, String name, Integer status) {
        FbsApiKey key = new FbsApiKey();
        key.setId(id);
        key.setApiKey(apiKey);
        key.setName(name);
        key.setStatus(status);
        key.setCreateTime(new Date());
        key.setCreatedBy("test-user");
        return key;
    }
}
