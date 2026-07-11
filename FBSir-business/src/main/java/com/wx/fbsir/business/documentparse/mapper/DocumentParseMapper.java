package com.wx.fbsir.business.documentparse.mapper;

import com.wx.fbsir.business.documentparse.domain.DocumentParse;

import java.util.List;

/**
 * 文档解析Mapper接口
 *
 * @author FBSir
 * @date 2025-12-26
 */
public interface DocumentParseMapper
{
    /**
     * 查询文档解析
     *
     * @param id 文档解析ID
     * @return 文档解析
     */
    public DocumentParse selectDocumentParseById(Long id);

    /**
     * 根据文档ID查询文档解析
     *
     * @param documentId 文档ID
     * @return 文档解析
     */
    public DocumentParse selectDocumentParseByDocumentId(String documentId);

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
     * 删除文档解析
     *
     * @param id 文档解析ID
     * @return 结果
     */
    public int deleteDocumentParseById(Long id);

    /**
     * 批量删除文档解析
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteDocumentParseByIds(Long[] ids);

    /**
     * 更新处理状态
     *
     * @param id 文档解析ID
     * @param processStatus 处理状态
     * @return 结果
     */
    public int updateProcessStatus(Long id, Integer processStatus);

    /**
     * 更新解析后的内容
     *
     * @param id 文档解析ID
     * @param parsedContent 解析后的内容
     * @return 结果
     */
    public int updateParsedContent(Long id, String parsedContent);
}

