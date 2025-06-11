package com.cube.wechat.thirdapp.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.cube.common.core.domain.R;
import com.cube.common.utils.StringUtils;
import com.cube.wechat.thirdapp.constant.BasicConstant;
import com.cube.wechat.thirdapp.entiy.RmExternalStatisticsNum;
import com.cube.wechat.thirdapp.entiy.WeChatThirdCompany;
import com.cube.wechat.thirdapp.mapper.RmExternalStatisticsNumMapper;
import com.cube.wechat.thirdapp.service.ExternalStatisticsNumService;
import com.cube.wechat.thirdapp.service.WeChatThirdCompanyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author sjl
 * @Created date 2024/3/12 10:23
 */
@Service
@Transactional
@Slf4j
public class ExternalStatisticsNumServiceImpl implements ExternalStatisticsNumService {
    @Autowired
    private RmExternalStatisticsNumMapper externalStatisticsNumMapper;


    @Autowired
    private WeChatThirdCompanyService weChatThirdCompanyService;

    @Autowired
    private BasicConstant constant;


    @Override
    public R saveExternalStatistics(List<RmExternalStatisticsNum> recordList, String type) {
        log.info("保存统计数据:" + recordList);
        if (CollectionUtils.isNotEmpty(recordList)) {
            for (RmExternalStatisticsNum record : recordList) {
                record.setDataTime(new Date());
                //查询是否已存在。
                RmExternalStatisticsNum rmExternalStatisticsNum = externalStatisticsNumMapper.selectExternalStatisticsNum(record);
                if (ObjectUtil.isNotEmpty(rmExternalStatisticsNum)) {
                    if (type != null && !"SYN".equals(type)) {
                        //如果当前操作是被外部联系人删除，并且没有被该成员删除，不算做流失，不计入到今日数据
                        Integer isDeletedByContact = record.getIsDeletedByContact();
                        Integer isDeletedByPerson = record.getIsDeletedByPerson();
                        if (isDeletedByContact == 1 && isDeletedByPerson == 0) {
                            record.setDataTime(null);
                        }
                        System.out.println(JSON.toJSONString(record));
                        externalStatisticsNumMapper.updateExternalStatisticsNum(record);
                    }
                } else {
                    externalStatisticsNumMapper.insertSelective(record);
                }
            }
        }
        return R.ok();
    }


    @Override
    public R<List<Map<String, Object>>> selectUserIdsByExternalId(Set<String> externalUserIds, String corpId) {
        try {
            Map<String, HashSet<String>> userToExternalUserIds = new HashMap<>();
            for (String externalUserId : externalUserIds) {
                //根据externalUserId和corpId查询,并且状态为1
                List<RmExternalStatisticsNum> rmExternalStatisticsNums = externalStatisticsNumMapper.selectExternalUserIdAndCorpId(externalUserId, corpId);
                log.info("rmExternalStatisticsNums:{}", rmExternalStatisticsNums);
                if (CollectionUtils.isNotEmpty(rmExternalStatisticsNums)) {
                    for (RmExternalStatisticsNum externalInfo : rmExternalStatisticsNums) {
                        String userId = externalInfo.getUserId();
                        log.info("userId:{}", userId);
                        userToExternalUserIds.computeIfAbsent(externalInfo.getUserId(), k -> new HashSet<>())
                                .add(externalInfo.getExternalUserId());
                    }
                }
            }
            List<Map<String, Object>> result = userToExternalUserIds.entrySet().stream().map(entry -> {
                Map<String, Object> map = new HashMap<>();
                map.put("userId", entry.getKey());
                map.put("externalUserId", entry.getValue());
                return map;
            }).collect(Collectors.toList());
            return R.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("查询失败，发生异常");
        }
    }

    @Override
    public R selectAddExternalByCorpId(String corpId) {
        if (StringUtils.isEmpty(corpId)) {
            return R.fail("企业id为空");
        }
        //查询公司详情
        WeChatThirdCompany qywxThirdCompany = new WeChatThirdCompany();
        qywxThirdCompany.setCorpId(corpId);
        qywxThirdCompany.setSuiteId(constant.getSuiteID());

        R<WeChatThirdCompany> qywxThirdCompanyR = weChatThirdCompanyService.selectCompanyInfo(qywxThirdCompany);
        if (qywxThirdCompanyR == null || qywxThirdCompanyR.getCode() != R.SUCCESS) {
            return R.fail("feign远程调用system服务失败");
        }
        WeChatThirdCompany qywxThirdCompanyRData = qywxThirdCompanyR.getData();
        if (ObjectUtil.isEmpty(qywxThirdCompanyRData)){
            return R.fail("该企业不存在");
        }

        Long friendCount = externalStatisticsNumMapper.selectAddExternalByCorpId(corpId);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("friendCount",friendCount);
        return R.ok(resultMap);
    }


}
