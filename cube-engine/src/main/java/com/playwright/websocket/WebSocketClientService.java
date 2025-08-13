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
import com.playwright.utils.BrowserConcurrencyManager;
import com.playwright.utils.BrowserTaskWrapper;
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
                    BrowserController browserController = SpringContextUtils.getBean(BrowserController.class);
                    AIGCController aigcController = SpringContextUtils.getBean(AIGCController.class);
                    UserInfoRequest userInfoRequest = JSONObject.parseObject(message, UserInfoRequest.class);
                    TTHController tthController = SpringContextUtils.getBean(TTHController.class);
                    MediaController mediaController = SpringContextUtils.getBean(MediaController.class);
                    BrowserConcurrencyManager concurrencyManager = SpringContextUtils.getBean(BrowserConcurrencyManager.class);
                    BrowserTaskWrapper taskWrapper = SpringContextUtils.getBean(BrowserTaskWrapper.class);
                    
                    // 打印当前并发状态
                    taskWrapper.printStatus();

                    // 处理包含"使用F8S"的消息
                    if(message.contains("使用F8S")){
                        // 处理包含"cube"的消息
                        if(message.contains("cube")){
                            concurrencyManager.submitBrowserTask(() -> {
                                try {
                                    aigcController.startAgent(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }, "元器智能体", userInfoRequest.getUserId());
                        }
                        // 处理包含"mini-max"的消息
                        if(message.contains("mini-max")){
                            concurrencyManager.submitBrowserTask(() -> {
                                try {
                                    aigcController.startMiniMax(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }, "MiniMax智能体", userInfoRequest.getUserId());
                        }
                        // 处理包含"metaso"的消息
                        if(message.contains("mita")){
                            concurrencyManager.submitBrowserTask(() -> {
                                try {
                                    aigcController.startMetaso(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }, "Metaso智能体", userInfoRequest.getUserId());
                        }
                        // 处理包含"yb-hunyuan"或"yb-deepseek"的消息
                        if(message.contains("yb-hunyuan-pt") || message.contains("yb-deepseek-pt")){
                            concurrencyManager.submitBrowserTask(() -> {
                                try {
                                    aigcController.startYB(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }, "元包智能体", userInfoRequest.getUserId());
                        }
                        // 处理包含"zj-db"的消息
                        if(message.contains("zj-db")){
                            concurrencyManager.submitBrowserTaskWithDeduplication(() -> {
                                try {
                                    aigcController.startDB(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }, "豆包智能体", userInfoRequest.getUserId(), 5, userInfoRequest.getUserPrompt());
                        }
                        // 处理包含"deepseek"的消息
                        if(message.contains("deepseek,")){
                            concurrencyManager.submitBrowserTaskWithDeduplication(() -> {
                                try {
                                    aigcController.startDeepSeek(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }, "DeepSeek智能体", userInfoRequest.getUserId(), 5, userInfoRequest.getUserPrompt());
                        }

                        // 处理包含"ty-qw"的信息
                        if (message.contains("ty-qw")){
                            concurrencyManager.submitBrowserTaskWithDeduplication(() -> {
                                try {
                                    aigcController.startTYQianwen(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }, "通义千问", userInfoRequest.getUserId(), 5, userInfoRequest.getUserPrompt());
                        }

                        // 处理Kimi相关的消息 - 扩展检测逻辑
                        String roles = userInfoRequest.getRoles();
                        if(message.contains("kimi-talk") || 
                           (roles != null && (roles.contains("kimi") || 
                                            roles.contains("KIMI") || 
                                            roles.contains("kimi-lwss") ||
                                            roles.contains("kimi-agent")))){
                            concurrencyManager.submitBrowserTaskWithDeduplication(() -> {
                                try {
                                    aigcController.startKimi(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }, "Kimi智能体", userInfoRequest.getUserId(), 5, userInfoRequest.getUserPrompt());
                        }
                        
                        // 新增：通用角色检测，支持更多AI服务的自动识别
                        if (roles != null && !roles.isEmpty()) {
                            // 检查是否包含其他AI服务标识但没有被上面的条件捕获
                            String[] roleArray = roles.split(",");
                            for (String role : roleArray) {
                                role = role.trim().toLowerCase();
                                
                                // ChatGPT相关
                                if (role.contains("gpt") || role.contains("openai")) {
                                    // 如果有ChatGPT相关的实现，可以在这里添加
                                }
                                
                                // Claude相关
                                if (role.contains("claude") || role.contains("anthropic")) {
                                    // 如果有Claude相关的实现，可以在这里添加
                                }
                                
                                // Gemini相关
                                if (role.contains("gemini") || role.contains("bard")) {
                                    // 如果有Gemini相关的实现，可以在这里添加
                                }
                            }
                        }

                        // 处理包含"baidu-agent"的消息
                        if(userInfoRequest.getRoles() != null && userInfoRequest.getRoles().contains("baidu-agent")){
                            concurrencyManager.submitBrowserTask(() -> {
                                try {
                                    aigcController.startBaidu(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }, "百度AI", userInfoRequest.getUserId());
                        }

                        if (message.contains("zhzd-chat")) {
                            // 使用带去重功能的任务提交，防止重复调用
                            concurrencyManager.submitBrowserTaskWithDeduplication(() -> {
                                try {
                                    aigcController.startZHZD(userInfoRequest);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }, "智谱AI", userInfoRequest.getUserId(), 5, userInfoRequest.getUserPrompt());
                        }
                    }

                    // 处理包含"AI评分"的消息
                    if(message.contains("AI评分")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                aigcController.startDBScore(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "AI评分", userInfoRequest.getUserId());
                    }

                    // 处理包含"START_AGENT"的消息
                    if(message.contains("START_AGENT")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                aigcController.startAgent(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "启动智能体", userInfoRequest.getUserId());
                    }
                    
                    // 处理包含"START_YB"的消息
                    if(message.contains("START_YB")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                aigcController.startYB(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "启动元包", userInfoRequest.getUserId());
                    }
                    
                    // 处理包含"AI排版"的消息
                    if(message.contains("AI排版")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                aigcController.startDBOffice(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "AI排版", userInfoRequest.getUserId());
                    }

                    // 处理包含"START_DEEPSEEK"的消息
                    if(message.contains("START_DEEPSEEK")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                aigcController.startDeepSeek(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "启动DeepSeek", userInfoRequest.getUserId());
                    }

                    // 处理获取通义千问二维码的消息
                    if(message.contains("PLAY_GET_QW_QRCODE")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getTongYiQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取通义千问二维码", userInfoRequest.getUserId());
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

                    // 处理获取百度AI二维码的消息
                    if(message.contains("PLAY_GET_BAIDU_QRCODE")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getBaiduQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取百度AI二维码", userInfoRequest.getUserId());
                    }
                    
                    // 处理获取yb二维码的消息
                    if(message.contains("PLAY_GET_YB_QRCODE")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getYBQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取元包二维码", userInfoRequest.getUserId());
                    }

                    // 处理检查yb登录状态的消息
                    if (message.contains("CHECK_YB_LOGIN")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                String checkLogin = browserController.checkLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_YB_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "元包登录检查", userInfoRequest.getUserId());
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

                    // 处理检查MiniMax登录状态的信息
                    if (message.contains("CHECK_MAX_LOGIN")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                String checkLogin = browserController.checkMaxLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_MAX_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "MiniMax登录检查", userInfoRequest.getUserId());
                    }
                    
                    // 处理获取MiniMax二维码的消息
                    if(message.contains("PLAY_GET_MAX_QRCODE")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getMaxQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取MiniMax二维码", userInfoRequest.getUserId());
                    }

                    // 处理检查Kimi登录状态的信息
                    if (message.contains("CHECK_KIMI_LOGIN")) {
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                String checkLogin = browserController.checkKimiLogin(userInfoRequest.getUserId());
                                userInfoRequest.setStatus(checkLogin);
                                userInfoRequest.setType("RETURN_KIMI_STATUS");
                                sendMessage(JSON.toJSONString(userInfoRequest));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "Kimi登录检查", userInfoRequest.getUserId());
                    }
                    
                    // 处理获取KiMi二维码的消息
                    if(message.contains("PLAY_GET_KIMI_QRCODE")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getKiMiQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取Kimi二维码", userInfoRequest.getUserId());
                    }

                    // 处理获取数据库二维码的消息
                    if(message.contains("PLAY_GET_DB_QRCODE")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getDBQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取豆包二维码", userInfoRequest.getUserId());
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
                    if(message.contains("PLAY_GET_METASO_QRCODE")){
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
                        }, "DeepSeek登录检查", userInfoRequest.getUserId());
                    }

                    // 处理获取DeepSeek二维码的消息
                    if(message.contains("PLAY_GET_DEEPSEEK_QRCODE")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                browserController.getDeepSeekQrCode(userInfoRequest.getUserId());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "获取DeepSeek二维码", userInfoRequest.getUserId());
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

                    // 处理知乎投递的消息
                    if(message.contains("投递到知乎")){
                        concurrencyManager.submitBrowserTask(() -> {
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
                        }, "知乎投递", userInfoRequest.getUserId());
                    }

                    // 处理百家号投递的消息
                    if(message.contains("投递到百家号")){
                        concurrencyManager.submitBrowserTask(() -> {
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
                        }, "百家号投递", userInfoRequest.getUserId());
                    }

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
                    
                    // 处理包含"微头条排版"的消息
                    if(message.contains("微头条排版")){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                aigcController.sendToTTHByDB(userInfoRequest);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "微头条排版", userInfoRequest.getUserId());
                    }

                    Map map = JSONObject.parseObject(message);
                    // 处理包含"微头条发布"的消息
                    if("微头条发布".equals(map.get("type"))){
                        concurrencyManager.submitBrowserTask(() -> {
                            try {
                                tthController.pushToTTH(map);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, "微头条发布", userInfoRequest.getUserId());
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
}
