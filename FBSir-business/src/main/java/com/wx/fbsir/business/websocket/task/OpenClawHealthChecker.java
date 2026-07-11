package com.wx.fbsir.business.websocket.task;

import com.wx.fbsir.business.websocket.domain.WsHostWhitelist;
import com.wx.fbsir.business.websocket.mapper.WsHostWhitelistMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;

import java.util.List;

/**
 * OpenClaw主机健康检查定时任务
 *
 * @author FBSir
 * @date 2026-03-14
 */
@Component
public class OpenClawHealthChecker {

    private static final Logger logger = LoggerFactory.getLogger(OpenClawHealthChecker.class);

    @Autowired
    private WsHostWhitelistMapper wsHostWhitelistMapper;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 每30秒执行一次健康检查
     */
    @Scheduled(fixedRate = 30000)
    public void checkOpenClawHosts() {
        logger.info("开始执行OpenClaw主机健康检查...");

        try {
            // 查询所有启用的OpenClaw主机
            List<WsHostWhitelist> openclawHosts = wsHostWhitelistMapper.selectOpenclawHosts();

            if (openclawHosts.isEmpty()) {
                logger.info("没有需要检查的OpenClaw主机");
                return;
            }

            logger.info("共检查 {} 台OpenClaw主机", openclawHosts.size());

            // 并行处理每台主机的健康检查
            openclawHosts.parallelStream().forEach(host -> {
                checkSingleHost(host);
            });

            logger.info("OpenClaw主机健康检查完成");
        } catch (Exception e) {
            logger.error("执行OpenClaw主机健康检查时发生错误", e);
        }
    }

    /**
     * 检查单台主机的健康状态
     *
     * @param host 主机信息
     */
    private void checkSingleHost(WsHostWhitelist host) {
        String hostId = host.getHostId();
        String healthCheckUrl = host.getHealthCheckUrl();

        if (healthCheckUrl == null || healthCheckUrl.isEmpty()) {
            logger.warn("主机 {} 未配置健康检查URL，跳过检查", hostId);
            return;
        }


        try {
            // 设置5秒超时
            restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                    super.prepareConnection(connection, httpMethod);
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                }
            });

            // 发送健康检查请求
            ResponseEntity<String> response = restTemplate.exchange(
                    healthCheckUrl,
                    HttpMethod.GET,
                    null,
                    String.class
            );

            // 根据响应结果更新在线状态
            String newStatus = (response.getStatusCode().is2xxSuccessful()) ? "online" : "offline";

            if (!newStatus.equals(host.getOnlineStatus())) {
                host.setOnlineStatus(newStatus);
                wsHostWhitelistMapper.update(host);
                logger.info("主机 {} 状态更新为: {}", hostId, newStatus);
            } else {
                logger.debug("主机 {} 状态保持为: {}", hostId, newStatus);
            }
        } catch (Exception e) {
            // 异常情况标记为离线
            if (!"offline".equals(host.getOnlineStatus())) {
                host.setOnlineStatus("offline");
                wsHostWhitelistMapper.update(host);
                logger.warn("主机 {} 健康检查失败，状态更新为: offline，错误信息: {}", hostId, e.getMessage());
            } else {
                logger.debug("主机 {} 健康检查失败，状态保持为: offline", hostId);
            }
        }
    }
}
