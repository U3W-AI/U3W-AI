package com.wx.fbsir.business.officialaccount.service;

import java.util.List;
import com.wx.fbsir.business.officialaccount.domain.WcOfficeAccount;

/**
 * 微信公众号配置Service接口
 * 
 * @author FBSir
 * @date 2025-12-08
 */
public interface IWcOfficeAccountService 
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
     * 新增或更新微信公众号配置
     * 
     * @param wcOfficeAccount 微信公众号配置
     * @return 结果
     */
    public int saveOrUpdateWcOfficeAccount(WcOfficeAccount wcOfficeAccount);

    /**
     * 修改微信公众号配置
     * 
     * @param wcOfficeAccount 微信公众号配置
     * @return 结果
     */
    public int updateWcOfficeAccount(WcOfficeAccount wcOfficeAccount);

    /**
     * 批量删除微信公众号配置
     * 
     * @param ids 需要删除的微信公众号配置主键集合
     * @return 结果
     */
    public int deleteWcOfficeAccountByIds(Long[] ids);

    /**
     * 删除微信公众号配置信息
     * 
     * @param id 微信公众号配置主键
     * @return 结果
     */
    public int deleteWcOfficeAccountById(Long id);
}
