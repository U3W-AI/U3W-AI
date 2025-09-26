package com.cube.wechat.selfapp.app.mapper;

import com.cube.wechat.selfapp.app.domain.PromptTemplate;
import com.cube.wechat.selfapp.app.domain.query.ScorePromptQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * ClassName: PromptTemplateMapper
 * Package: com.cube.wechat.selfapp.app.mapper
 * Description:
 *
 * @Author pupil
 * @Create 2025/9/20 13:21
 * @Version 1.0
 */
@Mapper
public interface PromptTemplateMapper {
    /**
     * 查询评分模板配置
     *
     * @param id 评分模板配置主键
     * @return 评分模板配置
     */
    public PromptTemplate getPromptTemplateById(Long id);

    /**
     * 查询评分模板配置列表
     *
     * @param scorePromptQuery 评分模板搜索参数
     * @return 评分模板配置集合
     */
    public List<PromptTemplate> getPromptTemplateList(ScorePromptQuery scorePromptQuery);

    /**
     * 新增评分模板配置
     *
     * @param PromptTemplate 评分模板配置
     * @return 结果
     */
    public int savePromptTemplate(PromptTemplate PromptTemplate);

    /**
     * 修改评分模板配置
     *
     * @param PromptTemplate 评分模板配置
     * @return 结果
     */
    public int updatePromptTemplate(PromptTemplate PromptTemplate);

    /**
     * 删除评分模板配置
     *
     * @param id 评分模板配置主键
     * @return 结果
     */
    public int deletePromptTemplateById(Long id);

    /**
     * 批量删除评分模板配置
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deletePromptTemplateByIds(Long[] ids);

    public PromptTemplate getScorePromptById(@Param("id") Long id);

    public List<PromptTemplate> getScorePromptList(@Param("scorePromptQuery") ScorePromptQuery scorePromptQuery,
                                                   @Param("userId") Long userId);

    public List<PromptTemplate> getAllScorePrompt(@Param("userId") Long userId);

    PromptTemplate getScoreWord();
}
