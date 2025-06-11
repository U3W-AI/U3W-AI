package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.RmExternalInfo;
import com.cube.wechat.thirdapp.entiy.RmExternalStatisticsNum;
import com.cube.wechat.thirdapp.param.WeChatExternalContactParam;

import java.util.List;
import java.util.Map;

/**
 * @author sjl
 * @Created date 2024/4/16 13:26
 * <p>
 * 居民-外部联系人数据服务
 */
public interface ExternalContactService {
    /**
     * 保存居民信息
     *
     * @param residentMap
     * @return
     */

    public R<RmExternalStatisticsNum> saveExternalInfo(Map residentMap);


    /**
     * 添加外部联系人
     *
     * @param paramMap
     * @return
     */
    public R<RmExternalStatisticsNum> addExternalContract(WeChatExternalContactParam weChatExternalContactParam);

    /**
     * 获取跟进人员数据
     *
     * @param dataList
     * @param qywxExternalContactParam
     */
    public void getFollowUserList(List<Map> dataList, WeChatExternalContactParam weChatExternalContactParam);

    public List<RmExternalStatisticsNum> processFinalData(List<Map> residentDataList, String corpId, String type);


    //同步指定人员的外部联系人
    public R<List<RmExternalStatisticsNum>> synExternalListByUserId(Map map);

    /**
     * 删除外部联系人
     *
     * @param paramMap
     * @return
     */
    public R<RmExternalStatisticsNum> deleteExternalContract(Map paramMap);

    /**
     * 同步企业联系人
     *
     * @param map
     * @return
     */

    public R synExternalContacts(Map map);

    /**
     * 查询外部联系人档案
     *
     * @param rmExternalInfo
     * @return
     */
    public R<Map> selectExternalFileInfo(RmExternalInfo rmExternalInfo);

    /**
     * 保存居民档案
     *
     * @param map
     * @return
     */
    public R saveExternalArchives(Map<String, Object> map);

    /**
     * 查询居民基本信息
     *
     * @param externalInfo
     * @return
     */
    public R<RmExternalInfo> selectExternalInfoByExternalUserId(RmExternalInfo externalInfo);

    /**
     * 更新居民头像
     */
    public R updateExternalContactAvatar(RmExternalInfo rmExternalInfo);

    /**
     * 获取未分配的外部联系人
     * @return
     */
    R getUnassignedExternalContacts(Map paramMap);


}
