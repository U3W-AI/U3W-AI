package com.wx.fbsir.business.dailyassistant.service;

import java.util.List;
import com.wx.fbsir.business.dailyassistant.domain.DailyArticle;

/**
 * 日更助手文章Service接口
 *
 * @author FBSir
 * @date 2025-12-05
 */
public interface IDailyArticleService
{
    /**
     * 查询日更助手文章
     *
     * @param id 日更助手文章ID
     * @return 日更助手文章
     */
    public DailyArticle selectDailyArticleById(Long id);

    /**
     * 查询日更助手文章列表
     *
     * @param dailyArticle 日更助手文章
     * @return 日更助手文章集合
     */
    public List<DailyArticle> selectDailyArticleList(DailyArticle dailyArticle);

    /**
     * 根据用户ID查询日更助手文章列表
     *
     * @param userId 用户ID
     * @return 日更助手文章集合
     */
    public List<DailyArticle> selectDailyArticleListByUserId(Long userId);

    /**
     * 新增日更助手文章
     *
     * @param dailyArticle 日更助手文章
     * @return 结果
     */
    public int insertDailyArticle(DailyArticle dailyArticle);

    /**
     * 修改日更助手文章
     *
     * @param dailyArticle 日更助手文章
     * @return 结果
     */
    public int updateDailyArticle(DailyArticle dailyArticle);

    /**
     * 批量删除日更助手文章
     *
     * @param ids 需要删除的日更助手文章ID
     * @return 结果
     */
    public int deleteDailyArticleByIds(Long[] ids);

    /**
     * 删除日更助手文章信息
     *
     * @param id 日更助手文章ID
     * @return 结果
     */
    public int deleteDailyArticleById(Long id);

    /**
     * 创建文章并调用智能体优化
     *
     * @param userId 用户ID
     * @param articleTitle 文章标题
     * @param selectedModels 已选择的模型，格式如"1,2,3"
     * @return 创建的文章
     */
    public DailyArticle createArticleAndOptimize(Long userId, String articleTitle, String selectedModels);

    /**
     * 触发文章优化（异步执行）
     * 此方法必须从外部类调用以确保@Async生效
     *
     * @param articleId 文章ID
     * @param userId 用户ID
     * @param articleTitle 文章标题
     */
    public void triggerArticleOptimization(Long articleId, Long userId, String articleTitle);

    /**
     * 保存大模型生成的未优化文章内容
     *
     * @param articleId 文章ID
     * @param modelName 模型名称
     * @param content 文章内容
     * @param modelIndex 模型索引（1-3）
     * @return 结果
     */
    public int saveModelContent(Long articleId, String modelName, String content, int modelIndex);

    /**
     * 更新优化后的文章内容
     *
     * @param articleId 文章ID
     * @param optimizedContent 优化后的内容
     * @return 结果
     */
    public int updateOptimizedContent(Long articleId, String optimizedContent);

    /**
     * 更新文章处理状态
     *
     * @param articleId 文章ID
     * @param processStatus 处理状态
     * @return 结果
     */
    public int updateProcessStatus(Long articleId, Integer processStatus);

    /**
     * 对文章内容进行智能排版（同步调用元器工作流，直接返回排版结果）
     * 排版结果不保存到数据库，仅返回给前端显示
     *
     * @param userId 用户ID
     * @param content 原始文章内容
     * @return 排版后的内容
     */
    public String layoutArticleSync(Long userId, String content);
}
