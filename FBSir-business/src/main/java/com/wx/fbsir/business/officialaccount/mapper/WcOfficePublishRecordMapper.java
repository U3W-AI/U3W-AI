package com.wx.fbsir.business.officialaccount.mapper;

import com.wx.fbsir.business.officialaccount.domain.WcOfficePublishRecord;
import java.util.List;

/**
 * 公众号文章发布记录Mapper接口
 * 
 * @author FBSir
 * @date 2025-12-08
 */
public interface WcOfficePublishRecordMapper 
{
    /**
     * 查询公众号文章发布记录
     * 
     * @param id 公众号文章发布记录主键
     * @return 公众号文章发布记录
     */
    public WcOfficePublishRecord selectWcOfficePublishRecordById(Long id);

    /**
     * 查询公众号文章发布记录列表
     * 
     * @param wcOfficePublishRecord 公众号文章发布记录
     * @return 公众号文章发布记录集合
     */
    public List<WcOfficePublishRecord> selectWcOfficePublishRecordList(WcOfficePublishRecord wcOfficePublishRecord);

    /**
     * 根据文章ID查询发布记录列表
     * 
     * @param articleId 文章ID
     * @return 发布记录列表
     */
    public List<WcOfficePublishRecord> selectWcOfficePublishRecordListByArticleId(Long articleId);

    /**
     * 新增公众号文章发布记录
     * 
     * @param wcOfficePublishRecord 公众号文章发布记录
     * @return 结果
     */
    public int insertWcOfficePublishRecord(WcOfficePublishRecord wcOfficePublishRecord);

    /**
     * 修改公众号文章发布记录
     * 
     * @param wcOfficePublishRecord 公众号文章发布记录
     * @return 结果
     */
    public int updateWcOfficePublishRecord(WcOfficePublishRecord wcOfficePublishRecord);

    /**
     * 删除公众号文章发布记录
     * 
     * @param id 公众号文章发布记录主键
     * @return 结果
     */
    public int deleteWcOfficePublishRecordById(Long id);

    /**
     * 批量删除公众号文章发布记录
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteWcOfficePublishRecordByIds(Long[] ids);
}
