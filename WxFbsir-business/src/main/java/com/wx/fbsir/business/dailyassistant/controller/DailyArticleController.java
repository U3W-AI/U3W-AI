package com.wx.fbsir.business.dailyassistant.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wx.fbsir.common.annotation.Anonymous;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.common.utils.poi.ExcelUtil;
import com.wx.fbsir.business.dailyassistant.domain.DailyArticle;
import com.wx.fbsir.business.dailyassistant.service.IDailyArticleService;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 日更助手文章Controller
 *
 * @author wxfbsir
 * @date 2025-12-05
 */
@RestController
@RequestMapping("/system/daily-article")
public class DailyArticleController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(DailyArticleController.class);

    @Autowired
    private IDailyArticleService dailyArticleService;

    @Autowired
    private com.wx.fbsir.business.point.service.PointsPrecheckService pointsPrecheckService;

    // TODO: 如需集成微信公众号功能，需要添加以下依赖
    // @Autowired
    // private WechatMpService wechatMpService;
    // @Autowired
    // private UserInfoMapper userInfoMapper;

    /**
     * 查询日更助手文章列表
     */
    @PreAuthorize("@ss.hasPermi('business:daily:query')")
    @GetMapping("/list")
    public TableDataInfo list(DailyArticle dailyArticle)
    {
        startPage();
        List<DailyArticle> list = dailyArticleService.selectDailyArticleList(dailyArticle);
        return getDataTable(list);
    }

    /**
     * 查询当前用户的日更助手文章列表（支持分页）
     */
    @PreAuthorize("@ss.hasPermi('business:daily:query')")
    @GetMapping("/myList")
    public TableDataInfo myList(DailyArticle dailyArticle)
    {
        Long userId = getUserId();
        dailyArticle.setUserId(userId);
        startPage();
        List<DailyArticle> list = dailyArticleService.selectDailyArticleList(dailyArticle);
        return getDataTable(list);
    }

    /**
     * 导出日更助手文章列表
     */
    @Log(title = "日更助手文章", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, DailyArticle dailyArticle)
    {
        List<DailyArticle> list = dailyArticleService.selectDailyArticleList(dailyArticle);
        ExcelUtil<DailyArticle> util = new ExcelUtil<DailyArticle>(DailyArticle.class);
        util.exportExcel(response, list, "日更助手文章数据");
    }

    /**
     * 获取日更助手文章详细信息
     */
    @PreAuthorize("@ss.hasPermi('business:daily:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(dailyArticleService.selectDailyArticleById(id));
    }

    /**
     * 查询文章处理状态（用于前端轮询）
     * 返回轻量级状态信息，不包含大字段内容
     */
    @GetMapping("/status/{id}")
    public AjaxResult getArticleStatus(@PathVariable("id") Long id)
    {
        DailyArticle article = dailyArticleService.selectDailyArticleById(id);
        if (article == null) {
            return error("文章不存在");
        }
        
        // 只返回状态相关字段，减少数据传输
        Map<String, Object> status = new HashMap<>();
        status.put("id", article.getId());
        status.put("articleTitle", article.getArticleTitle());
        status.put("processStatus", article.getProcessStatus());  // 0-处理中, 1-成功, 2-失败
        status.put("errorMessage", article.getErrorMessage());
        status.put("agentTaskId", article.getAgentTaskId());
        
        // 只有成功时才返回内容长度信息
        if (article.getProcessStatus() == 1) {
            status.put("hasOptimizedContent", article.getOptimizedContent() != null);
            status.put("hasModel1Content", article.getModel1Content() != null);
            status.put("hasModel2Content", article.getModel2Content() != null);
            status.put("hasModel3Content", article.getModel3Content() != null);
        }
        
        return success(status);
    }

    /**
     * 新增日更助手文章
     */
    @PreAuthorize("@ss.hasPermi('business:daily:add')")
    @Log(title = "日更助手文章", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody DailyArticle dailyArticle)
    {
        dailyArticle.setCreateBy(getUsername());
        return toAjax(dailyArticleService.insertDailyArticle(dailyArticle));
    }

    /**
     * 创建文章并调用智能体优化（异步处理）
     * 
     * 返回后文章处理状态为 0（处理中），前端需要轮询 /status/{id} 接口查询处理结果
     */
    @PreAuthorize("@ss.hasPermi('business:daily:add')")
    @Log(title = "日更助手-创建优化文章", businessType = BusinessType.INSERT)
    @PostMapping("/createAndOptimize")
    public AjaxResult createAndOptimize(@RequestBody Map<String, String> params)
    {
        String articleTitle = params.get("articleTitle");
        if (articleTitle == null || articleTitle.trim().isEmpty()) {
            return error("文章标题不能为空");
        }

        String selectedModels = params.get("selectedModels");
        if (selectedModels == null || selectedModels.trim().isEmpty()) {
            selectedModels = "1,2,3"; // 默认全选
        }

        Long userId = getUserId();

         // ========== 积分前置校验 ==========
        // 使用积分前置校验服务，在业务执行前进行积分校验和扣减
        // 如果积分不足或规则校验失败，直接返回错误，不执行后续业务
        com.wx.fbsir.business.point.domain.PointsResult pointsResult =
            pointsPrecheckService.tryChangePoints(userId, "USE_DAILY_ASSISTANT", null);

        if (!pointsResult.isSuccess()) {
            // 积分校验失败，直接拦截，不执行文章生成业务
            return AjaxResult.error(pointsResult.getMsg());
        }
        // ========== 积分校验通过，继续执行业务 ==========

        // 1. 快速创建文章记录（同步，< 100ms）

        DailyArticle article = dailyArticleService.createArticleAndOptimize(userId, articleTitle, selectedModels);
        
        // 2. 触发异步优化任务（从Controller调用确保@Async生效）
        dailyArticleService.triggerArticleOptimization(article.getId(), userId, articleTitle);

        // 3. 立即返回（不等待AI生成结果）
        Map<String, Object> result = new HashMap<>();
        result.put("id", article.getId());
        result.put("articleTitle", article.getArticleTitle());
        result.put("processStatus", article.getProcessStatus());
        result.put("message", "文章创建成功，AI内容正在生成中，请稍后查询处理状态");
        result.put("pointsBalance", pointsResult.getBalanceAfter()); // 返回扣减后的积分余额

        return success(result);
    }

    /**
     * 保存大模型生成的文章内容
     * 注意：此接口供工作流回调使用，无需用户认证，因此不记录操作日志
     */
    @Anonymous
    @PostMapping("/saveModelContent")
    public AjaxResult saveModelContent(@RequestBody Map<String, Object> params)
    {
        try {
            // 参数校验
            if (params.get("articleId") == null) {
                return error("缺少参数: articleId");
            }
            if (params.get("modelName") == null) {
                return error("缺少参数: modelName");
            }
            if (params.get("content") == null) {
                return error("缺少参数: content");
            }
            if (params.get("modelIndex") == null) {
                return error("缺少参数: modelIndex");
            }
            
            Long articleId = Long.parseLong(params.get("articleId").toString());
            String modelName = params.get("modelName").toString();
            String content = params.get("content").toString();
            int modelIndex = Integer.parseInt(params.get("modelIndex").toString());

            logger.info("保存模型{}内容 - 文章ID: {}", modelIndex, articleId);

            int result = dailyArticleService.saveModelContent(articleId, modelName, content, modelIndex);
            return toAjax(result);
            
        } catch (NumberFormatException e) {
            logger.error("参数格式错误", e);
            return error("参数格式错误: " + e.getMessage());
        } catch (Exception e) {
            logger.error("保存模型内容异常", e);
            return error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 更新优化后的文章内容
     * 注意：此接口供工作流回调使用，无需用户认证，因此不记录操作日志
     */
    @Anonymous
    @PostMapping("/updateOptimizedContent")
    public AjaxResult updateOptimizedContent(@RequestBody Map<String, Object> params)
    {
        try {
            // 参数校验
            if (params.get("articleId") == null) {
                return error("缺少参数: articleId");
            }
            if (params.get("optimizedContent") == null) {
                return error("缺少参数: optimizedContent");
            }
            
            Long articleId = Long.parseLong(params.get("articleId").toString());
            String optimizedContent = params.get("optimizedContent").toString();

            int result = dailyArticleService.updateOptimizedContent(articleId, optimizedContent);
            
            // 显式更新状态为已完成
            if (result > 0) {
                dailyArticleService.updateProcessStatus(articleId, 1);
                logger.info("文章ID: {} 已完成优化", articleId);
            }

            return toAjax(result);
            
        } catch (NumberFormatException e) {
            logger.error("参数格式错误", e);
            return error("参数格式错误: " + e.getMessage());
        } catch (Exception e) {
            logger.error("更新优化内容异常", e);
            return error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 修改日更助手文章
     */
    @Log(title = "日更助手文章", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody DailyArticle dailyArticle)
    {
        dailyArticle.setUpdateBy(getUsername());
        return toAjax(dailyArticleService.updateDailyArticle(dailyArticle));
    }

    /**
     * 删除日更助手文章
     */
    @PreAuthorize("@ss.hasPermi('business:daily:remove')")
    @Log(title = "日更助手文章", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(dailyArticleService.deleteDailyArticleByIds(ids));
    }

    /**
     * 测试接口 - 检查后端是否正常
     */
    @GetMapping("/test")
    public AjaxResult test()
    {
        log.info("=== 测试接口被调用 ===");
        return success("后端正常运行");
    }

    /**
     * 调用元器工作流对文章内容进行智能排版（同步返回结果）
     * 排版结果不保存到数据库，仅返回给前端显示
     * 
     * @param params 请求参数（包含content）
     * @return 排版后的内容
     */
    @PreAuthorize("@ss.hasPermi('business:daily:layout')")
    @Log(title = "日更助手-智能排版", businessType = BusinessType.OTHER)
    @PostMapping("/layoutArticle")
    public AjaxResult layoutArticle(@RequestBody Map<String, Object> params)
    {
        log.debug("[排版] 收到请求 - 内容长度: {}", params.containsKey("content") ? params.get("content").toString().length() : 0);
        
        try {
            // 1. 获取参数
            String content = params.get("content").toString();

            if (content == null || content.trim().isEmpty()) {
                log.warn("[排版] 内容为空");
                return error("排版内容不能为空");
            }

            log.debug("[排版] 开始处理 - 内容长度: {}", content.length());

            // 2. 获取用户ID并进行积分前置校验
            Long userId = getUserId();
            
            // 使用积分前置校验服务，在业务执行前进行积分校验和扣减
            // 如果积分不足或规则校验失败，直接返回错误，不执行后续业务
            com.wx.fbsir.business.point.domain.PointsResult pointsResult = 
                pointsPrecheckService.tryChangePoints(userId, "ARTICLE_LAYOUT", null);

            if (!pointsResult.isSuccess()) {
                // 积分校验失败，直接拦截，不执行排版业务
                return AjaxResult.error(pointsResult.getMsg());
            }
            // ========== 积分校验通过，继续执行业务 ==========

            // 3. 调用Service进行排版（同步返回排版结果，不保存到数据库）
            String layoutedContent = dailyArticleService.layoutArticleSync(userId, content);

            log.info("[排版] 完成 - 输入: {} 字符, 输出: {} 字符", content.length(), layoutedContent.length());
            log.trace("[排版] HTML前300字符: {}", layoutedContent.length() > 300 ? layoutedContent.substring(0, 300) : layoutedContent);

            // 4. 返回排版后的内容和积分信息
            Map<String, Object> result = new HashMap<>();
            result.put("layoutedContent", layoutedContent);
            result.put("message", "排版完成");
            result.put("pointsBalance", pointsResult.getBalanceAfter()); // 返回扣减后的积分余额

            return success(result);

        } catch (Exception e) {
            log.error("[排版] 失败: {}", e.getMessage());
            return error("排版失败：" + e.getMessage());
        }
    }

    /**
     * 投递文章到微信公众号草稿箱
     * 
     * 注意：此接口已废弃，请使用新的公众号投递接口：
     * POST /officialaccount/publish/{articleId}/{contentType}
     * 
     * @param params 请求参数（articleId, contentType, layoutedContent）
     * @return 投递结果
     * @deprecated 请使用 WcOfficeAccountController.publishToWechat 接口
     */
    @Deprecated
    @PreAuthorize("@ss.hasPermi('business:daily:publish')")
    @Log(title = "日更助手-投递公众号", businessType = BusinessType.OTHER)
    @PostMapping("/publishToWechat")
    public AjaxResult publishToWechat(@RequestBody Map<String, Object> params)
    {
        return error("此接口已废弃，请使用新的公众号投递接口：POST /officialaccount/publish/{articleId}/{contentType}");
        
        /* 旧实现代码（已废弃）：
        try {
            Long articleId = Long.parseLong(params.get("articleId").toString());
            String contentType = params.get("contentType").toString();

            if (!"layout".equals(contentType)) {
                return error("仅支持投递排版后的文章");
            }

            DailyArticle article = dailyArticleService.selectDailyArticleById(articleId);
            if (article == null) {
                return error("文章不存在");
            }

            Long userId = getUserId();
            String unionId = userInfoMapper.getUnionIdByUserId(String.valueOf(userId));
            if (unionId == null || unionId.trim().isEmpty()) {
                return error("未找到用户信息，请先登录微信");
            }

            Object layoutedContentObj = params.get("layoutedContent");
            if (layoutedContentObj == null) {
                return error("排版内容为空，无法投递");
            }
            String originalContent = layoutedContentObj.toString();
            
            String baseTitle = article.getArticleTitle();
            String extractedTitle = extractTitleFromLayoutContent(originalContent);
            String finalTitle = (extractedTitle != null && !extractedTitle.trim().isEmpty())
                    ? extractedTitle.trim()
                    : baseTitle;
            
            String content = removeTitleFromContent(originalContent);
            content = convertTextToHtml(content);

            if (content == null || content.trim().isEmpty()) {
                return error("内容为空，无法投递");
            }

            Map<String, Object> publishParams = new HashMap<>();
            publishParams.put("unionId", unionId);
            publishParams.put("title", finalTitle);
            publishParams.put("contentText", content);
            publishParams.put("shareUrl", "");
            publishParams.put("thumbMediaId", null);

            ResultBody result = wechatMpService.publishToOffice(publishParams);

            if (result.getCode() == 200) {
                Integer publishCount = article.getPublishCount();
                if (publishCount == null) {
                    publishCount = 0;
                }
                article.setPublishCount(publishCount + 1);
                dailyArticleService.updateDailyArticle(article);

                return success(result.getData());
            } else {
                return error(result.getMessages());
            }

        } catch (Exception e) {
            log.error("投递文章到公众号失败", e);
            return error("投递失败：" + e.getMessage());
        }
        */
    }
}
