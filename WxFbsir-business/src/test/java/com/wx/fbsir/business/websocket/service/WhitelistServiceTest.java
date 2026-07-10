package com.wx.fbsir.business.websocket.service;

import com.wx.fbsir.business.websocket.domain.WsHostWhitelist;
import com.wx.fbsir.business.websocket.mapper.WsHostWhitelistMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WhitelistService 测试")
class WhitelistServiceTest {

    @Mock
    private WsHostWhitelistMapper whitelistMapper;

    @InjectMocks
    private WhitelistService service;

    @Test
    @DisplayName("Engine 类型白名单可通过 Engine 连接校验")
    void engineWhitelistShouldPassForEngineConnection() {
        WsHostWhitelist whitelist = buildWhitelist("engine-001", "engine");
        when(whitelistMapper.selectByHostId("engine-001")).thenReturn(whitelist);

        WhitelistService.ValidationResult result = service.validateHostId("engine-001", "127.0.0.1", "engine");

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("OpenClaw 条目不能被当作 Engine 节点")
    void openClawWhitelistShouldBeRejectedForEngineConnection() {
        WsHostWhitelist whitelist = buildWhitelist("test", "openclaw");
        when(whitelistMapper.selectByHostId("test")).thenReturn(whitelist);

        WhitelistService.ValidationResult result = service.validateHostId("test", "127.0.0.1", "engine");

        assertEquals("HOST_TYPE_MISMATCH", result.getCode());
    }

    @Test
    @DisplayName("空类型旧记录仍兼容 Engine 连接")
    void blankHostTypeShouldRemainCompatible() {
        WsHostWhitelist whitelist = buildWhitelist("legacy-engine", null);
        when(whitelistMapper.selectByHostId("legacy-engine")).thenReturn(whitelist);

        WhitelistService.ValidationResult result = service.validateHostId("legacy-engine", "127.0.0.1", "engine");

        assertTrue(result.isValid());
    }

    private WsHostWhitelist buildWhitelist(String hostId, String hostType) {
        WsHostWhitelist whitelist = new WsHostWhitelist();
        whitelist.setHostId(hostId);
        whitelist.setHostType(hostType);
        whitelist.setStatus(1);
        return whitelist;
    }
}
