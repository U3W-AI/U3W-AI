package com.wx.fbsir.business.knowledgebase.service;

import java.util.List;
import com.wx.fbsir.business.knowledgebase.domain.KnowledgeBaseInfo;

/**
 * 知识库Service接口
 * 
 * @author wxfbsir
 * @date 2025-12-20
 */
public interface IKnowledgeBaseService
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
     * 批量删除知识库
     * 
     * @param kbId 需要删除的知识库主键集合
     * @return 结果
     */
    public int deleteKnowledgeBaseByKbIds(Long kbId);

    /**
     * 删除知识库信息
     * 
     * @param kbId 知识库主键
     * @return 结果
     */
    public int deleteKnowledgeBaseByKbId(Long kbId);

    /**
     * 收藏/取消收藏知识库
     * 
     * @param kbId 知识库ID
     * @param userId 用户ID
     * @return 结果
     */
    public boolean toggleFavoriteKnowledge(Long kbId, Long userId);

    /**
     * 知识库上传（上传到智能体元器或企业微信机器人）
     * 
     * @param kbId 知识库ID
     * @param uploadType 上传类型（1-智能体元器，2-企业微信机器人，3-两者）
     * @param agentName 智能体名称（上传类型为1或3时必填）
     * @param robotName 机器人名称（上传类型为2或3时必填）
     * @param teamName 团队名称（上传类型为1或3时可选，默认"个人空间"）
     * @return 结果
     */
    public boolean uploadKnowledgeBase(Long kbId, Integer uploadType, String agentName, String robotName, String teamName);

    /**
     * 本地文档上传
     * 
     * @param filePath 文档路径
     * @param uploadType 上传类型（1-智能体元器，2-企业微信机器人，3-两者）
     * @param agentName 智能体名称（上传类型为1或3时必填）
     * @param robotName 机器人名称（上传类型为2或3时必填）
     * @param kbName 知识库名称（可选，如果不提供则使用文件名）
     * @param teamName 团队名称（上传类型为1或3时可选，默认"个人空间"）
     * @return 结果
     */
    public boolean uploadLocalDocument(String filePath, Integer uploadType, String agentName, String robotName, String kbName, String teamName);

    /**
     * 查询当前用户可见的知识库列表（包含自己的知识库和公共模板）
     *
     * @return 知识库集合
     */
    public List<KnowledgeBaseInfo> selectUserVisibleKnowledgeBase();

    /**
     * 查询用户收藏的知识库列表
     *
     * @return 知识库集合
     */
    public List<KnowledgeBaseInfo> selectFavoriteKnowledgeBase();
}
