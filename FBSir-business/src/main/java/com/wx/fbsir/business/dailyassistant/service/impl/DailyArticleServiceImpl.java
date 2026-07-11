package com.wx.fbsir.business.dailyassistant.service.impl;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.wx.fbsir.business.dailyassistant.domain.DailyArticle;
import com.wx.fbsir.business.dailyassistant.domain.YuanqiAgentConfig;
import com.wx.fbsir.business.dailyassistant.mapper.DailyArticleMapper;
import com.wx.fbsir.business.dailyassistant.service.IDailyArticleService;
import com.wx.fbsir.business.dailyassistant.service.IYuanqiAgentConfigService;
import com.wx.fbsir.business.dailyassistant.service.impl.YuanqiAgentApiService.YuanqiApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日更助手文章Service业务层处理
 *
 * @author FBSir
 * @date 2025-12-05
 */
@Service
public class DailyArticleServiceImpl implements IDailyArticleService
{
    private static final Logger log = LoggerFactory.getLogger(DailyArticleServiceImpl.class);
    
    // 定时器：用于检查回调超时
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Autowired
    private DailyArticleMapper dailyArticleMapper;

    @Autowired
    private IYuanqiAgentConfigService yuanqiAgentConfigService;

    @Autowired
    private YuanqiAgentApiService yuanqiAgentApiService;

    /**
     * 查询日更助手文章
     *
     * @param id 日更助手文章ID
     * @return 日更助手文章
     */
    @Override
    public DailyArticle selectDailyArticleById(Long id)
    {
        return dailyArticleMapper.selectDailyArticleById(id);
    }

    /**
     * 查询日更助手文章列表
     *
     * @param dailyArticle 日更助手文章
     * @return 日更助手文章
     */
    @Override
    public List<DailyArticle> selectDailyArticleList(DailyArticle dailyArticle)
    {
        return dailyArticleMapper.selectDailyArticleList(dailyArticle);
    }

    /**
     * 根据用户ID查询日更助手文章列表
     *
     * @param userId 用户ID
     * @return 日更助手文章集合
     */
    @Override
    public List<DailyArticle> selectDailyArticleListByUserId(Long userId)
    {
        return dailyArticleMapper.selectDailyArticleListByUserId(userId);
    }

    /**
     * 新增日更助手文章
     *
     * @param dailyArticle 日更助手文章
     * @return 结果
     */
    @Override
    public int insertDailyArticle(DailyArticle dailyArticle)
    {
        return dailyArticleMapper.insertDailyArticle(dailyArticle);
    }

    /**
     * 修改日更助手文章
     *
     * @param dailyArticle 日更助手文章
     * @return 结果
     */
    @Override
    public int updateDailyArticle(DailyArticle dailyArticle)
    {
        return dailyArticleMapper.updateDailyArticle(dailyArticle);
    }

    /**
     * 批量删除日更助手文章
     *
     * @param ids 需要删除的日更助手文章ID
     * @return 结果
     */
    @Override
    public int deleteDailyArticleByIds(Long[] ids)
    {
        return dailyArticleMapper.deleteDailyArticleByIds(ids);
    }

    /**
     * 删除日更助手文章信息
     *
     * @param id 日更助手文章ID
     * @return 结果
     */
    @Override
    public int deleteDailyArticleById(Long id)
    {
        return dailyArticleMapper.deleteDailyArticleById(id);
    }

    /**
     * 创建文章（仅创建记录，不触发优化）
     * 
     * 注意：优化任务需要在Controller层调用 triggerArticleOptimization 方法触发
     *      这样可以确保异步执行（避免同类调用导致@Async失效）
     */
    @Override
    public DailyArticle createArticleAndOptimize(Long userId, String articleTitle, String selectedModels)
    {
        // 1. 快速创建新文章记录
        DailyArticle article = new DailyArticle();
        article.setUserId(userId);
        article.setArticleTitle(articleTitle);
        article.setSelectedModels(selectedModels);
        article.setProcessStatus(0); // 处理中

        // 2. 立即保存到数据库
        dailyArticleMapper.insertDailyArticle(article);
        
        log.info("文章创建成功 - 文章ID: {}, 标题: {}, 等待Controller触发异步优化", article.getId(), articleTitle);

        // 3. 返回文章（Controller层会调用异步方法）
        return article;
    }
    
    /**
     * 异步处理文章优化
     * 该方法在后台线程池中执行，不会阻塞主线程
     * 
     * 重要：此方法必须由外部类调用才能确保@Async生效
     *      如果在同一个类内部调用（this.method()），Spring AOP代理不会介入，会导致同步执行
     * 
     * @param articleId 文章ID
     * @param userId 用户ID
     * @param articleTitle 文章标题
     */
    @Async("asyncTaskExecutor")
    @Override
    public void triggerArticleOptimization(Long articleId, Long userId, String articleTitle)
    {
        log.info("[异步任务] 开始处理文章优化 - 文章ID: {}", articleId);
        
        try {
            // 获取用户的腾讯元器智能体配置（解密后的真实配置）
            YuanqiAgentConfig config = yuanqiAgentConfigService.selectActiveConfigByUserIdDecrypted(userId, "daily_assistant");
            
            if (config == null) {
                log.error("[异步任务] 未找到智能体配置 - 文章ID: {}", articleId);
                updateArticleStatus(articleId, 2, "未找到启用的腾讯元器智能体配置", null, null);
                return;
            }

            // 调用腾讯元器智能体API（工作流）
            String appKey = config.getApiKey();     // API密钥（appkey）
            String appId = config.getAgentId();     // 智能体ID（appid）
            String userIdStr = String.valueOf(userId);
            
            log.info("[异步任务] 开始优化文章 - ID: {}, 标题: {}", articleId, articleTitle);
            
            // 调用API服务（异步工作流，只提交任务，不等待内容）
            YuanqiApiResponse response = yuanqiAgentApiService.optimizeArticle(
                appKey, 
                appId, 
                userIdStr, 
                articleTitle,
                articleId
            );
            
            if (response.isSuccess()) {
                // 工作流任务已提交成功，记录taskId
                // 注意：内容不从这里获取，而是通过工作流的HTTP回调接口传递
                //      - /saveModelContent 保存各模型生成的内容
                //      - /updateOptimizedContent 保存最终优化内容
                
                // 检查文章是否已经通过回调完成（避免竞态条件）
                DailyArticle currentArticle = dailyArticleMapper.selectDailyArticleById(articleId);
                if (currentArticle != null && currentArticle.getProcessStatus() == 1) {
                    // 文章已经通过回调完成，不再更新状态
                    log.info("[异步任务] 工作流任务提交成功，但文章已通过回调完成 - 文章ID: {}, TaskId: {}, 跳过状态更新", 
                            articleId, response.getTaskId());
                } else {
                    // 文章还未完成，更新taskId并保持处理中状态
                    updateArticleStatus(articleId, 0, null, null, response.getTaskId());  // status仍为0（处理中）
                    
                    log.info("[异步任务] 工作流任务提交成功 - 文章ID: {}, TaskId: {}, 等待回调传递结果", 
                            articleId, response.getTaskId());
                    
                    // 启动5分钟超时检查：如果5分钟后还没收到回调，设为失败
                    scheduleCallbackTimeoutCheck(articleId, articleTitle);
                }
            } else {
                // 任务提交失败
                updateArticleStatus(articleId, 2, response.getErrorMessage(), null, null);
                
                log.error("[异步任务] 工作流任务提交失败 - 文章ID: {}, 错误: {}", articleId, response.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("[异步任务] 调用腾讯元器智能体异常 - 文章ID: {}", articleId, e);
            updateArticleStatus(articleId, 2, "调用智能体异常: " + e.getMessage(), null, null);
        }
    }
    
    /**
     * 更新文章处理状态
     */
    private void updateArticleStatus(Long articleId, int status, String errorMessage, 
                                     String optimizedContent, String taskId)
    {
        DailyArticle article = new DailyArticle();
        article.setId(articleId);
        article.setProcessStatus(status);
        
        if (errorMessage != null) {
            article.setErrorMessage(errorMessage);
        }
        
        if (optimizedContent != null) {
            article.setOptimizedContent(optimizedContent);
        }
        
        if (taskId != null) {
            article.setAgentTaskId(taskId);
        }
        
        dailyArticleMapper.updateDailyArticle(article);
    }

    /**
     * 保存大模型生成的未优化文章内容
     *
     * @param articleId 文章ID
     * @param modelName 模型名称
     * @param content 文章内容
     * @param modelIndex 模型索引（1-3）
     * @return 结果
     */
    @Override
    public int saveModelContent(Long articleId, String modelName, String content, int modelIndex)
    {
        // 首先查询文章，检查该模型是否被选中
        DailyArticle existingArticle = dailyArticleMapper.selectDailyArticleById(articleId);
        if (existingArticle == null) {
            log.warn("文章不存在 - articleId: {}", articleId);
            return 0;
        }
        
        // 检查该模型是否被选中
        String selectedModels = existingArticle.getSelectedModels();
        if (selectedModels == null || !selectedModels.contains(String.valueOf(modelIndex))) {
            return 0; // 模型未被选中，不保存
        }
        
        DailyArticle article = new DailyArticle();
        article.setId(articleId);
        
        switch (modelIndex) {
            case 1:
                article.setModel1Name(modelName);
                article.setModel1Content(content);
                break;
            case 2:
                article.setModel2Name(modelName);
                article.setModel2Content(content);
                break;
            case 3:
                article.setModel3Name(modelName);
                article.setModel3Content(content);
                break;
            default:
                throw new IllegalArgumentException("模型索引必须在1-3之间");
        }
        
        return dailyArticleMapper.updateModelContent(article);
    }

    /**
     * 更新优化后的文章内容
     * 注意：此方法会同时将文章状态更新为1（已完成）
     *
     * @param articleId 文章ID
     * @param optimizedContent 优化后的内容
     * @return 结果
     */
    @Override
    public int updateOptimizedContent(Long articleId, String optimizedContent)
    {
        // updateOptimizedContent 的 SQL 会同时将 process_status 设置为 1
        return dailyArticleMapper.updateOptimizedContent(articleId, optimizedContent);
    }

    /**
     * 更新文章处理状态
     *
     * @param articleId 文章ID
     * @param processStatus 处理状态
     * @return 结果
     */
    @Override
    public int updateProcessStatus(Long articleId, Integer processStatus)
    {
        return dailyArticleMapper.updateProcessStatus(articleId, processStatus);
    }

    /**
     * 对文章内容进行智能排版（同步返回排版结果，不保存到数据库）
     * 
     * @param userId 用户ID
     * @param content 原始文章内容
     * @return 排版后的内容
     */
    @Override
    public String layoutArticleSync(Long userId, String content)
    {
        // 1. 获取用户的腾讯元器配置（解密后的真实配置）
        YuanqiAgentConfig config = yuanqiAgentConfigService.selectActiveConfigByUserIdDecrypted(userId, "daily_assistant");
        
        if (config == null) {
            log.error("未找到启用的腾讯元器智能体配置 - userId: {}", userId);
            throw new RuntimeException("未找到启用的腾讯元器智能体配置，请先配置智能体");
        }

        // 2. 获取排版智能体配置（优先使用排版专用智能体）
        String appKey = config.getApiKey();
        String appId = config.getAgentId(); // 默认使用主智能体
        
        // 尝试从 configJson 中获取排版专用智能体ID
        String configJson = config.getConfigJson();
        if (configJson != null && !configJson.trim().isEmpty()) {
            try {
                // 使用 Jackson 解析 JSON
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(configJson);
                
                if (jsonNode.has("layoutAgentId")) {
                    String layoutAgentId = jsonNode.get("layoutAgentId").asText();
                    if (layoutAgentId != null && !layoutAgentId.trim().isEmpty()) {
                        appId = layoutAgentId; // 使用排版专用智能体
                        log.info("使用排版专用智能体 - LayoutAgentId: {}", layoutAgentId);
                    }
                }
            } catch (Exception e) {
                log.warn("解析 configJson 失败，使用默认智能体 - configJson: {}, error: {}", configJson, e.getMessage());
            }
        }
        
        String userIdStr = String.valueOf(userId);
        
        log.info("开始智能排版 - 内容长度: {} 字符", content.length());
        
        try {
            // 调用同步排版API
            YuanqiAgentApiService.YuanqiApiResponse response = yuanqiAgentApiService.layoutArticleSync(
                appKey, 
                appId, 
                userIdStr, 
                content
            );
            
            if (response.isSuccess()) {
                String layoutedContent = response.getContent();
                log.info("同步排版完成 - ContentLength: {}", layoutedContent.length());
                return layoutedContent;
            } else {
                // 排版失败
                log.error("同步排版失败 - 错误: {}", response.getErrorMessage());
                throw new RuntimeException("排版失败: " + response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("调用腾讯元器同步排版工作流异常", e);
            throw new RuntimeException("调用排版工作流异常: " + e.getMessage());
        }
    }
    
    /**
     * 启动回调超时检查
     * 如果5分钟后文章仍然是"处理中"状态，说明没有收到回调，将状态设为失败
     * 
     * @param articleId 文章ID
     * @param articleTitle 文章标题
     */
    private void scheduleCallbackTimeoutCheck(Long articleId, String articleTitle) {
        scheduler.schedule(() -> {
            try {
                // 5分钟后检查文章状态
                DailyArticle article = dailyArticleMapper.selectDailyArticleById(articleId);
                
                if (article != null && article.getProcessStatus() == 0) {
                    // 仍然是处理中状态，说明没有收到回调
                    log.warn("[回调超时] 文章\"{}\"(ID:{})已超过5分钟未收到回调，设置为失败状态", 
                            articleTitle, articleId);
                    
                    updateArticleStatus(articleId, 2, 
                            "工作流回调超时：已等待5分钟仍未收到优化结果，请检查腾讯元器工作流配置或重试", 
                            null, null);
                } else if (article != null) {
                    log.info("[回调检查] 文章\"{}\"(ID:{})状态正常 - processStatus: {}", 
                            articleTitle, articleId, article.getProcessStatus());
                }
            } catch (Exception e) {
                log.error("[回调超时检查] 检查文章状态异常 - articleId: {}", articleId, e);
            }
        }, 5, TimeUnit.MINUTES);  // 5分钟后执行
        
        log.info("[回调超时检查] 已为文章\"{}\"(ID:{})启动5分钟超时检查", articleTitle, articleId);
    }

}
