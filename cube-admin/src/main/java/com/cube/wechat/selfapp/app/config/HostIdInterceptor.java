package com.cube.wechat.selfapp.app.config;

import com.cube.wechat.selfapp.app.mapper.SysHostWhitelistMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2025年07月11日 09:48
 */
@Component
public class HostIdInterceptor implements HandshakeInterceptor {

    @Autowired
    private SysHostWhitelistMapper whitelistMapper;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        URI uri = request.getURI();
        String query = uri.getQuery();
        String hostId = null;

        if (query != null && query.contains("clientId=")) {
            String clientId = Arrays.stream(query.split("&"))
                    .filter(param -> param.startsWith("clientId="))
                    .map(param -> param.split("=")[1])
                    .findFirst()
                    .orElse(null);

            if (clientId != null && clientId.startsWith("play-")) {
                hostId = clientId.substring("play-".length()); // 提取 play- 后面的部分
            }
        }

        if(query.contains("play")){
            int res = whitelistMapper.selectActiveByHostId(hostId);
            if (hostId == null || res == 0) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                response.getBody().write("主机ID未注册，请联系管理员".getBytes(StandardCharsets.UTF_8));
                return false;
            }
        }

        attributes.put("hostId", hostId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
