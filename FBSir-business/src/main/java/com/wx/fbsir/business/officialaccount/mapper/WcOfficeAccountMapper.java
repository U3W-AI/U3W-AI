package com.wx.fbsir.business.officialaccount.mapper;

import com.wx.fbsir.business.officialaccount.domain.WcOfficeAccount;
import java.util.List;

/**
 * 微信公众号配置Mapper接口
 * 
 * @author FBSir
 * @date 2025-12-08
 */
public interface WcOfficeAccountMapper 
{
    /**
     * 查询微信公众号配置
     * 
     * @param id 微信公众号配置主键
     * @return 微信公众号配置
     */
    public WcOfficeAccount selectWcOfficeAccountById(Long id);

    /**
     * 根据用户ID查询微信公众号配置
     * 
     * @param userId 用户ID
     * @return 微信公众号配置
     */
    public WcOfficeAccount selectWcOfficeAccountByUserId(Long userId);

    /**
     * 查询微信公众号配置列表
     * 
     * @param wcOfficeAccount 微信公众号配置
     * @return 微信公众号配置集合
     */
    public List<WcOfficeAccount> selectWcOfficeAccountList(WcOfficeAccount wcOfficeAccount);

    /**
     * 新增微信公众号配置
     * 
     * @param wcOfficeAccount 微信公众号配置
     * @return 结果
     */
    public int insertWcOfficeAccount(WcOfficeAccount wcOfficeAccount);

    /**
     * 修改微信公众号配置
     * 
     * @param wcOfficeAccount 微信公众号配置
     * @return 结果
     */
    public int updateWcOfficeAccount(WcOfficeAccount wcOfficeAccount);

    /**
     * 删除微信公众号配置
     * 
     * @param id 微信公众号配置主键
     * @return 结果
     */
    public int deleteWcOfficeAccountById(Long id);

    /**
     * 批量删除微信公众号配置
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteWcOfficeAccountByIds(Long[] ids);
}
