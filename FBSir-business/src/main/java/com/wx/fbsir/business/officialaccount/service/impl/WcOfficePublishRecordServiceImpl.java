package com.wx.fbsir.business.officialaccount.service.impl;

import java.util.List;
import com.wx.fbsir.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wx.fbsir.business.officialaccount.mapper.WcOfficePublishRecordMapper;
import com.wx.fbsir.business.officialaccount.domain.WcOfficePublishRecord;
import com.wx.fbsir.business.officialaccount.service.IWcOfficePublishRecordService;

/**
 * 公众号文章发布记录Service业务层处理
 * 
 * @author FBSir
 * @date 2025-12-08
 */
@Service
public class WcOfficePublishRecordServiceImpl implements IWcOfficePublishRecordService 
{
    @Autowired
    private WcOfficePublishRecordMapper wcOfficePublishRecordMapper;

    /**
     * 查询公众号文章发布记录
     * 
     * @param id 公众号文章发布记录主键
     * @return 公众号文章发布记录
     */
    @Override
    public WcOfficePublishRecord selectWcOfficePublishRecordById(Long id)
    {
        return wcOfficePublishRecordMapper.selectWcOfficePublishRecordById(id);
    }

    /**
     * 查询公众号文章发布记录列表
     * 
     * @param wcOfficePublishRecord 公众号文章发布记录
     * @return 公众号文章发布记录
     */
    @Override
    public List<WcOfficePublishRecord> selectWcOfficePublishRecordList(WcOfficePublishRecord wcOfficePublishRecord)
    {
        return wcOfficePublishRecordMapper.selectWcOfficePublishRecordList(wcOfficePublishRecord);
    }

    /**
     * 根据文章ID查询发布记录列表
     * 
     * @param articleId 文章ID
     * @return 发布记录列表
     */
    @Override
    public List<WcOfficePublishRecord> selectWcOfficePublishRecordListByArticleId(Long articleId)
    {
        return wcOfficePublishRecordMapper.selectWcOfficePublishRecordListByArticleId(articleId);
    }

    /**
     * 新增公众号文章发布记录
     * 
     * @param wcOfficePublishRecord 公众号文章发布记录
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertWcOfficePublishRecord(WcOfficePublishRecord wcOfficePublishRecord)
    {
        wcOfficePublishRecord.setCreateTime(DateUtils.getNowDate());
        return wcOfficePublishRecordMapper.insertWcOfficePublishRecord(wcOfficePublishRecord);
    }

    /**
     * 修改公众号文章发布记录
     * 
     * @param wcOfficePublishRecord 公众号文章发布记录
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateWcOfficePublishRecord(WcOfficePublishRecord wcOfficePublishRecord)
    {
        wcOfficePublishRecord.setUpdateTime(DateUtils.getNowDate());
        return wcOfficePublishRecordMapper.updateWcOfficePublishRecord(wcOfficePublishRecord);
    }

    /**
     * 批量删除公众号文章发布记录
     * 
     * @param ids 需要删除的公众号文章发布记录主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteWcOfficePublishRecordByIds(Long[] ids)
    {
        return wcOfficePublishRecordMapper.deleteWcOfficePublishRecordByIds(ids);
    }

    /**
     * 删除公众号文章发布记录信息
     * 
     * @param id 公众号文章发布记录主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteWcOfficePublishRecordById(Long id)
    {
        return wcOfficePublishRecordMapper.deleteWcOfficePublishRecordById(id);
    }
}
