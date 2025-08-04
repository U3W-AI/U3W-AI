package com.playwright.websocket;

/**
 * @author 优立方
 * @version JDK 17
 * @date 2025年01月16日 17:14
 */

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.playwright.controller.*;
import com.playwright.entity.UserInfoRequest;
import com.playwright.utils.SpringContextUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
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
            System.out.println("WebSocket 服务器地址为空，跳过连接建立。");
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
                    System.out.println("WebSocket 连接已建立。");
                    reconnecting = false;
                    stopReconnectionTask(); // 停止重连任务
                    startHeartbeatTask();
                }

                /**
                 * 当接收到消息时调用
                 */
                @Override
                public void onMessage(String message) {
                    BrowserController browserController = SpringContextUtils.getBean(BrowserController.class);
                    AIGCController aigcController = SpringContextUtils.getBean(AIGCController.class);;
                    UserInfoRequest userInfoRequest = JSONObject.parseObject(message, UserInfoRequest.class);
                    TTHController tthController = SpringContextUtils.getBean(TTHController.class);
                    MediaController mediaController = SpringContextUtils.getBean(MediaController.class);
                    System.out.println("Received message: " + message);

                    // 处理包含"使用F8S"的消息
                    if(message.contains("使用F8S")){
                        // 处理包含"cube"的消息
                        if(message.contains("cube")){
                            new Thread(() -> {
                                try {
                                    aigcController.startAgent(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }
                        // 处理包含"mini-max"的消息
                        if(message.contains("mini-max")){
                            new Thread(() -> {
                                try {
                                    aigcController.startMiniMax(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }
                        // 处理包含"metaso"的消息
                        if(message.contains("mita")){
                            new Thread(() -> {
                                try {
                                    aigcController.startMetaso(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }
                        // 处理包含"yb-hunyuan"或"yb-deepseek"的消息
                        if(message.contains("yb-hunyuan") || message.contains("yb-deepseek")){
                            new Thread(() -> {
                                try {
                                    aigcController.startYB(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }
                        // 处理包含"zj-db"的消息
                        if(message.contains("zj-db")){
                            new Thread(() -> {
                                try {
                                    aigcController.startDB(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }
                        // 处理包含"deepseek"的消息
                        if(message.contains("deepseek")){
                            new Thread(() -> {
                                try {
                                    aigcController.startDeepSeek(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }

                        // 处理包含"ty-qw"的信息
                        if (message.contains("ty-qw")){
                            new Thread(() -> {
                                try {
                                    aigcController.startTYQianwen(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }

                        // 处理包含"kimi"的消息
                        if(message.contains("kimi")){
                            new Thread(() -> {
                                try {
                                    aigcController.startKimi(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }
                    }

                    // 处理包含"AI评分"的消息
                    if(message.contains("AI评分")){
                        new Thread(() -> {
                            try {
                                aigcController.startDBScore(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理包含"START_AGENT"的消息
                    if(message.contains("START_AGENT")){
                        JSONObject jsonObject = JSONObject.parseObject(message);
                        new Thread(() -> {
                            try {
                                aigcController.startAgent(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                    // 处理包含"START_YB"的消息
                    if(message.contains("START_YB")){
                        JSONObject jsonObject = JSONObject.parseObject(message);
                        new Thread(() -> {
                            try {
                                aigcController.startYB(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                    // 处理包含"START_DBOffice"的消息
                    if(message.contains("AI排版")){
                        JSONObject jsonObject = JSONObject.parseObject(message);
                        new Thread(() -> {
                            try {
                                aigcController.startDBOffice(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理包含"START_DEEPSEEK"的消息
                    if(message.contains("START_DEEPSEEK")){
                        JSONObject jsonObject = JSONObject.parseObject(message);
                        new Thread(() -> {
                            try {
                                aigcController.startDeepSeek(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理获取agent二维码的消息
                    if(message.contains("PLAY_GET_AGENT_QRCODE")){
                        new Thread(() -> {
                            try {
                                browserController.getAgentQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理检查agent登录状态的消息
                    if (message.contains("CHECK_AGENT_LOGIN")) {
                        new Thread(() -> {
                            try {
                                String checkLogin = browserController.checkAgentLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_AGENT_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理获取通义千问二维码的消息
                    if(message.contains("PLAY_GET_QW_QRCODE")){
                        new Thread(() -> {
                            try {
                                browserController.getTongYiQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理检查通义千问登录状态的消息
                    if (message.contains("PLAY_CHECK_QW_LOGIN")) {
                        new Thread(() -> {
                            try {
                                String checkLogin = browserController.checkTongYiLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_TY_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                    // 处理获取yb二维码的消息
                    if(message.contains("PLAY_GET_YB_QRCODE")){
                        new Thread(() -> {
                            try {
                                browserController.getYBQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理检查yb登录状态的消息
                    if (message.contains("CHECK_YB_LOGIN")) {
                        new Thread(() -> {
                            try {
                                String checkLogin = browserController.checkLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_YB_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理检查数据库登录状态的消息
                    if (message.contains("CHECK_DB_LOGIN")) {
                        new Thread(() -> {
                            try {
                                String checkLogin = browserController.checkDBLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_DB_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理检查MiniMax登录状态的信息
                    if (message.contains("CHECK_MAX_LOGIN")) {
                        new Thread(() -> {
                            try {
                                String checkLogin = browserController.checkMaxLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_MAX_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                    // 处理获取MiniMax二维码的消息
                    if(message.contains("PLAY_GET_MAX_QRCODE")){
                        new Thread(() -> {
                            try {
                                browserController.getMaxQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理检查Kimi登录状态的信息
                    if (message.contains("CHECK_KIMI_LOGIN")) {
                        new Thread(() -> {
                            try {
                                String checkLogin = browserController.checkKimiLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_KIMI_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                    // 处理获取KiMi二维码的消息
                    if(message.contains("PLAY_GET_KIMI_QRCODE")){
                        new Thread(() -> {
                            try {
                                browserController.getKiMiQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理获取数据库二维码的消息
                    if(message.contains("PLAY_GET_DB_QRCODE")){
                        JSONObject jsonObject = JSONObject.parseObject(message);
                        new Thread(() -> {
                            try {
                                browserController.getDBQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    //  处理检查秘塔登录状态的信息
                    if (message.contains("CHECK_METASO_LOGIN")) {
                        new Thread(() -> {
                            try {
                                String checkLogin = browserController.checkMetasoLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_METASO_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                    // 处理获取秘塔二维码的消息
                    if(message.contains("PLAY_GET_METASO_QRCODE")){
                        new Thread(() -> {
                            try {
                                browserController.getMetasoQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理检查DeepSeek登录状态的消息
                    if (message.contains("PLAY_CHECK_DEEPSEEK_LOGIN")) {
                        new Thread(() -> {
                            try {
                                // 先尝试获取登录状态
                                String checkLogin = browserController.checkDeepSeekLogin(userInfoRequest.getUserId());

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
                        }).start();
                    }

                    // 处理获取DeepSeek二维码的消息
                    if(message.contains("PLAY_GET_DEEPSEEK_QRCODE")){
                        new Thread(() -> {
                            try {
                                browserController.getDeepSeekQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理检查知乎登录状态的消息
                    if (message.contains("PLAY_CHECK_ZHIHU_LOGIN")) {
                        new Thread(() -> {
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
                        }).start();
                    }
                    // 处理检查百家号登录状态的消息
                    if (message.contains("PLAY_CHECK_BAIJIAHAO_LOGIN")) {
                        new Thread(() -> {
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
                        }).start();
                    }

                    // 处理获取知乎二维码的消息
                    if(message.contains("PLAY_GET_ZHIHU_QRCODE")){
                        new Thread(() -> {
                            try {
                                mediaController.getZhihuQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理获取百家号二维码的消息
                    if(message.contains("PLAY_GET_BAIJIAHAO_QRCODE")){
                        new Thread(() -> {
                            try {
                                mediaController.getBaijiahaoQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    // 处理知乎投递的消息
                    if(message.contains("投递到知乎")){
                        JSONObject jsonObject = JSONObject.parseObject(message);
                        new Thread(() -> {
                            try {
                                // 获取ZhihuDeliveryController的实例并调用投递方法
                                ZhihuDeliveryController zhihuDeliveryController = SpringContextUtils.getBean(ZhihuDeliveryController.class);
                                zhihuDeliveryController.deliverToZhihu(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                                // 发送错误消息
                                userInfoRequest.setType("RETURN_ZHIHU_DELIVERY_RES");
                                userInfoRequest.setStatus("error");
                                userInfoRequest.setDraftContent("投递到知乎失败：" + e.getMessage());
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            }
                        }).start();
                    }

                    // 处理百家号投递的消息
                    if(message.contains("投递到百家号")){
                        JSONObject jsonObject = JSONObject.parseObject(message);
                        new Thread(() -> {
                            try {
                                // 获取BaijiahaoDeliveryController的实例并调用投递方法
                                BaijiahaoDeliveryController baijiahaoDeliveryController = SpringContextUtils.getBean(BaijiahaoDeliveryController.class);
                                baijiahaoDeliveryController.deliverToBaijiahao(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                                // 发送错误消息
                                userInfoRequest.setType("RETURN_BAIJIAHAO_DELIVERY_RES");
                                userInfoRequest.setStatus("error");
                                userInfoRequest.setDraftContent("投递到百家号失败：" + e.getMessage());
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            }
                        }).start();
                    }

                    // 处理获取TT二维码的消息
                    if(message.contains("PLAY_GET_TTH_QRCODE")){
                        new Thread(() -> {
                            try {
                                mediaController.getTTHQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                    // 处理获取TT登录状态的消息
                    if (message.contains("PLAY_CHECK_TTH_LOGIN")) {
                        new Thread(() -> {
                            try {
                                String checkLogin = mediaController.checkTTHLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_TOUTIAO_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                    // 处理包含"微头条排版"的消息
                    if(message.contains("微头条排版")){
                        JSONObject jsonObject = JSONObject.parseObject(message);
                        new Thread(() -> {
                            try {
                                aigcController.sendToTTHByDB(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    Map map = JSONObject.parseObject(message);
                    // 处理包含"微头条发布"的消息
                    if("微头条发布".equals(map.get("type"))){
                        new Thread(() -> {
                            try {
                                tthController.pushToTTH(map);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                }

                /**
                 * 当WebSocket连接关闭时调用
                 */
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("WebSocket connection closed. Reason: " + reason);
                    startReconnectionTask();
                    stopHeartbeatTask();
                }

                /**
                 * 当WebSocket发生错误时调用
                 */
                @Override
                public void onError(Exception ex) {
                    System.out.println("WebSocket error occurred: " + ex.getMessage());
                    startReconnectionTask();
                    stopHeartbeatTask();
                }
            };

            // 连接到WebSocket服务器
            webSocketClient.connect();

        } catch (URISyntaxException e) {
            System.out.println("Invalid WebSocket URI: " + e.getMessage());
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
                System.out.println("发送心跳包：" + pingMessage.toJSONString());
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
                System.out.println("连接失败，请检查主机ID是否已注册...");
                connectToServer();
            } else {
                System.out.println("WebSocket 已连接，不需要重连。");
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
            System.out.println("Message sent: " + message);
        }
    }
}
