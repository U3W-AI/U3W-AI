package com.cube.wechat.thirdapp.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.cube.common.core.domain.R;
import com.cube.common.utils.StringUtils;
import com.cube.wechat.thirdapp.constant.BasicConstant;
import com.cube.wechat.thirdapp.entiy.RmExternalInfo;
import com.cube.wechat.thirdapp.entiy.WeChatThirdCompany;
import com.cube.wechat.thirdapp.mapper.RmExternalInfoMapper;
import com.cube.wechat.thirdapp.service.ExternalAvatarService;
import com.cube.wechat.thirdapp.service.WeChatCorpUserService;
import com.cube.wechat.thirdapp.service.WeChatThirdCompanyService;
import com.cube.wechat.thirdapp.util.FriendAvatarUtil;
import com.cube.wechat.thirdapp.util.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author 张云龙
 */
@Slf4j
@Service
public class ExternalAvatarServiceImpl implements ExternalAvatarService {

    @Autowired
    private RedisService redisService;

    @Autowired
    private RmExternalInfoMapper rmExternalInfoMapper;

    @Autowired
    private WeChatCorpUserService weChatCorpUserService;
//    @Autowired
//    private RmEsSynDataService esSynDataService;;

    @Autowired
    private BasicConstant constant;

    /**
     * 服务商CorpID
     */
    @Value("${static.url}")
    private String staticUrl;
    @Autowired
    private WeChatThirdCompanyService weChatThirdCompanyService;


    @Override
    public R<String> getExternalAvatar(String externalUserId) {

        //redis获取  三方公共token 不用拼接企业
        Object externalAccessToken = redisService.getCacheObject("third_external_accessToken");

        if (ObjectUtil.isEmpty(externalAccessToken)) {
            String accessToken = FriendAvatarUtil.getAccessToken();
            if (StringUtils.isEmpty(accessToken)) {
                log.error("获取第三方增强组件Token失败");
                return R.fail("获取第三方增强组件Token失败");
            } else {
                redisService.setCacheObject("third_external_accessToken", accessToken, 5400L, TimeUnit.SECONDS);
                String externalAvatar = FriendAvatarUtil.getExternalAvatar(accessToken, externalUserId);
                return R.ok(externalAvatar, "获取成功");
            }
        } else {
            String externalAvatar = FriendAvatarUtil.getExternalAvatar(externalAccessToken.toString(), externalUserId);
            return R.ok(externalAvatar, "获取成功");
        }
    }


    /**
     * 根据企业id和外部联系人id 获取外部联系人头像  重载方法
     * @param externalUserId
     * @param corpId
     * @return
     */
    @Override
    public String getExternalAvatar(String externalUserId,String corpId) {

        //redis获取  三方公共token 不用拼接企业
        //直接获取redis里的token  无需重复判断  有一个半小时
        String accessToken = FriendAvatarUtil.getAccessToken();
        String externalAvatar = FriendAvatarUtil.getExternalAvatarByCorpIdAndExternalUserId(accessToken, externalUserId,corpId);
        log.info("增强组建返回居民信息：externalUserId：{},头像：{}", externalUserId,externalAvatar);
        if(StringUtils.isEmpty(externalAvatar)){
            log.info("头像为空，更新头像失败 设置为空：externalUserId：{}", externalUserId);
            externalAvatar = staticUrl+"tx.jpg";
        }
        return externalAvatar;
    }

    /**
     * 每天凌晨四点执行一次 更新外部联系人头像
     *
     * @return
     */
    @Override

    public R qrtzExternalAvatar(Map<String, String> paramMap) {
        log.info("======================开始定时更新无头像外部联系人========================");
        WeChatThirdCompany weChatThirdCompany = new WeChatThirdCompany();
        weChatThirdCompany.setSuiteId(constant.getSuiteID());
        List<String> corpList = new ArrayList<>();
        if(paramMap != null&& MapUtils.getString(paramMap,"corpId")!=null){
            corpList.add(MapUtils.getString(paramMap,"corpId"));
        }else{
            R<List<WeChatThirdCompany>> selectAllCompanyR = weChatThirdCompanyService.selectAllCompany(weChatThirdCompany);
            if(selectAllCompanyR!=null&&selectAllCompanyR.getCode()==R.SUCCESS){
                List<WeChatThirdCompany> allCompanyRData = selectAllCompanyR.getData();
                corpList = allCompanyRData.stream().map(WeChatThirdCompany::getCorpId).collect(Collectors.toList());
            }
        }
        if(corpList!=null&&corpList.size()>0){
            for (String corpId : corpList) {
                //直接获取redis里的token  无需重复判断  有一个半小时
                String accessToken = FriendAvatarUtil.getAccessToken();
                if (StringUtils.isEmpty(accessToken)) {
                    log.error("定时任务====获取第三方增强组件Token失败");
                    return R.fail("定时任务====获取第三方增强组件Token失败");
                }
                //一次取1000
                int batchSize = 1000;
                int offset = 0;
                while (true) {
                    List<RmExternalInfo> rmExternalInfos = rmExternalInfoMapper.selectByAvatarIsNull(corpId, offset, batchSize);
                    if (CollectionUtils.isEmpty(rmExternalInfos)) {
                        log.info("没有需要更新的外部联系人");
                        break;
                    }
                    if (rmExternalInfos != null && rmExternalInfos.size() > 0) {
                        List<RmExternalInfo> updateList = new ArrayList<>();
//                        List<RmEsSynData> esSynDataList = new ArrayList<RmEsSynData>();
                        for (RmExternalInfo externalInfo : rmExternalInfos) {
                            log.info("开始更新外部联系人头像：", externalInfo.getExternalUserId());
                            String externalUserId = externalInfo.getExternalUserId();
                            String externalAvatar = FriendAvatarUtil.getExternalAvatarByCorpIdAndExternalUserId(accessToken, externalUserId,corpId);
//                            String externalAvatar = FriendAvatarUtil.getExternalAvatar(accessToken, externalInfo.getExternalUserId());
                            log.info("增强组建返回居民信息：externalUserId：{},头像：{}", externalUserId,externalAvatar);
                            if(StringUtils.isEmpty(externalAvatar)){
                                log.info("头像为空，更新头像失败 设置为空：externalUserId：{}", externalInfo.getExternalUserId());
                                externalAvatar = staticUrl+"tx.jpg";
                            }
                            if (StringUtils.isNotEmpty(externalAvatar)) {
                                RmExternalInfo rmExternalInfo = new RmExternalInfo();
                                rmExternalInfo.setId(externalInfo.getId());
                                rmExternalInfo.setAvatar(externalAvatar);
                                rmExternalInfo.setUserId(externalInfo.getUserId());
//                                RmEsSynData rmEsSynData = new RmEsSynData();
//                                rmEsSynData.setCorpId(corpId);
//                                rmEsSynData.setUserId(externalInfo.getUserId());
//                                rmEsSynData.setIsSingleUser(1);
//                                rmEsSynData.setExternalUserId(externalInfo.getExternalUserId());
//                                rmEsSynData.setUpdateType(EsSynDataUpdateEnum.UPDATE_EXTERNAL.getCode());
//                                esSynDataList.add(rmEsSynData);
                                updateList.add(rmExternalInfo);
                            }
                        }
                        // 批量提交更新
                        if (CollectionUtils.isNotEmpty(updateList)) {
                            rmExternalInfoMapper.updateExternalAvatarBatch(updateList);
                        }
                        //todo 同步 es
                       /* if(CollectionUtils.isNotEmpty(esSynDataList)){
                            //批量提交同步 es
                            esSynDataService.saveBatch(esSynDataList);
                        }*/

                    }
                    offset += batchSize;
                }
            }
        }


        log.info("======================更新结束========================");
        return R.ok();
    }

    @Override
    public R selectUserNameByUserIds() {
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("suiteId", constant.getSuiteID());
        weChatCorpUserService.selectUserNameByUserIds(paramMap);
        return R.ok();
    }
}
