package com.wx.fbsir.engine.controller;

import com.wx.fbsir.engine.capability.annotation.OnceCapability;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import com.wx.fbsir.engine.websocket.util.WebSocketSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

/**
 * Payload处理示例Controller
 * 
 * 演示如何从payload中提取各种类型的数据：
 * - 基础类型：String, Integer, Long, Boolean
 * - 复合类型：Map, List
 * - 嵌套结构：多层Map和List
 * 
 * @author FBSir
 * @date 2025-12-29
 */
@Component
@Controller
public class PayloadExampleController {

    private static final Logger log = LoggerFactory.getLogger(PayloadExampleController.class);
    private final WebSocketSender webSocketSender;

    public PayloadExampleController(WebSocketSender webSocketSender) {
        this.webSocketSender = webSocketSender;
    }

    /**
     * 示例：处理简单字段
     * 
     * 客户端消息：
     * {
     *   "type": "SIMPLE_TASK",
     *   "engineId": "engine-001",
     *   "payload": {
     *     "taskName": "测试任务",
     *     "priority": 1,
     *     "enabled": true
     *   }
     * }
     */
    @OnceCapability(type = "SIMPLE_TASK", description = "简单任务示例")
    public void handleSimpleTask(EngineMessage request) {
        try {
            // 提取基础类型字段
            String taskName = request.getPayloadValue("taskName");
            Integer priority = request.getPayloadValue("priority");
            Boolean enabled = request.getPayloadValue("enabled");
            
            log.info("[示例] 任务名称: {}, 优先级: {}, 启用: {}", taskName, priority, enabled);
            
            // 构建响应
            EngineMessage response = EngineMessage.builder()
                .type("SIMPLE_TASK_RESULT")
                .userId(request.getUserId())
                .engineId(request.getEngineId())
                .payload("success", true)
                .payload("message", "任务处理完成")
                .payload("taskName", taskName)
                .build();
            
            webSocketSender.send(response);
            
        } catch (Exception e) {
            log.error("[示例] 处理失败", e);
            sendError(request, "处理失败: " + e.getMessage());
        }
    }

    /**
     * 示例：处理嵌套对象
     * 
     * 客户端消息：
     * {
     *   "type": "NESTED_TASK",
     *   "engineId": "engine-001",
     *   "payload": {
     *     "user": {
     *       "id": "user123",
     *       "name": "张三",
     *       "settings": {
     *         "theme": "dark",
     *         "language": "zh-CN"
     *       }
     *     }
     *   }
     * }
     */
    @OnceCapability(type = "NESTED_TASK", description = "嵌套对象示例")
    public void handleNestedTask(EngineMessage request) {
        try {
            // 提取嵌套Map
            @SuppressWarnings("unchecked")
            Map<String, Object> user = (Map<String, Object>) request.getPayloadValue("user");
            
            if (user != null) {
                String userId = (String) user.get("id");
                String userName = (String) user.get("name");
                
                // 提取更深层的嵌套
                @SuppressWarnings("unchecked")
                Map<String, Object> settings = (Map<String, Object>) user.get("settings");
                if (settings != null) {
                    String theme = (String) settings.get("theme");
                    String language = (String) settings.get("language");
                    log.info("[示例] 用户: {} ({}), 主题: {}, 语言: {}", userName, userId, theme, language);
                }
            }
            
            // 构建响应
            EngineMessage response = EngineMessage.builder()
                .type("NESTED_TASK_RESULT")
                .userId(request.getUserId())
                .engineId(request.getEngineId())
                .payload("success", true)
                .payload("user", user)
                .build();
            
            webSocketSender.send(response);
            
        } catch (Exception e) {
            log.error("[示例] 处理失败", e);
            sendError(request, "处理失败: " + e.getMessage());
        }
    }

    /**
     * 示例：处理数组/列表
     * 
     * 客户端消息：
     * {
     *   "type": "BATCH_TASK",
     *   "engineId": "engine-001",
     *   "payload": {
     *     "items": [
     *       {"id": 1, "name": "Item 1"},
     *       {"id": 2, "name": "Item 2"}
     *     ]
     *   }
     * }
     */
    @OnceCapability(type = "BATCH_TASK", description = "批量任务示例")
    public void handleBatchTask(EngineMessage request) {
        try {
            // 提取List
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) request.getPayloadValue("items");
            
            if (items != null) {
                log.info("[示例] 收到 {} 个项目", items.size());
                
                // 遍历处理
                for (Map<String, Object> item : items) {
                    Integer id = (Integer) item.get("id");
                    String name = (String) item.get("name");
                    log.info("[示例] 处理项目: {} - {}", id, name);
                }
            }
            
            // 构建响应
            EngineMessage response = EngineMessage.builder()
                .type("BATCH_TASK_RESULT")
                .userId(request.getUserId())
                .engineId(request.getEngineId())
                .payload("success", true)
                .payload("processedCount", items != null ? items.size() : 0)
                .build();
            
            webSocketSender.send(response);
            
        } catch (Exception e) {
            log.error("[示例] 处理失败", e);
            sendError(request, "处理失败: " + e.getMessage());
        }
    }

    /**
     * 示例：处理复杂混合结构
     *
     * 客户端消息：
     * {"type": "COMPLEX_TASK","engineId": "engine-001","payload": {"config": {"timeout": 30000,"retry": true},"filters": [{"field": "status", "value": "active"}],"metadata": {"tags": ["tag1", "tag2"]}}}
     */
    @OnceCapability(type = "COMPLEX_TASK", description = "复杂结构示例")
    public void handleComplexTask(EngineMessage request) {
        try {
            // 提取配置对象
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) request.getPayloadValue("config");
            if (config != null) {
                Integer timeout = (Integer) config.get("timeout");
                Boolean retry = (Boolean) config.get("retry");
                log.info("[示例] 配置: 超时={}, 重试={}", timeout, retry);
            }
            
            // 提取过滤器列表
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filters = (List<Map<String, Object>>) request.getPayloadValue("filters");
            if (filters != null) {
                for (Map<String, Object> filter : filters) {
                    String field = (String) filter.get("field");
                    String value = (String) filter.get("value");
                    log.info("[示例] 过滤器: {} = {}", field, value);
                }
            }
            
            // 提取元数据中的标签数组
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) request.getPayloadValue("metadata");
            if (metadata != null) {
                @SuppressWarnings("unchecked")
                List<String> tags = (List<String>) metadata.get("tags");
                if (tags != null) {
                    log.info("[示例] 标签: {}", String.join(", ", tags));
                }
            }
            
            // 构建响应
            EngineMessage response = EngineMessage.builder()
                .type("COMPLEX_TASK_RESULT")
                .userId(request.getUserId())
                .engineId(request.getEngineId())
                .payload("success", true)
                .payload("config", config)
                .payload("filterCount", filters != null ? filters.size() : 0)
                .build();
            
            webSocketSender.send(response);
            
        } catch (Exception e) {
            log.error("[示例] 处理失败", e);
            sendError(request, "处理失败: " + e.getMessage());
        }
    }

    /**
     * 发送错误响应
     */
    private void sendError(EngineMessage request, String errorMessage) {
        EngineMessage response = EngineMessage.builder()
            .type(request.getType() + "_RESULT")
            .userId(request.getUserId())
            .engineId(request.getEngineId())
            .payload("success", false)
            .payload("error", errorMessage)
            .build();
        
        webSocketSender.send(response);
    }
}
