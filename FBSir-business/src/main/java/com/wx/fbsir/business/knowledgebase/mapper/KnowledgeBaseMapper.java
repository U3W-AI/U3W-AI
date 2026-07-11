package com.wx.fbsir.business.knowledgebase.mapper;

import java.util.List;
import com.wx.fbsir.business.knowledgebase.domain.KnowledgeBaseInfo;
import org.apache.ibatis.annotations.Param;

/**
 * 知识库Mapper接口
 * 
 * @author FBSir
 * @date 2025-12-20
 */
public interface KnowledgeBaseMapper
{
    /**
     * 查询知识库
     * 
     * @param kbId 知识库主键
     * @return 知识库
     */
    public KnowledgeBaseInfo selectKnowledgeBaseByKbId(Long kbId);

    /**
     * 查询知识库列表
     * 
     * @param knowledgeBaseInfo 知识库
     * @return 知识库集合
     */
    public List<KnowledgeBaseInfo> selectKnowledgeBaseList(KnowledgeBaseInfo knowledgeBaseInfo);

    /**
     * 查询公共模板列表
     * 
     * @return 知识库集合
     */
    public List<KnowledgeBaseInfo> selectPublicTemplateList();

    /**
     * 根据知识库ID列表查询知识库
     * 
     * @param kbIds 知识库ID列表
     * @return 知识库集合
     */
    public List<KnowledgeBaseInfo> selectKnowledgeBaseByIds(@Param("kbIds") List<Long> kbIds);

    /**
     * 新增知识库
     * 
     * @param knowledgeBaseInfo 知识库
     * @return 结果
     */
    public int insertKnowledgeBase(KnowledgeBaseInfo knowledgeBaseInfo);

    /**
     * 修改知识库
     * 
     * @param knowledgeBaseInfo 知识库
     * @return 结果
     */
    public int updateKnowledgeBase(KnowledgeBaseInfo knowledgeBaseInfo);

    /**
     * 删除知识库
     * 
     * @param kbId 知识库主键
     * @return 结果
     */
    public int deleteKnowledgeBaseByKbId(Long kbId);

    /**
     * 批量删除知识库
     * 
     * @param kbId 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteKnowledgeBaseByKbIds(Long kbId);
}
