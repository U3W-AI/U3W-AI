package com.wx.fbsir.business.documentparse.service;

import com.wx.fbsir.business.documentparse.domain.DocumentParse;

import java.util.List;

/**
 * 文档解析Service接口
 *
 * @author FBSir
 * @date 2025-12-26
 */
public interface IDocumentParseService
{
    /**
     * 查询文档解析
     *
     * @param id 文档解析ID
     * @return 文档解析
     */
    public DocumentParse selectDocumentParseById(Long id);

    /**
     * 查询文档解析列表
     *
     * @param documentParse 文档解析
     * @return 文档解析集合
     */
    public List<DocumentParse> selectDocumentParseList(DocumentParse documentParse);

    /**
     * 根据用户ID查询文档解析列表
     *
     * @param userId 用户ID
     * @return 文档解析集合
     */
    public List<DocumentParse> selectDocumentParseListByUserId(Long userId);

    /**
     * 新增文档解析
     *
     * @param documentParse 文档解析
     * @return 结果
     */
    public int insertDocumentParse(DocumentParse documentParse);

    /**
     * 修改文档解析
     *
     * @param documentParse 文档解析
     * @return 结果
     */
    public int updateDocumentParse(DocumentParse documentParse);

    /**
     * 批量删除文档解析
     *
     * @param ids 需要删除的文档解析ID
     * @return 结果
     */
    public int deleteDocumentParseByIds(Long[] ids);

    /**
     * 删除文档解析信息
     *
     * @param id 文档解析ID
     * @return 结果
     */
    public int deleteDocumentParseById(Long id);

    /**
     * 创建文档解析记录并调用智能体解析（异步处理）
     *
     * @param userId 用户ID
     * @param documentId 文档ID（自动生成）
     * @param documentName 文档名称
     * @param prompt 提示词
     * @param base64Content 文档base64编码内容
     * @return 创建的文档解析记录
     */
    public DocumentParse createDocumentParseAndProcess(Long userId, String documentId, String documentName, String prompt, String base64Content);

    /**
     * 触发文档解析（异步执行）
     * 此方法必须从外部类调用以确保@Async生效
     *
     * @param documentParseId 文档解析ID
     * @param userId 用户ID
     * @param documentId 文档ID
     * @param prompt 提示词
     * @param url 文件访问URL（已替换域名）
     * @param originalFilename 原始文件名
     */
    public void triggerDocumentParse(Long documentParseId, Long userId, String documentId, String prompt, String url, String originalFilename);

    /**
     * 更新解析后的内容
     *
     * @param documentParseId 文档解析ID
     * @param parsedContent 解析后的内容
     * @return 结果
     */
    public int updateParsedContent(Long documentParseId, String parsedContent);

    /**
     * 更新文档解析处理状态
     *
     * @param documentParseId 文档解析ID
     * @param processStatus 处理状态
     * @return 结果
     */
    public int updateProcessStatus(Long documentParseId, Integer processStatus);
}

