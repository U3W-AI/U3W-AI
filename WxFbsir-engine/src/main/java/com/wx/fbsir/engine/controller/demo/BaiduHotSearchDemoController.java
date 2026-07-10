package com.wx.fbsir.engine.controller.demo;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.wx.fbsir.engine.capability.annotation.StreamCapability;
import com.wx.fbsir.engine.capability.base.StreamTaskHelper;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import com.wx.fbsir.engine.playwright.util.ScreenshotUploadClient;
import com.wx.fbsir.engine.playwright.util.ScreenshotUtil;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 百度热搜演示Controller（流式输出完整示例）
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 演示内容
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 本Controller演示框架的完整能力，包括：
 * 
 * 1. ✅ 流式输出 - 使用StreamTaskHelper实现进度推送
 * 2. ✅ Playwright自动化 - 浏览器控制、页面操作、元素定位
 * 3. ✅ 会话管理 - 持久化会话、状态保存、资源管理
 * 4. ✅ 截图上传 - 自动截图、图片上传、URL返回
 * 5. ✅ 数据提取 - 页面元素抓取、结构化数据返回
 * 6. ✅ 异常处理 - 完整的错误处理和资源清理
 * 7. ✅ 进度推送 - 实时推送任务进度到前端
 * 8. ✅ 中间态返回 - 通过sendLog、sendScreenshot推送中间结果
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 业务流程
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 打开百度首页
 * 2. 抓取热搜榜前10条数据（标题、链接、热度标签）
 * 3. 点击第一条热搜
 * 4. 等待页面加载完成
 * 5. 截图并上传，获取图片URL
 * 6. 返回热搜数据和截图URL
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 客户端调用示例
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * ```json
 * {
 *   "type": "BAIDU_HOT_SEARCH_DEMO",
 *   "engineId": "engine-001",
 *   "payload": {
 *     "clickIndex": 0,
 *     "needScreenshot": true
 *   }
 * }
 * ```
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 返回数据格式
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 中间态（TASK_LOG）：
 * ```json
 * {
 *   "type": "TASK_LOG",
 *   "payload": {
 *     "requestId": "xxx",
 *     "message": "正在打开百度首页...",
 *     "timestamp": 1234567890
 *   }
 * }
 * ```
 * 
 * 中间态（TASK_SCREENSHOT）：
 * ```json
 * {
 *   "type": "TASK_SCREENSHOT",
 *   "payload": {
 *     "requestId": "xxx",
 *     "screenshotUrl": "http://xxx.com/image.png",
 *     "description": "点击热搜后的页面截图"
 *   }
 * }
 * ```
 * 
 * 最终结果（TASK_RESULT）：
 * ```json
 * {
 *   "type": "TASK_RESULT",
 *   "payload": {
 *     "requestId": "xxx",
 *     "success": true,
 *     "data": {
 *       "hotSearchList": [
 *         {
 *           "rank": 1,
 *           "title": "总书记始终不变的牵挂",
 *           "url": "https://...",
 *           "tag": "置顶",
 *           "tagType": "top"
 *         }
 *       ],
 *       "clickedItem": {
 *         "title": "总书记始终不变的牵挂",
 *         "url": "https://...",
 *         "screenshotUrl": "http://xxx.com/image.png"
 *       },
 *       "totalCount": 10,
 *       "timestamp": 1234567890
 *     }
 *   }
 * }
 * ```
 *
 * @author wxfbsir
 * @date 2025-12-29
 */
@Controller
public class BaiduHotSearchDemoController extends StreamTaskHelper {

    private static final Logger log = LoggerFactory.getLogger(BaiduHotSearchDemoController.class);

    @Autowired
    private BrowserPoolManager browserPool;
    
    @Autowired
    private ScreenshotUtil screenshotUtil;
    
    @Autowired
    private ScreenshotUploadClient uploadClient;

    /**
     * 处理百度热搜抓取任务（流式返回）
     * 
     * 演示要点：
     * 1. 继承StreamTaskHelper获得流式能力
     * 2. 使用StreamTask管理任务生命周期
     * 3. 通过task.sendLog()推送中间进度
     * 4. 通过task.sendScreenshot()推送截图
     * 5. 通过task.sendSuccess()发送最终结果
     * 6. finally块确保资源清理
     * 
     * @param message 消息对象，包含payload参数
     */
    @StreamCapability(
        type = "BAIDU_HOT_SEARCH_DEMO",
        description = "百度热搜抓取演示（流式输出完整示例）",
        progressInterval = 3000  // 每3秒自动推送一次心跳进度（可选）
    )
    public void handleBaiduHotSearch(EngineMessage message) {
        // ━━━━━━━━━━ 调试代码：验证参数传递 ━━━━━━━━━━
//         System.out.println("完整消息: " + message);
//         System.out.println("Payload内容: " + message.getPayload());
        
        // ━━━━━━━━━━ 步骤1: 提取参数 ━━━━━━━━━━
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        // 业务参数：从payload中提取（带默认值）
        Integer clickIndex = message.getPayloadValue("clickIndex");
        if (clickIndex == null) clickIndex = 0; // 默认点击第一条
        
        Boolean needScreenshot = message.getPayloadValue("needScreenshot");
        if (needScreenshot == null) needScreenshot = true;
        
        log.info("[百度热搜演示] 任务开始 - 用户: {}, 请求: {}, 点击索引: {}", userId, requestId, clickIndex);
        
        // ━━━━━━━━━━ 步骤2: 创建流式任务 ━━━━━━━━━━
        // StreamTask会自动管理：
        // - requestId传递
        // - 消息类型（TASK_LOG、TASK_SCREENSHOT、TASK_RESULT）
        // - 定时心跳（每3秒自动推送进度）
        StreamTask task = startStreamTask(userId, requestId);
        
        BrowserSession session = null;
        
        try {
            // ━━━━━━━━━━ 步骤3: 获取浏览器会话 ━━━━━━━━━━
            task.sendLog("正在启动浏览器...");
            
            // 获取持久化会话（会话数据会保存在磁盘，下次可复用）
            // 
            // 🔥 重要：持久化会话说明
            // - 会话数据保存在：./data/playwright/baidu_demo/{userId}/
            // - 包含：Cookies、LocalStorage、SessionStorage、IndexedDB等
            // - 下次调用时会自动加载这些数据，保持登录状态
            // - 使用完毕后必须调用 session.destroy() 释放文件锁
            // 
            // 参数说明：
            // - userId: 用户ID，用于隔离不同用户的会话数据
            // - "baidu_demo": 会话标识，用于区分不同业务场景（如：baidu、taobao、wechat等）
            // - false: 不使用无头模式（方便调试，生产环境建议改为true）
            session = browserPool.acquirePersistent(userId, "baidu_demo", false);
            Page page = session.getOrCreatePage();
            
            task.sendLog("浏览器启动成功");
            
            // ━━━━━━━━━━ 步骤4: 打开百度首页 ━━━━━━━━━━
            task.sendLog("正在打开百度首页...");
            page.navigate("https://www.baidu.com", new Page.NavigateOptions().setTimeout(15000));
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
            
            task.sendLog("百度首页加载完成");
            
            // ━━━━━━━━━━ 步骤5: 抓取热搜榜数据 ━━━━━━━━━━
            task.sendLog("正在抓取热搜榜数据...");
            
            List<Map<String, Object>> hotSearchList = new ArrayList<>();
            
            // 定位热搜容器
            Locator hotSearchContainer = page.locator("#hotsearch-content-wrapper .hotsearch-item");
            int count = hotSearchContainer.count();
            
            log.info("[百度热搜演示] 找到热搜条目数: {}", count);
            
            // 最多抓取10条
            int maxCount = Math.min(count, 10);
            for (int i = 0; i < maxCount; i++) {
                Locator item = hotSearchContainer.nth(i);
                Locator link = item.locator("a.title-content");
                
                Map<String, Object> hotItem = new HashMap<>();
                hotItem.put("rank", i + 1);
                hotItem.put("title", link.locator(".title-content-title").textContent().trim());
                hotItem.put("url", link.getAttribute("href"));
                
                // 尝试获取标签（热、新等）
                Locator tagLocator = item.locator(".title-content-mark");
                if (tagLocator.count() > 0) {
                    String tag = tagLocator.textContent().trim();
                    hotItem.put("tag", tag);
                    
                    // 根据标签内容判断类型
                    if (tag.contains("热")) {
                        hotItem.put("tagType", "hot");
                    } else if (tag.contains("新")) {
                        hotItem.put("tagType", "new");
                    } else {
                        hotItem.put("tagType", "normal");
                    }
                } else {
                    // 检查是否是置顶（有红色图标）
                    Locator topIcon = item.locator(".title-content-top-icon");
                    if (topIcon.count() > 0 && topIcon.isVisible()) {
                        hotItem.put("tag", "置顶");
                        hotItem.put("tagType", "top");
                    }
                }
                
                hotSearchList.add(hotItem);
            }
            
            task.sendLog(String.format("成功抓取 %d 条热搜数据", hotSearchList.size()));
            
            // ━━━━━━━━━━ 步骤6: 点击指定的热搜 ━━━━━━━━━━
            if (clickIndex >= 0 && clickIndex < hotSearchList.size()) {
                Map<String, Object> targetItem = hotSearchList.get(clickIndex);
                String targetTitle = (String) targetItem.get("title");
                
                task.sendLog(String.format("正在点击第 %d 条热搜: %s", clickIndex + 1, targetTitle));
                
                // 点击热搜链接
                Locator targetLink = hotSearchContainer.nth(clickIndex).locator("a.title-content");
                targetLink.click();
                
                // 等待新页面加载
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));
                page.waitForTimeout(2000); // 额外等待，确保页面渲染完成
                
                task.sendLog("页面加载完成");
                
                // ━━━━━━━━━━ 步骤7: 截图并上传 ━━━━━━━━━━
                if (needScreenshot) {
                    task.sendLog("正在截图...");
                    
                    // 截图到临时文件
                    Path screenshotPath = screenshotUtil.capture(
                        page, 
                        String.format("baidu_hot_%d_%s", clickIndex, requestId)
                    );
                    
                    task.sendLog("截图完成，正在上传...");
                    
                    // 读取截图文件为字节数组
                    byte[] imageBytes = java.nio.file.Files.readAllBytes(screenshotPath);
                    
                    // 上传到图片服务器
                    ScreenshotUploadClient.UploadResult uploadResult = uploadClient.uploadScreenshot(
                        userId, 
                        String.format("baidu_hot_%d", clickIndex), 
                        imageBytes
                    );
                    
                    String screenshotUrl = uploadResult.getUrl();
                    task.sendLog("图片上传成功: " + screenshotUrl);
                    
                    // 🔥 重要：通过StreamTask推送截图（前端会显示在截图轮播区）
                    task.sendScreenshot(screenshotUrl);
                    
                    // 保存到点击的热搜项
                    targetItem.put("screenshotUrl", screenshotUrl);
                }
                
                // 获取当前页面URL
                String currentUrl = page.url();
                targetItem.put("actualUrl", currentUrl);
            }
            
            // ━━━━━━━━━━ 步骤8: 构建返回数据 ━━━━━━━━━━
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("hotSearchList", hotSearchList);
            resultData.put("totalCount", hotSearchList.size());
            resultData.put("clickedIndex", clickIndex);
            
            if (clickIndex >= 0 && clickIndex < hotSearchList.size()) {
                resultData.put("clickedItem", hotSearchList.get(clickIndex));
            }
            
            resultData.put("timestamp", System.currentTimeMillis());
            
            // ━━━━━━━━━━ 步骤9: 发送最终结果 ━━━━━━━━━━
            // 🔥 重要：通过StreamTask发送最终结果
            // - 消息类型自动设置为 TASK_RESULT
            // - success自动设置为true
            // - 包含完整的业务数据
            task.sendSuccess("热搜抓取完成", resultData);
            
            log.info("[百度热搜演示] 任务完成 - 用户: {}, 热搜数: {}", userId, hotSearchList.size());
            
        } catch (Exception e) {
            log.error("[百度热搜演示] 任务失败 - 用户: {}, 请求: {}", userId, requestId, e);
            
            // 🔥 重要：通过StreamTask发送错误结果
            // - 消息类型自动设置为 TASK_RESULT
            // - success自动设置为false
            // - 包含错误信息
            task.sendError("任务执行失败: " + e.getMessage());
            
        } finally {
            // ━━━━━━━━━━ 步骤10: 清理资源 ━━━━━━━━━━
            // 🔥 重要：必须在finally块中清理资源
            
            // 1. 停止StreamTask（停止心跳推送）
            task.stop();
            
            // 2. 释放浏览器会话
            if (session != null) {
                try {
                    // 🔥 重要：完全销毁会话，释放文件锁
                    // 
                    // session.destroy() 会执行以下操作：
                    // 1. 关闭所有Page页面
                    // 2. 关闭BrowserContext上下文
                    // 3. 关闭Browser浏览器实例（如果是独占的）
                    // 4. 释放用户数据目录的文件锁
                    // 5. 从池中移除会话记录
                    // 
                    // ⚠️ 注意：会话数据（Cookies等）已经自动保存到磁盘
                    // 下次调用 acquirePersistent() 时会自动加载
                    browserPool.destroy(session);
                    log.debug("[百度热搜演示] 会话已销毁 - 用户: {}", userId);
                } catch (Exception e) {
                    log.warn("[百度热搜演示] 销毁会话失败: {}", e.getMessage());
                }
            }
        }
    }
}
