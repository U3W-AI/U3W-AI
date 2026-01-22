package com.wx.fbsir.engine.controller.yuanqi;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.wx.fbsir.engine.capability.annotation.StreamCapability;
import com.wx.fbsir.engine.capability.base.StreamTaskHelper;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import com.wx.fbsir.engine.util.BrowserSessionLockUtil;
import com.wx.fbsir.engine.utils.yuanqi.YuanQiLoginUtil;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * 元器（YuanQi）知识库控制器
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 功能概述
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 知识库创建与配置 - 自动创建知识库、导入网页内容、关联智能体
 * 2. 智能体关联 - 将知识库关联到指定智能体并发布
 * 3. 自动化流程 - 完整的知识库配置流程自动化执行
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 消息类型
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - YUANQI_SET_KNOWLEDGE: 扫码登录并配置知识库（流式输出）
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 业务流程
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 扫码登录 → 2. 检查知识库是否存在 → 3. 创建知识库（如不存在）
 * → 4. 导入网页内容 → 5. 关联智能体 → 6. 发布智能体
 * 
 * @author wxfbsir
 * @date 2025-01-06
 */
@Controller
public class YuanQiKnowledgeController extends StreamTaskHelper {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    @Autowired
    private BrowserPoolManager browserPoolManager;
    @Autowired
    private YuanQiLoginUtil loginUtil;
    /**
     * 元器知识库配置（流式返回）
     * 
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 📌 功能说明
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * 完整的知识库配置流程，包括：
     * 1. 自动扫码登录（如果未登录）
     * 2. 检查知识库是否存在，不存在则创建
     * 3. 导入网页内容到知识库
     * 4. 将知识库关联到指定智能体
     * 5. 发布智能体（如果知识库未关联）
     * 
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 📌 请求参数
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * 请求JSON示例：
     * ```json
     * {
     *   "type": "YUANQI_SET_KNOWLEDGE",
     *   "engineId": "engine-001",
     *   "userId": "user-123",
     *   "payload": {
     *     "requestId": "req-001",
     *     "teamName": "个人空间"
     *     "knowledgeBaseName": "招聘简历匹配知识库",
     *     "importWebUrl": "https://example.com/knowledge",
     *     "agentName": "智能助手"
     *   }
     * }
     * ```
     * 
     * 参数说明：
     * | 参数名 | 类型 | 必填 | 说明 | 示例值 |
     * |--------|------|------|------|--------|
     * | `teamName` | String | ✅ | 团队名称 | `"招聘简历匹配知识库"` |
     * | `knowledgeBaseName` | String | ✅ | 知识库名称 | `"招聘简历匹配知识库"` |
     * | `importWebUrl` | String | ✅ | 要导入的网页URL | `"https://example.com/knowledge"` |
     * | `agentName` | String | ✅ | 要关联的智能体名称 | `"智能助手"` |
     * | `requestId` | String | ⭕ | 请求ID（Admin自动生成） | `"req-001"` |
     *
     * 
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 📌 进度推送
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * 流式输出消息类型：
     * - `TASK_LOG`: 执行步骤日志（如"正在创建知识库..."）
     * - `TASK_RESULT`: 最终结果（成功/失败）
     * 
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 📌 返回数据
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * 成功响应：
     * ```json
     * {
     *   "type": "TASK_RESULT",
     *   "userId": "user-123",
     *   "payload": {
     *     "requestId": "req-001",
     *     "success": true,
     *     "data": {
     *       "message": "知识库配置完成",
     *       "knowledgeBaseName": "招聘简历匹配知识库",
     *       "agentName": "智能助手",
     *       "importWebUrl": "https://example.com/knowledge",
     *       "created": true,
     *       "associated": true,
     *       "published": true,
     *       "timestamp": 1736144400000
     *     }
     *   }
     * }
     * ```
     * 
     * 错误响应：
     * ```json
     * {
     *   "type": "TASK_RESULT",
     *   "userId": "user-123",
     *   "payload": {
     *     "requestId": "req-001",
     *     "success": false,
     *     "errorCode": "TASK_ERROR",
     *     "errorMessage": "知识库配置失败: 无法找到智能体",
     *     "timestamp": 1736144400000
     *   }
     * }
     * ```
     * 
     * @param message 消息对象，包含userId和payload参数
     */
    @StreamCapability(
        type = "YUANQI_SET_KNOWLEDGE",
        description = "元器扫码登录并配置知识库",
        progressInterval = 2000  // 推送间隔（毫秒）
    )
    public void yuanQi_addKnowledge(EngineMessage message) {
        //获取用户id
        String userId = message.getUserId();

        String requestId = message.getPayloadValue("requestId");
        //获取团队名称（如果不提供则默认"个人空间"）
        String teamName = message.getPayloadValue("teamName");
        if (teamName == null || teamName.trim().isEmpty()) {
            teamName = "个人空间";
        }
        //获取用户添加的知识库名称
        String knowledgeBaseName = message.getPayloadValue("knowledgeBaseName");
        //获取用户添加的知识库url
        String importWebUrl = message.getPayloadValue("importWebUrl");
        //获取用户添加知识库的智能体元器名称
        String agentName = message.getPayloadValue("agentName");
        
        log.info("[元器知识库配置] 开始 - 用户: {}, 请求: {}, 知识库: {},配置的url：{}, 智能体: {}, 团队名称{}",
            userId, requestId, knowledgeBaseName,importWebUrl, agentName,teamName);
        
        StreamTask task = startStreamTask(userId, requestId, 2000);
        BrowserSession session = null;
        //获取用户会话锁
//        Lock userSessionLock = BrowserSessionLockUtil.getUserSessionLock(userId);
        boolean lockAcquired = false;
        try {
            //尝试获取锁（6分钟超时）
//            lockAcquired = userSessionLock.tryLock(6, TimeUnit.MINUTES);
            if (!lockAcquired) {
                task.sendError("当前有其他任务正在执行，请稍后重试");
                log.warn("[元器知识库配置] 获取用户会话锁超时 - 用户: {}", userId);
                return;
            }
            // 步骤1: 获取持久化浏览器会话
            task.sendLog("正在获取浏览器会话...");
            session = browserPoolManager.acquirePersistent(userId, "yuanqi", false);
            Page page = session.getOrCreatePage();
            
            // 步骤2: 扫码登录（如果未登录）
            task.sendLog("正在检查登录状态，如未登录将自动扫码登录...");
            task.sendLog("正在打开元器登录页面...");


            // 导航到首页
            task.sendLog("正在加载元器首页...");
            boolean navSuccess = loginUtil.navigateToHomePage(page);

            if (!navSuccess) {
                task.sendError("无法加载元器首页，请检查网络连接");
                return;
            }

            // TODO: 扫码登录功能已移除，需要重新实现或使用其他登录方式
            // //扫码登陆
            // Page page_login = loginUtil.scanLogin(page,task,log,userId,requestId);
            // if (page_login == null) {
            //     task.sendError("扫码登陆失败");
            //     return;
            // }else{
            //     page = page_login;
            // }

            task.sendLog("跳过登录检查，开始配置知识库...");
            
            // 步骤3: 进入知识库页面
            try {
                //先指定团队或者个人名称（默认个人空间）
                Locator team = page.locator("svg[data-v-3a6c441f].collapse-icon.v-icon--fill");
                team.click();
                page.waitForTimeout(1000);
                //查看是否有这个团队名称
                Locator teamButton = page.getByText(teamName, new Page.GetByTextOptions().setExact(true)).first();
                if (teamButton.count() == 0) {
                    task.sendError("查找的团队名称不存在");
                    log.error("查找的团队名称不存在");
                    return;
                }else{
                    task.sendLog("进入团队");
                    teamButton.click();
                    page.waitForTimeout(1000);
                }
                task.sendLog("正在进入知识库页面...");
                // 通过完全匹配查找页面中text是"知识库"的元素
                Locator knowledgeBaseButton = page.getByText("知识库", new Page.GetByTextOptions().setExact(true)).first();
                knowledgeBaseButton.click();
                page.waitForTimeout(2000);
                task.sendLog("已进入知识库页面");

                // 步骤4: 检查知识库是否存在
                task.sendLog("正在检查知识库是否存在: " + knowledgeBaseName);
                Locator kbNameInput = page.locator("input.v-input--search__input").first();
                kbNameInput.clear();
                // 向输入框中输入新建知识库的名称
                kbNameInput.fill(knowledgeBaseName);
                //点击搜索按钮
                Locator searchButton =  page.locator("svg.v-input--search__search").first();
                searchButton.click();
                // 等待页面响应（确保输入后元素加载完成）
                page.waitForTimeout(1000);
                // 定位是否存在文本为新建知识库名称的元素（判断知识库是否已存在）
                Locator existKbElement = page.locator(":has-text('" + knowledgeBaseName + "')").first();
                // 初始化标记变量，默认不存在
                boolean isKbExist;
                try {
                    // 检查元素是否可见（可见则说明知识库已存在）
                    
                    isKbExist = existKbElement.isVisible();
                    if (isKbExist) {
                        task.sendLog("知识库已存在，跳过创建步骤");
                    }
                } catch (Exception e) {
                    // 捕获定位失败异常，确认知识库不存在
                    isKbExist = false;
                }
                
                // 步骤5: 创建知识库（如果不存在）
                if (!isKbExist) {
                    task.sendLog("知识库不存在，开始创建新知识库...");
                    
                    // 点击"新建知识库"按钮
                    task.sendLog("点击新建知识库按钮...");
                    Locator newKnowledgeBaseBtn = page.getByText("新建知识库").first();
                    newKnowledgeBaseBtn.click();
                    page.waitForTimeout(1000);
                    
                    // 选择"通用知识库"
                    task.sendLog("选择通用知识库类型...");
                    Locator generalKnowledgeBase = page.getByText("通用知识库").first();
                    generalKnowledgeBase.click();
                    page.waitForTimeout(2000);

                    // 填写知识库名称和描述
                    task.sendLog("填写知识库名称和描述...");
                    Locator nameInput = page.locator("label.v-input--default__placeholder:has-text('请输入名称')")
                            .locator("..")
                            .locator("input.v-input--default__input");
                    // 直接强制执行填充，跳过所有前置校验
                    nameInput.fill(knowledgeBaseName, new Locator.FillOptions()
                            .setForce(true)  // 强制填充，忽略可见性/可编辑性
                            .setTimeout(5000));

                    // 定位描述输入框并输入固定内容
                    Locator descInput = page.locator("label.v-textarea__placeholder:has-text('说明知识库的内容和用途')")
                            .locator("..") // 取placeholder标签的父元素
                            .locator("textarea.v-textarea__txt");
                    // 强制填充描述内容
                    descInput.fill("招聘过程中的简历筛选和人岗匹配知识库", new Locator.FillOptions()
                            .setForce(true)
                            .setTimeout(5000));
                    page.waitForTimeout(1000);
                    
                    // 点击确认按钮
                    task.sendLog("确认创建知识库...");
                    Locator confirmBtn = page.locator("button.v-button--primary.v-button--large[data-v-079738ad]:has-text('确定')");
                    confirmBtn.click(new Locator.ClickOptions()
                            .setForce(true)                // 忽略可见性/稳定性/启用状态
                            .setPosition(10, 10)           // 指定按钮内部坐标点击（避免点击空白处）
                            .setTimeout(5000));            // 缩短超时至5秒
                    page.waitForTimeout(3000);
                    task.sendLog("知识库创建完成");
                }
                
                // 步骤6: 打开知识库详情
                task.sendLog("正在打开知识库详情: " + knowledgeBaseName);
                Locator knowledge = page.getByText(knowledgeBaseName).first();
                knowledge.click();
                page.waitForTimeout(2000);

                // 步骤7: 导入网页内容
                task.sendLog("开始导入网页内容...");
                
                // 点击导入按钮
                task.sendLog("点击导入按钮...");
                Locator importBtn = page.locator("button.filter-btn:has-text('导入')").first();
                importBtn.click();
                page.waitForTimeout(2000);

                // 选择网页文件选项
                task.sendLog("选择网页文件导入方式...");
                Locator webFileOption = page.getByText("网页文件").first();
                webFileOption.click();
                page.waitForTimeout(2000);

                // 输入网页URL并导入
                task.sendLog("输入网页URL: " + importWebUrl);
                Locator urlInput = page.locator("label.v-input--default__placeholder:has-text('请输入网址，http://或https://开头')")
                        .locator("..") // 取placeholder标签的父元素
                        .locator("input.v-input--default__input");
                // 强制填充网址（跳过所有可见性/可交互性校验，适配平台特性）
                urlInput.fill(importWebUrl, new Locator.FillOptions()
                        .setForce(true)        // 忽略可见性/稳定性校验
                        .setTimeout(5000));

                task.sendLog("开始导入网页内容，请稍候...");
                Locator importWebBtn = page.locator("button:has-text('导入网页')").first();
                importWebBtn.click();
                page.waitForTimeout(10000);

                // 等待导入完成并确认
                task.sendLog("等待网页内容导入完成（最长等待120秒）...");
                //给导入的知识库添加标题
                Locator inputBox = page.locator("input.v-input--simpler__input[type='text'][autocomplete='off']").first();
                task.sendLog("等待输入框...");
                inputBox.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE) // 等待按钮可见
                        .setTimeout(300000)); // 自定义超时时间：300s

                page.waitForTimeout(5000);
                task.sendLog("输入知识库名称");
                inputBox.fill(knowledgeBaseName);
                page.waitForTimeout(5000);

                Locator importToDocLibBtn = page.locator("button:has-text('导入到文档库')").first();
                importToDocLibBtn.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE) // 等待按钮可见
                        .setTimeout(60000)); // 自定义超时时间：60s
                importToDocLibBtn.click();
                page.waitForTimeout(2000);
                task.sendLog("网页内容导入完成");
                
                // 步骤8: 关联智能体
                task.sendLog("开始关联智能体: " + agentName);
                // 跳转到智能体界面
                task.sendLog("正在跳转到智能体界面...");
                Locator YuanQiButton;
                if (teamName.equals("个人空间")){
                    YuanQiButton = page.getByText("我的智能体", new Page.GetByTextOptions().setExact(true)).first();
                }else{
                    YuanQiButton = page.getByText("团队智能体", new Page.GetByTextOptions().setExact(true)).first();

                }
                YuanQiButton.click();
                // 等待2秒，确保页面跳转/渲染完成
                page.waitForTimeout(2000);

                // 选择指定智能体
                task.sendLog("正在查找智能体: " + agentName);
                Locator agentElement = page.getByText(agentName,new Page.GetByTextOptions().setExact(true)).first();
                if (agentElement.count() == 0){
                    log.error("未查询到目标智能体");
                    task.sendError("未查询到目标智能体");
                    return;
                }
                agentElement.click();
                page.waitForTimeout(10000); // 智能体加载可能需要较长时间
                task.sendLog("已进入智能体详情页");

                // 步骤9: 检查并关联知识库
                task.sendLog("正在检查知识库关联状态...");
                Locator addBtn = page.locator("a.is-link:has-text('添加')").first();
                addBtn.click();
                page.waitForTimeout(1000);

                // 选中复选框，直接定位接收点击的v-checkbox__inner容器
                Locator checkbox = page.locator(".scroller-item-filename-title:has-text('"+ knowledgeBaseName +"')")
                        .locator("..").locator("..").locator("..") // 保留原有层级定位
                        .locator(".v-checkbox__inner").first(); // 定位实际可点击的复选框内层容器
                //发布后的智能体已经拥有知识库则不需要重新发布
                if (!checkbox.isChecked()){
                    task.sendLog("知识库未关联，开始关联并发布...");
                    // 强制点击可点击区域，跳过事件拦截校验
                    checkbox.click(new Locator.ClickOptions()
                            .setForce(true)                // 忽略事件拦截/可见性校验
                            .setPosition(5, 5)             // 指定容器内坐标点击，确保命中可交互区域
                            .setTimeout(10000));             // 缩短超时至10秒
                    page.waitForTimeout(2000);

                    // 点击确定按钮
                    task.sendLog("确认关联知识库...");
                    Locator okBtn = page.locator("button.v-button--primary.v-button--large:has(div:has-text('确定'))").nth(0);
                    okBtn.click();
                    page.waitForTimeout(2000);

                    // 发布智能体
                    task.sendLog("正在发布智能体...");
                    Locator publishBtn = page.locator("button.v-button--primary:has-text('发布')").first();
                    publishBtn.click();
                    page.waitForTimeout(3000);

                    // 确认发布
                    task.sendLog("确认发布智能体...");
                    Locator publicOk = page.locator("button.v-button--primary.v-button--large:has-text('发布')").first();
                    publicOk.click();
                    page.waitForTimeout(2000);
                    task.sendLog("智能体发布完成");
                } else {
                    task.sendLog("知识库已关联，无需重新发布");
                }
                
                // 步骤10: 完成配置
                task.sendLog("知识库配置完成！");
                
                // 构建返回数据
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("message", "知识库配置完成");
                resultData.put("knowledgeBaseName", knowledgeBaseName);
                resultData.put("agentName", agentName);
                resultData.put("importWebUrl", importWebUrl);
                task.sendSuccess("知识库配置完成！", resultData);
                log.info("[元器知识库配置] 完成 - 用户: {}, 知识库: {}, 智能体: {}", 
                    userId, knowledgeBaseName, agentName);


            } catch (Exception e) {
                log.error("[元器知识库配置] 知识库操作失败 - 用户: {}, 请求: {}", userId, requestId, e);
                task.sendError("知识库操作失败");
            }
        } catch (Exception e) {
            log.error("[元器知识库配置] 执行失败 - 用户: {}, 请求: {}", userId, requestId, e);
            task.sendError("知识库配置失败");
        } finally {
            //释放锁
//            if (lockAcquired) {
//                userSessionLock.unlock();
//            }
            task.stop();
            
            // 确保资源释放
            if (session != null) {
                try {
                    session.destroy();
                    log.debug("[元器知识库配置] 已销毁会话释放资源 - 用户: {}", userId);
                } catch (Exception e) {
                    log.warn("[元器知识库配置] 销毁会话失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                }
            }
        }
    }

}
