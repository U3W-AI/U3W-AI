package com.playwright.websocket;

/**
 * @author 优立方
 * @version JDK 17
 * @date 2025年01月16日 17:14
 */

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.playwright.controller.ai.AIGCController;
import com.playwright.controller.ai.BrowserController;
import com.playwright.controller.media.BaijiahaoDeliveryController;
import com.playwright.controller.media.MediaController;
import com.playwright.controller.media.TTHController;
import com.playwright.controller.media.ZhihuDeliveryController;
import com.playwright.entity.UserInfoRequest;
import com.playwright.entity.mcp.ImgInfo;
import com.playwright.entity.mcp.Item;
import com.playwright.entity.mcp.McpResult;
import com.playwright.mcp.CubeMcp;
import com.playwright.utils.common.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class WebSocketClientService {
    // WebSocket服务器地址
    private final String serverUri;

    // WebSocket客户端实例
    private WebSocketClient webSocketClient;
    // 定时任务调度器
    private ScheduledExecutorService scheduler;
    // 是否正在重连标志
    private boolean reconnecting = false;
    // 重连任务
    private ScheduledFuture<?> reconnectTask;
    private ScheduledFuture<?> heartbeatTask;

    /**
     * 构造函数，初始化WebSocket连接
     */
    public WebSocketClientService(@Value("${cube.wssurl}") String serverUri) {
        this.serverUri = serverUri;
        if (serverUri == null || serverUri.trim().isEmpty()) {
            return;
        }
        initializeScheduler();
        connectToServer();
    }

    /**
     * 初始化定时任务调度器
     */
    private void initializeScheduler() {
        if (scheduler == null || scheduler.isShutdown() || scheduler.isTerminated()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
    }

    /**
     * 连接到WebSocket服务器
     */
    private void connectToServer() {
        try {
            // 创建WebSocket服务器URI
            URI uri = new URI(serverUri);
            // 创建WebSocket客户端
            webSocketClient = new WebSocketClient(uri) {
                /**
                 * 当WebSocket连接成功时调用
                 */
                @Override
                public void onOpen(ServerHandshake handshake) {
                    reconnecting = false;
                    stopReconnectionTask(); // 停止重连任务
                    startHeartbeatTask();
                }


                /**
                 * 当接收到消息时调用
                 */
                @Override
                public void onMessage(String message) {
                    TTHController tthController = SpringContextUtils.getBean(TTHController.class);
                    MediaController mediaController = SpringContextUtils.getBean(MediaController.class);
                    BrowserController browserController = SpringContextUtils.getBean(BrowserController.class);
                    AIGCController aigcController = SpringContextUtils.getBean(AIGCController.class);
                    UserInfoRequest userInfoRequest = JSONObject.parseObject(message, UserInfoRequest.class);
                    BrowserConcurrencyManager concurrencyManager = SpringContextUtils.getBean(BrowserConcurrencyManager.class);
                    BrowserTaskWrapper taskWrapper = SpringContextUtils.getBean(BrowserTaskWrapper.class);
                    ZhihuDeliveryController zhihuDeliveryController = SpringContextUtils.getBean(ZhihuDeliveryController.class);
                    BaijiahaoDeliveryController baijiahaoDeliveryController = SpringContextUtils.getBean(BaijiahaoDeliveryController.class);

                    // 打印当前并发状态
                    taskWrapper.printStatus();
                    String aiName = userInfoRequest.getAiName();
                    if (message.contains("AI排版")) {
                        aiLayoutPrompt(userInfoRequest);
                    }

                    // 处理包含"使用F8S"的消息
                    if (message.contains("使用F8S") || message.contains("AI评分") || message.contains("AI排版")) {
                        //豆包生成图片
                        if (message.contains("db-img")) {
                            concurrencyManager.submitBrowserTaskWithDeduplication(() -> {
                                startAI(userInfoRequest, aiName, "图片生成", browserController, aigcController);
                            }, "豆包智能体", userInfoRequest.getUserId(), 5, userInfoRequest.getUserPrompt());
                        }
                        // 公众号排版
                        if (message.contains("znpb-ds")) {
                            concurrencyManager.submitBrowserTaskWithDeduplication(() -> {
                                startAI(userInfoRequest, aiName, "排版", browserController, aigcController);
                            }, "豆包智能体", userInfoRequest.getUserId(), 5, userInfoRequest.getUserPrompt());
                        }
                        // 使用带去重功能的任务提交，防止重复调用
                        if (message.contains("zhzd-chat")) {
                            concurrencyManager.submitBrowserTaskWithDeduplication(() -> {
                                startAI(userInfoRequest, aiName, "知乎直答", browserController, aigcController);
                            }, "智谱AI", userInfoRequest.getUserId(), 5, userInfoRequest.getUserPrompt());
                        }
                        // 处理包含"metaso"的消息
                        if (message.contains("mita")) {
                            concurrencyManager.submitBrowserTask(() -> {
                                startAI(userInfoRequest, aiName, "秘塔", browserController, aigcController);
                            }, "Metaso智能体", userInfoRequest.getUserId());
                        }
                        // 处理包含"yb-hunyuan"息,yb-deepseek"的消息
                        if (message.contains("yb-hunyuan-pt") || message.contains("yb-deepseek-pt")) {
                            concurrencyManager.submitBrowserTask(() -> {
                                startAI(userInfoRequest, aiName, "元宝", browserController, aigcController);
                            }, "元宝智能体", userInfoRequest.getUserId());
                        }
                        // 处理包含"zj-db"的消息
                        if (message.contains("zj-db")) {
                            concurrencyManager.submitBrowserTaskWithDeduplication(() -> {
                                startAI(userInfoRequest, aiName, "豆包", browserController, aigcController);
                            }, "豆包智能体", userInfoRequest.getUserId(), 5, userInfoRequest.getUserPrompt());
                        }

                        // 处理包含"baidu-agent"的消息
                        if (userInfoRequest.getRoles() != null && userInfoRequest.getRoles().contains("baidu-agent")) {
                            concurrencyManager.submitBrowserTask(() -> {
                                startAI(userInfoRequest, aiName, "百度", browserController, aigcController);
                            }, "百度AI", userInfoRequest.getUserId());
                        }
                        // 处理包含"deepseek"的消息
                        if (message.contains("deepseek,")) {
                            concurrencyManager.submitBrowserTaskWithDeduplication(() -> {
                                startAI(userInfoRequest, aiName, "DeepSeek", browserController, aigcController);
                            }, "DeepSeek智能体", userInfoRequest.getUserId(), 5, userInfoRequest.getUserPrompt());
                        }
                        // 处理包含"ty-qw"的信息
                        if (message.contains("ty-qw")) {
                            concurrencyManager.submitBrowserTaskWithDeduplication(() -> {
                                startAI(userInfoRequest, aiName, "通义千问", browserController, aigcController);
                            }, "通义千问", userInfoRequest.getUserId(), 5, userInfoRequest.getUserPrompt());
                        }
                    }
                    if(message.contains("媒体投递")) {
                        // 处理知乎投递的消息
                        if(message.contains("zhihu")){
                            concurrencyManager.submitBrowserTask(() -> {
                                try {
                                    // 获取ZhihuDeliveryController的实例并调用投递方法
                                    zhihuDeliveryController.deliverToZhihu(userInfoRequest);
                                } catch (Exception e) {
                                    // 发送错误消息
                                    userInfoRequest.setType("RETURN_ZHIHU_DELIVERY_RES");
                                    userInfoRequest.setStatus("error");
                                    userInfoRequest.setDraftContent("投递到知乎失败：" + e.getMessage());
                                    sendMessage(JSON.toJSONString(userInfoRequest));
                                }
                            }, "知乎投递", userInfoRequest.getUserId());
                        }

                        // 处理百家号投递的消息
                        if(message.contains("baijiahao")){
                            concurrencyManager.submitBrowserTask(() -> {
                                try {
                                    // 获取BaijiahaoDeliveryController的实例并调用投递方法
                                    baijiahaoDeliveryController.deliverToBaijiahao(userInfoRequest);
                                } catch (Exception e) {
                                    // 发送错误消息
                                    userInfoRequest.setType("RETURN_BAIJIAHAO_DELIVERY_RES");
                                    userInfoRequest.setStatus("error");
                                    userInfoRequest.setDraftContent("投递到百家号失败：" + e.getMessage());
                                    sendMessage(JSON.toJSONString(userInfoRequest));
                                }
                            }, "百家号投递", userInfoRequest.getUserId());
                        }
                        // 处理包含"微头条发布"的消息
                        if(message.contains("weitoutiao")){
                            concurrencyManager.submitBrowserTask(() -> {
                                try {
                                    tthController.pushToTTH(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }, "微头条发布", userInfoRequest.getUserId());
                        }
                    }

                    // 处理媒体登录相关
                    // 处理获取TT二维码的消息
                    if(message.contains("PLAY_GET_TTH_QRCODE")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                mediaController.getTTHQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取头条号二维码", userInfoRequest.getUserId());
                    }

                    // 处理获取TT登录状态的消息
                    if (message.contains("PLAY_CHECK_TTH_LOGIN")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                String checkLogin = mediaController.checkTTHLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_TOUTIAO_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "头条号登录检查", userInfoRequest.getUserId());
                    }
                    // 处理检查知乎登录状态的消息
                    if (message.contains("PLAY_CHECK_ZHIHU_LOGIN")) {
                        // 🚀 知乎状态检测使用高优先级，优先处理
                        concurrencyManager.submitHighPriorityTask(() -> {
                            try {
                                String checkLogin = mediaController.checkZhihuLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_ZHIHU_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                                // 发送错误状态
                                userInfoRequest.setStatus("false");
                                userInfoRequest.setType("RETURN_ZHIHU_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            }
                        }, "知乎登录检查", userInfoRequest.getUserId());
                    }

                    // 处理获取知乎二维码的消息
                    if(message.contains("PLAY_GET_ZHIHU_QRCODE")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                mediaController.getZhihuQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取知乎二维码", userInfoRequest.getUserId());
                    }

                    // 处理检查百家号登录状态的消息
                    if (message.contains("PLAY_CHECK_BAIJIAHAO_LOGIN")) {
                        // 🚀 百家号状态检测使用高优先级，优先处理
                        concurrencyManager.submitHighPriorityTask(() -> {
                            try {
                                String checkLogin = mediaController.checkBaijiahaoLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_BAIJIAHAO_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                                // 发送错误状态
                                userInfoRequest.setStatus("false");
                                userInfoRequest.setType("RETURN_BAIJIAHAO_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            }
                        }, "百家号登录检查", userInfoRequest.getUserId());
                    }

                    // 处理获取百家号二维码的消息
                    if(message.contains("PLAY_GET_BAIJIAHAO_QRCODE")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                mediaController.getBaijiahaoQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取百家号二维码", userInfoRequest.getUserId());
                    }

                    // 处理获取知乎二维码的消息
                    if (message.contains("PLAY_GET_ZHIHU_QRCODE")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getZhihuQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取知乎二维码", userInfoRequest.getUserId());
                    }

                    // 处理检查知乎登录状态的消息
                    if (message.contains("PLAY_CHECK_ZHIHU_LOGIN")) {
                        // 🚀 知乎状态检测使用高优先级，优先处理
                        concurrencyManager.submitHighPriorityTask(() -> {
                            try {
                                String checkLogin = browserController.checkZhihuLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_ZHIHU_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                                // 发送错误状态
                                userInfoRequest.setStatus("false");
                                userInfoRequest.setType("RETURN_ZHIHU_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            }
                        }, "知乎登录检查", userInfoRequest.getUserId());
                    }

                    //  处理检查秘塔登录状态的信息
                    if (message.contains("CHECK_METASO_LOGIN")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                String checkLogin = browserController.checkMetasoLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_METASO_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "Metaso登录检查", userInfoRequest.getUserId());
                    }

                    // 处理获取秘塔二维码的消息
                    if (message.contains("PLAY_GET_METASO_QRCODE")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getMetasoQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取Metaso二维码", userInfoRequest.getUserId());
                    }

                    // 处理检查DeepSeek登录状态的消息
                    if (message.contains("PLAY_CHECK_DEEPSEEK_LOGIN")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                // 先尝试获取登录状态
                                String checkLogin = browserController.checkDSLogin(userInfoRequest.getUserId());

                                // 构建并发送状态消息 - 使用与其他AI智能体一致的格式
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_DEEPSEEK_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                                // 发送错误状态 - 使用与其他AI智能体一致的格式
                                userInfoRequest.setStatus("false");
                                userInfoRequest.setType("RETURN_DEEPSEEK_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            }
                        }, "DeepSeek登录检查", userInfoRequest.getUserId());
                    }

                    // 处理获取DeepSeek二维码的消息
                    if (message.contains("PLAY_GET_DEEPSEEK_QRCODE")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getDSQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取DeepSeek二维码", userInfoRequest.getUserId());
                    }

                    // 处理包含"START_YB"的消息
                    if (message.contains("START_YB")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                aigcController.startYB(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "启动元包", userInfoRequest.getUserId());
                    }

//                    // 处理包含"AI排版"的消息
//                    if(message.contains("AI排版")){
//                        concurrencyManager.submitBrowserTask(() -> {
//                            try {
////                                aigcController.startYBOffice(userInfoRequest);
//                                cubeMcp.publishToOffice(userInfoRequest);
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                        }, "AI排版", userInfoRequest.getUserId());
//                    }

                    // 处理检查百度AI登录状态的消息
                    if (message.contains("PLAY_CHECK_BAIDU_LOGIN")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                String checkLogin = browserController.checkBaiduLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_BAIDU_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                                // 发送错误状态
                                userInfoRequest.setStatus("false");
                                userInfoRequest.setType("RETURN_BAIDU_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            }
                        }, "百度AI登录检查", userInfoRequest.getUserId());
                    }

                    // 处理检查通义千问登录状态的消息
                    if (message.contains("PLAY_CHECK_QW_LOGIN")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                String checkLogin = browserController.checkTongYiLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_TY_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "通义千问登录检查", userInfoRequest.getUserId());
                    }


                    // 处理获取百度AI二维码的消息
                    if (message.contains("PLAY_GET_BAIDU_QRCODE")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getBaiduQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取百度AI二维码", userInfoRequest.getUserId());
                    }

                    // 处理获取通义千问二维码的消息
                    if (message.contains("PLAY_GET_QW_QRCODE")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getTongYiQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取通义千问二维码", userInfoRequest.getUserId());
                    }

                    // 处理获取yb二维码的消息
                    if (message.contains("PLAY_GET_YB_QRCODE")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getYBQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取元宝二维码", userInfoRequest.getUserId());
                    }

//                    if(message.contains("AI评分")){
//                        new Thread(() -> {
//                            try {
//                                aigcController.startDBScore(userInfoRequest);
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                        }).start();
//                    }
                    // 处理检查yb登录状态的消息
                    if (message.contains("CHECK_YB_LOGIN")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                String checkLogin = browserController.checkYBLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_YB_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "元宝登录检查", userInfoRequest.getUserId());
                    }

                    // 处理检查数据库登录状态的消息
                    if (message.contains("CHECK_DB_LOGIN")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                String checkLogin = browserController.checkDBLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_DB_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "豆包登录检查", userInfoRequest.getUserId());
                    }


                    // 处理获取数据库二维码的消息
                    if (message.contains("PLAY_GET_DB_QRCODE")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getDBQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取豆包二维码", userInfoRequest.getUserId());
                    }


                }

                /**
                 * 当WebSocket连接关闭时调用
                 */
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    startReconnectionTask();
                    stopHeartbeatTask();
                }

                /**
                 * 当WebSocket发生错误时调用
                 */
                @Override
                public void onError(Exception ex) {
                    startReconnectionTask();
                    stopHeartbeatTask();
                }
            };

            // 连接到WebSocket服务器
            webSocketClient.connect();

        } catch (URISyntaxException e) {
        }
    }

    /**
     * 启动心跳任务
     */
    private void startHeartbeatTask() {
        if (scheduler == null || scheduler.isShutdown() || scheduler.isTerminated()) {
            initializeScheduler();
        }

        stopHeartbeatTask(); // 避免重复创建

        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (webSocketClient != null && webSocketClient.isOpen()) {
                JSONObject pingMessage = new JSONObject();
                pingMessage.put("type", "heartbeat");
                webSocketClient.send(pingMessage.toJSONString());
            }
        }, 0, 30, TimeUnit.SECONDS); // 每 30 秒发送一次
    }

    /**
     * 关闭心跳任务
     */
    private void stopHeartbeatTask() {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    /**
     * 启动重连任务
     */
    private void startReconnectionTask() {
        initializeScheduler();

        if (reconnecting) {
            return; // 避免重复启动重连任务
        }

        reconnecting = true;

        // 停止之前的重连任务（如果有的话），确保不会创建多个任务
        stopReconnectionTask();

        // 启动新的重连任务
        reconnectTask = scheduler.scheduleWithFixedDelay(() -> {
            if (webSocketClient == null || !webSocketClient.isOpen()) {
                connectToServer();
            } else {
                stopReconnectionTask(); // 连接成功后，停止任务
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    /**
     * 停止重连任务
     */
    private void stopReconnectionTask() {
        if (reconnectTask != null && !reconnectTask.isCancelled()) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    /**
     * 发送消息到WebSocket服务器
     */
    public void sendMessage(String message) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.send(message);
        }
    }

    /**
     * 用于区分消息的方法
     */
    public void sendMessage(UserInfoRequest userInfoRequest, McpResult mcpResult, String aiName) {
        Map<String, String> content = new HashMap<>();
        content.put("type", userInfoRequest.getType());
        content.put("userId", userInfoRequest.getUserId());
        content.put("aiName", aiName);
        content.put("taskId", userInfoRequest.getTaskId());
        if ("openAI".equals(userInfoRequest.getType())) {
            String result = "";
            if (mcpResult == null || mcpResult.getResult() == null || mcpResult.getResult().isEmpty()) {
                result = aiName + "执行错误,请稍后重试";
            } else {
                result = mcpResult.getResult();
            }
            content.put("message", result);
        } else {
//            TODO 其他情况
        }
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.send(JSONObject.toJSONString(content));
        }
    }

    public void startAI(UserInfoRequest userInfoRequest, String aiName, String cnName, BrowserController browserController, AIGCController aigcController) {
        try {
//            不同ai不同处理
            String status = null;
            switch (cnName) {
                case "知乎直答" -> {
                    status = browserController.checkZhihuLogin(userInfoRequest.getUserId());
                }
                case "秘塔" -> {
                    status = browserController.checkMetasoLogin(userInfoRequest.getUserId());
                }
                case "元宝", "排版" -> {
                    status = browserController.checkYBLogin(userInfoRequest.getUserId());
                }
                case "豆包", "图片生成" -> {
                    status = browserController.checkDBLogin(userInfoRequest.getUserId());
                }
                case "百度" -> {
                    status = browserController.checkBaiduLogin(userInfoRequest.getUserId());
                }
                case "DeepSeek" -> {
                    status = browserController.checkDSLogin(userInfoRequest.getUserId());
                }
                case "通义千问" -> {
                    status = browserController.checkTongYiLogin(userInfoRequest.getUserId());
                }
//                TODO 后续添加其他AI
            }

            if (status == null || status.equals("未登录") || status.equals("false")) {
                sendMessage(userInfoRequest, McpResult.fail("请先前往后台登录" + cnName, null), aiName);
                return;
            }

//            不同AI不同处理
            McpResult mcpResult = null;
            switch (cnName) {
                case "知乎直答" -> {
                    mcpResult = aigcController.startZHZD(userInfoRequest);
                }
                case "秘塔" -> {
                    mcpResult = aigcController.startMetaso(userInfoRequest);
                }
                case "元宝" -> {
                    mcpResult = aigcController.startYB(userInfoRequest);
                }
                case "豆包" -> {
                    mcpResult = aigcController.startDB(userInfoRequest);
                }
                case "百度" -> {
                    mcpResult = aigcController.startBaidu(userInfoRequest);
                }
                case "DeepSeek" -> {
                    mcpResult = aigcController.startDS(userInfoRequest);
                }
                case "通义千问" -> {
                    mcpResult = aigcController.startTYQianwen(userInfoRequest);
                }
                case "排版" -> {
                    mcpResult = aigcController.startYBOffice(userInfoRequest);
                }
                case "图片生成" -> {
                    mcpResult = aigcController.startDBImg(userInfoRequest);
                }
//                TODO 后续添加其他AI
            }


            if (aiName.contains("stream")) {
                return;
            }
            sendMessage(userInfoRequest, mcpResult, aiName);
        } catch (Exception e) {
            sendMessage(userInfoRequest, McpResult.fail("生成失败,请稍后再试", null), aiName);
        }
    }

    public void aiLayoutPrompt(UserInfoRequest userInfoRequest) {
        try {
            LayoutPromptUtil layoutPromptUtil = SpringContextUtils.getBean(LayoutPromptUtil.class);
            UserInfoUtil userInfoUtil = SpringContextUtils.getBean(UserInfoUtil.class);
            CubeMcp cubeMcp = SpringContextUtils.getBean(CubeMcp.class);
            String selectedMedia = userInfoRequest.getSelectedMedia();
//            获取排版提示词
            String znpbPrompt = layoutPromptUtil.getLayoutPrompt(selectedMedia);
            String content = userInfoRequest.getUserPrompt();
            String unionId = userInfoUtil.getUnionIdByUserId(userInfoRequest.getUserId());
            userInfoRequest.setUnionId(unionId);
            if (selectedMedia.contains("wechat")) {
                // 获取图片信息
                McpResult mcp = cubeMcp.getMaterial(userInfoRequest);
                String listJson = mcp.getResult();
                String thumbMediaId = null;
                List<Item> images = JSONUtil.toList(listJson, Item.class);
                List<ImgInfo> imgInfoList = new ArrayList<>();

                for (Item image : images) {
                    String name = image.getName();
                    if (name.contains(unionId)) {
                        if (thumbMediaId == null && name.contains("封面")) {
                            thumbMediaId = image.getMedia_id();
                            continue;
                        }
                        ImgInfo imgInfo = new ImgInfo();
                        imgInfo.setImgDescription(name.substring(name.indexOf("-")));
                        imgInfo.setImgUrl(image.getUrl());
                        imgInfoList.add(imgInfo);
                    }
                }
                if (imgInfoList.isEmpty()) {
                    userInfoRequest.setUserPrompt("文本内容: `" + content + "`" + ", " + znpbPrompt);
                } else {
                    userInfoRequest.setUserPrompt("文本内容: `" + content + "`" + ", 图片信息: {" + imgInfoList.toString() + "} " + znpbPrompt);
                }
            } else {
                userInfoRequest.setUserPrompt("文本内容: `" + content + "`" + ", " + znpbPrompt);
            }
            //TODO 添加其他媒体排版

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
