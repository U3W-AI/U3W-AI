package com.cube.wechat.thirdapp.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.cube.common.core.domain.R;
import com.cube.common.utils.StringUtils;
import com.cube.wechat.thirdapp.constant.BasicConstant;
import com.cube.wechat.thirdapp.entiy.*;
import com.cube.wechat.thirdapp.mapper.*;
import com.cube.wechat.thirdapp.param.WeChatExternalContactParam;
import com.cube.wechat.thirdapp.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author sjl
 * @Created date 2024/4/16 13:53
 */
@Service
@Transactional
@Slf4j
public class ExternalContactServiceImpl implements ExternalContactService {
    @Autowired
    private RmExternalInfoMapper rmExternalInfoMapper;
    @Autowired
    private RmExternalInfoArchivesMapper rmExternalInfoArchivesMapper;
    @Autowired
    private RmExternalInfoArchivesHistoryMapper rmExternalInfoArchivesHistoryMapper;
    @Autowired
    private RmExternalStatisticsNumMapper externalStatisticsNumMapper;
    @Autowired
    private WeChatDataService weChatDataService;
    @Autowired
    private ILabelService labelService;
    @Autowired
    private ExternalLabelService externalLabelService;
    @Autowired
    private ExternalStatisticsNumService externalStatisticsNumService;
    @Autowired
    private WeChatCorpUserService weChatCorpUserService;
    @Autowired
    private ExternalAvatarService externalAvatarService;
    @Autowired
    private RmExternalLabelMapper rmExternalLabelMapper;
    @Autowired
    private RmLabelMapper rmLabelMapper;
    @Autowired
    private RmUserExternalSynMapper rmUserExternalSynMapper;
    @Autowired
    private WeChatThirdCompanyService weChatThirdCompanyService;
    @Autowired
    private BasicConstant constant;
    @Autowired
    private CompanyService companyService;
//    @Autowired
//    private RmEsSynDataService rmEsSynDataService;


    /**
     * 保存居民数据
     *
     * @param residentMap
     * @return
     */
    @Override
    public R<RmExternalStatisticsNum> saveExternalInfo(Map residentMap) {
        try {
            String corpId = MapUtils.getString(residentMap, "corpId");
            //TODO 1 居民信息主数据处理，逻辑判断，入库
            String userid = MapUtils.getString(residentMap, "userid");
            String remark = MapUtils.getString(residentMap, "remark");
            String remarkCorpName = MapUtils.getString(residentMap, "remark_corp_name");
            String operUserid = MapUtils.getString(residentMap, "oper_userid");
            long createtime = MapUtils.getLong(residentMap, "createTime");
            String description = MapUtils.getString(residentMap, "description");
            String externalUserId = MapUtils.getString(residentMap, "externalUserId");
            String name = MapUtils.getString(residentMap, "name");
            String avatar = MapUtils.getString(residentMap, "avatar");
            String corpName = MapUtils.getString(residentMap, "corpName");
            String corpFullName = MapUtils.getString(residentMap, "corpFullName");
            String externaltype = MapUtils.getString(residentMap, "type");
            String gender = MapUtils.getString(residentMap, "gender");
            String add_way = MapUtils.getString(residentMap, "add_way");
            String unionid = MapUtils.getString(residentMap, "unionid");
            String position = MapUtils.getString(residentMap, "position");
            RmExternalInfo externalInfo = new RmExternalInfo();
            externalInfo.setExternalUserId(externalUserId);
            externalInfo.setName(name);
            externalInfo.setAvatar(avatar);
            externalInfo.setCorpName(corpName);
            externalInfo.setCorpFullName(corpFullName);
            externalInfo.setType(externaltype);
            externalInfo.setGender(gender);
            externalInfo.setCorpId(corpId);
            externalInfo.setAddWay(add_way);
            externalInfo.setUnionId(unionid);
            externalInfo.setPosition(position);
            externalInfo.setUserId(userid);
            externalInfo.setRemark(remark);
            externalInfo.setRemarkCorpName(remarkCorpName);
            externalInfo.setOperUserid(operUserid);
            RmExternalStatisticsNum rmExternalStatisticsNum = new RmExternalStatisticsNum();

            if (ObjectUtils.isEmpty(createtime)) {
                externalInfo.setCreateTime(new Date());
                rmExternalStatisticsNum.setAddTime(new Date());
            } else {
                DateTime date = DateUtil.date(createtime * 1000);// 需要将秒数转换为毫秒数
                externalInfo.setCreateTime(date);
                rmExternalStatisticsNum.setAddTime(date);
            }
            rmExternalStatisticsNum.setExternalUserId(externalUserId);
            rmExternalStatisticsNum.setUserId(userid);
            rmExternalStatisticsNum.setCorpId(corpId);
            //未被外部联系人删除
            rmExternalStatisticsNum.setIsDeletedByContact(0);
            //外部联系人未被删除
            rmExternalStatisticsNum.setIsDeletedByPerson(0);
            //未流失
            rmExternalStatisticsNum.setIsLost(0);
            externalInfo.setDescription(description);
            //根据  externalUserId 和  operUserId去MongoDB查询  没有就新增
            //查询存不存在
            RmExternalInfo paramExternalInfo = new RmExternalInfo();
            paramExternalInfo.setCorpId(corpId);
            paramExternalInfo.setExternalUserId(externalUserId);
            paramExternalInfo.setUserId(userid);
            externalInfo.setStatus(1);
            List<RmExternalInfo> rmExternalInfoList = rmExternalInfoMapper.selectExternalInfoByExternalInfo(paramExternalInfo);
            if (rmExternalInfoList != null && rmExternalInfoList.size() > 0) {
                String dataType = MapUtils.getString(residentMap, "dataType");
                if (StringUtils.isNotEmpty(dataType) && "syn".equals(dataType)) {
                    log.info("不更新基本信息");
                } else {
                    //修改
                    externalInfo.setDataUpdateTime(new Date());
                    externalInfo.setId(rmExternalInfoList.get(0).getId());
                    rmExternalInfoMapper.updateByPrimaryKeySelective(externalInfo);
                }
            } else {
                //新增
                externalInfo.setId(UUID.randomUUID().toString());
                externalInfo.setDataCreateTime(new Date());
                rmExternalInfoMapper.insertSelective(externalInfo);
            }
            //TODO 2 标签数据处理，逻辑判断，入库
            List<Map> tagsList = (List<Map>) MapUtils.getObject(residentMap, "tagsList");
            List<Map> labelParamList = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(tagsList)) {
                for (Map tagMap : tagsList) {
                    Map<String, Object> labelParamMap = new HashMap<>();
                    //标签组名称
                    labelParamMap.put("groupName", MapUtils.getString(tagMap, "group_name"));
                    labelParamMap.put("corpId", corpId);
                    labelParamMap.put("labelName", MapUtils.getString(tagMap, "tag_name"));
                    String type = MapUtils.getString(tagMap, "type");
                    if (StringUtils.isNotEmpty(type) && !type.equals("2")) {
                        labelParamMap.put("type", type);
                        labelParamMap.put("labelId", MapUtils.getString(tagMap, "tag_id"));
                        labelParamMap.put("userId", userid);
                        labelParamList.add(labelParamMap);
                    }
                }
            }
            if (CollectionUtils.isNotEmpty(labelParamList)) {
                //批量保存标签组和标签数据
                R<List<Map>> qywxLabelData = labelService.saveQywxLabelData(labelParamList, corpId);
                if (qywxLabelData.getCode() == R.SUCCESS) {
                    List<Map> labelDataData = qywxLabelData.getData();
                    if (labelDataData != null && labelDataData.size() > 0) {
                        //批量插入
                        List<RmExternalLabel> insertBatchList = new ArrayList<>();
                        //清除原关联数据
                        RmExternalLabel rmExternalLabel = new RmExternalLabel();
                        rmExternalLabel.setExternalUserId(externalUserId);
                        rmExternalLabel.setCorpId(corpId);
                        rmExternalLabel.setUserId(userid);
                        externalLabelService.deleteExternalLabel(rmExternalLabel);
                        //封装保存新标签数据
                        for (Map labelDataDataMap : labelDataData) {
                            String labelId = MapUtils.getString(labelDataDataMap, "labelId");
                            String labelGroupId = MapUtils.getString(labelDataDataMap, "labelGroupId");
                            RmExternalLabel externalInfoLabel = new RmExternalLabel();
                            externalInfoLabel.setLabelId(labelId);
                            externalInfoLabel.setUserId(userid);
                            externalInfoLabel.setLabelGroupId(labelGroupId);
                            externalInfoLabel.setCorpId(corpId);
                            externalInfoLabel.setExternalUserId(externalUserId);
                            externalInfoLabel.setCreateTime(DateUtil.parse(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss")));
                            externalInfoLabel.setId(UUID.randomUUID().toString());
                            externalInfoLabel.setCreateTime(new Date());
                            insertBatchList.add(externalInfoLabel);
                        }
                        externalLabelService.saveExternalLabelBatch(insertBatchList);
                    }
                }
            }
            return R.ok(rmExternalStatisticsNum);
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("居民基本信息保存失败,发生异常");
        }
    }

    @Override
    public R<RmExternalStatisticsNum> addExternalContract(WeChatExternalContactParam weChatExternalContactParam) {
        //labelService.getQywxLabelData(qywxExternalContactParam.getCorpId());
        //数据总集合，通过递归，数据会源源不断向该集合中添加对象
        List<Map> residentDataList = new ArrayList<>();
        //第一次分页游标参数设置为空
        weChatExternalContactParam.setNext_cursor(null);
        //封装数据
        getFollowUserList(residentDataList, weChatExternalContactParam);
        List<RmExternalStatisticsNum> rmExternalStatisticsNumList = processFinalData(residentDataList, weChatExternalContactParam.getCorpId(), "");
        if (CollectionUtils.isNotEmpty(rmExternalStatisticsNumList)) {
            externalStatisticsNumService.saveExternalStatistics(rmExternalStatisticsNumList, "add");
            return R.ok(null, "添加居民成功");
        } else {
            return R.fail(null, "【添加好友】居民信息存储服务调用失败");
        }
    }


    @Override
    public void getFollowUserList(List<Map> dataList, WeChatExternalContactParam qywxExternalContactParam) {
        //数据集
        R<Map> queryExternalContactInfoR = weChatDataService.queryExternalContactInfo(qywxExternalContactParam.getCorpId(), qywxExternalContactParam.getExternalUserId(), qywxExternalContactParam.getNext_cursor());
        if (queryExternalContactInfoR.getCode() == R.SUCCESS) {
            Map externalContactResultMap = queryExternalContactInfoR.getData();
            Map externalContactMap = MapUtils.getMap(externalContactResultMap, "external_contact");
            //外部联系人id
            String externalUserId = MapUtils.getString(externalContactMap, "external_userid");
            String name = MapUtils.getString(externalContactMap, "name");
            String position = MapUtils.getString(externalContactMap, "position");
            String avatar = MapUtils.getString(externalContactMap, "avatar");
            String corpName = MapUtils.getString(externalContactMap, "corp_name");
            String corpFullName = MapUtils.getString(externalContactMap, "corp_full_name");
            int type = MapUtils.getInteger(externalContactMap, "type");
            int gender = MapUtils.getInteger(externalContactMap, "gender");
            String unionid = MapUtils.getString(externalContactMap, "unionid");
            List<Map> followUserList = (List<Map>) MapUtils.getObject(externalContactResultMap, "follow_user");
            if (CollectionUtils.isNotEmpty(followUserList)) {
                for (Map followUserMap : followUserList) {
                    //居民数据，根据跟进人数拆分
                    Map<String, Object> residentDataMap = new HashMap<>();
                    residentDataMap.put("externalUserId", externalUserId);
                    residentDataMap.put("name", name);
                    residentDataMap.put("avatar", avatar);
                    residentDataMap.put("corpName", corpName);
                    residentDataMap.put("corpFullName", corpFullName);
                    residentDataMap.put("type", type);
                    residentDataMap.put("gender", gender);
                    residentDataMap.put("unionid", unionid);
                    residentDataMap.put("corpId", qywxExternalContactParam.getCorpId());
                    residentDataMap.put("position", position);
                    String userid = MapUtils.getString(followUserMap, "userid");
                    if (!userid.equals(qywxExternalContactParam.getUserId())) {
                        continue;
                    }
                    String oper_userid = MapUtils.getString(followUserMap, "oper_userid");
                    String remark_corp_name = MapUtils.getString(followUserMap, "remark_corp_name");
                    //来源
                    String add_way = MapUtils.getString(followUserMap, "add_way");
                    //备注
                    String remark = MapUtils.getString(followUserMap, "remark");
                    //描述
                    String description = MapUtils.getString(followUserMap, "description");
                    //添加时间
                    String createtime = MapUtils.getString(followUserMap, "createtime");
                    residentDataMap.put("userid", userid);
                    residentDataMap.put("oper_userid", oper_userid);
                    residentDataMap.put("remark_corp_name", remark_corp_name);
                    residentDataMap.put("add_way", add_way);
                    residentDataMap.put("remark", remark);
                    residentDataMap.put("description", description);
                    residentDataMap.put("createTime", createtime);
                    //标签数据
                    List<Map> tagsList = (List<Map>) MapUtils.getObject(followUserMap, "tags");
                    residentDataMap.put("tagsList", tagsList);
                    //联系人信息-居民信息
                    dataList.add(residentDataMap);
                }
            }
            //分页游标
            Integer next_cursor = MapUtils.getInteger(externalContactMap, "next_cursor");
            //说明还有数据
            if (next_cursor != null && next_cursor != 0) {
                //继续递归获取 增加参数
                qywxExternalContactParam.setNext_cursor(next_cursor);
                getFollowUserList(dataList, qywxExternalContactParam);
            }
        } else {
            log.error("============================获取外部联系人请求失败===================================");
            log.error(JSON.toJSONString(queryExternalContactInfoR));
        }

    }

    @Override
    public List<RmExternalStatisticsNum> processFinalData(List<Map> residentDataList, String corpId, String dataType) {
        List<RmExternalStatisticsNum> externalStatisticsNumList = new ArrayList<>();
        //TODO 处理最终数据
        if (CollectionUtils.isNotEmpty(residentDataList)) {
            //TODO1 居民数据入库
            for (Map residentMap : residentDataList) {
                String userid = MapUtils.getString(residentMap, "userid");
                String externalUserId = MapUtils.getString(residentMap, "externalUserId");
                residentMap.put("corpId", corpId);
                //获取头像
                String externalAvatar = externalAvatarService.getExternalAvatar(externalUserId,corpId);
                if (StringUtils.isEmpty(externalAvatar)) {
                    log.info("获取外部联系人头像失败");
                } else {
                    residentMap.put("avatar", externalAvatar);
                }
                //todo 1 保存居民基本信息
                residentMap.put("dataType", dataType);
                String userType = MapUtils.getString(residentMap, "type");
                if (StringUtils.isNotEmpty(userType) && userType.equals("2")) {
                    log.info("当前成员为企业用户 开始执行档案初始化");
                    String corpName = MapUtils.getString(residentMap, "corpName");
                    Map<String, Object> reqMap = new HashMap<>();
                    reqMap.put("corpName", corpName);
                    reqMap.put("externalUserId", externalUserId);
                    reqMap.put("userId", userid);
                    reqMap.put("corpId", corpId);
                    companyService.schedSaveCompanyExternalRation(reqMap);
                }
                R<RmExternalStatisticsNum> saveExternalInfoR = saveExternalInfo(residentMap);

                //todo 1.1 保存居民档案信息

                if (saveExternalInfoR.getCode() == R.SUCCESS) {
                    RmExternalStatisticsNum rmExternalStatisticsNum = saveExternalInfoR.getData();
                    externalStatisticsNumList.add(rmExternalStatisticsNum);
                    //TODO 2 标签数据处理，逻辑判断，入库
                    List<Map> tagsList = (List<Map>) MapUtils.getObject(residentMap, "tagsList");
                    List<Map> labelParamList = new ArrayList<>();
                    if (CollectionUtils.isNotEmpty(tagsList)) {
                        for (Map tagMap : tagsList) {
                            Map<String, Object> labelParamMap = new HashMap<>();

                            String type = MapUtils.getString(tagMap, "type");
                            if (StringUtils.isNotEmpty(type) && !type.equals("2")) {
                                //标签组名称
                                labelParamMap.put("groupName", MapUtils.getString(tagMap, "group_name"));
                                labelParamMap.put("corpId", corpId);
                                labelParamMap.put("labelName", MapUtils.getString(tagMap, "tag_name"));
                                labelParamMap.put("type", type);
                                labelParamMap.put("labelId", MapUtils.getString(tagMap, "tag_id"));
                                labelParamMap.put("userId", userid);
                                labelParamList.add(labelParamMap);
                            }

                        }
                    }
                    if (CollectionUtils.isNotEmpty(labelParamList)) {
                        //批量保存标签组和标签数据
                        R<List<Map>> qywxLabelData = labelService.saveQywxLabelData(labelParamList, corpId);
                        if (qywxLabelData.getCode() == R.SUCCESS) {
                            List<Map> labelDataData = qywxLabelData.getData();
                            if (labelDataData != null && labelDataData.size() > 0) {
                                //批量插入
                                List<RmExternalLabel> insertBatchList = new ArrayList<>();
                                //清除原关联数据
                                RmExternalLabel rmExternalLabel = new RmExternalLabel();
                                rmExternalLabel.setExternalUserId(externalUserId);
                                rmExternalLabel.setCorpId(corpId);
                                rmExternalLabel.setUserId(userid);
                                externalLabelService.deleteExternalLabel(rmExternalLabel);
                                //封装保存新标签数据
                                for (Map labelDataDataMap : labelDataData) {
                                    String labelId = MapUtils.getString(labelDataDataMap, "labelId");
                                    String labelGroupId = MapUtils.getString(labelDataDataMap, "labelGroupId");
                                    RmExternalLabel externalInfoLabel = new RmExternalLabel();
                                    externalInfoLabel.setLabelId(labelId);
                                    externalInfoLabel.setUserId(userid);
                                    externalInfoLabel.setLabelGroupId(labelGroupId);
                                    externalInfoLabel.setCorpId(corpId);
                                    externalInfoLabel.setExternalUserId(externalUserId);
                                    externalInfoLabel.setCreateTime(DateUtil.parse(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss")));
                                    externalInfoLabel.setId(UUID.randomUUID().toString());
                                    externalInfoLabel.setCreateTime(new Date());
                                    insertBatchList.add(externalInfoLabel);
                                }
                                externalLabelService.saveExternalLabelBatch(insertBatchList);
                            }
                        }
                    } else {
                        //说明成员将该居民标签移除了
                        //清除原关联数据
                        RmExternalLabel rmExternalLabel = new RmExternalLabel();
                        rmExternalLabel.setExternalUserId(externalUserId);
                        rmExternalLabel.setCorpId(corpId);
                        rmExternalLabel.setUserId(userid);
                        externalLabelService.deleteExternalLabel(rmExternalLabel);
                    }
                }
            }
        }
        return externalStatisticsNumList;
    }


    @Override
    public R<RmExternalStatisticsNum> deleteExternalContract(Map paramMap) {
        try {
            RmExternalInfo rmExternalInfo = new RmExternalInfo();
            RmExternalStatisticsNum rmExternalStatisticsNum = new RmExternalStatisticsNum();
            //企业id
            String corpId = MapUtils.getString(paramMap, "corpId");
            //外部联系人id
            String externalUserId = MapUtils.getString(paramMap, "externalUserId");
            //企业人员id
            String userId = MapUtils.getString(paramMap, "userId");
            //状态
            Integer status = MapUtils.getInteger(paramMap, "status");
            //todo 查询已存在数据
            RmExternalInfo paramExternalInfo = new RmExternalInfo();
            paramExternalInfo.setCorpId(corpId);
            paramExternalInfo.setExternalUserId(externalUserId);
            paramExternalInfo.setUserId(userId);
            List<RmExternalInfo> rmExternalInfoList = rmExternalInfoMapper.selectExternalInfoByExternalInfo(paramExternalInfo);
            if (rmExternalInfoList != null && rmExternalInfoList.size() > 0) {
                //id
                String id = rmExternalInfoList.get(0).getId();
                rmExternalInfo.setId(id);
                //已存在，查询当前状态
                Integer currentStatus = rmExternalInfoList.get(0).getStatus();
                //如果当前状态为2，说明已经把外部联系人删除，
                if (currentStatus == 2) {
                    //如果当前操作是3:被外部联系人删除，将状态更新为4，双向删除
                    if (status == 3) {
                        rmExternalInfo.setStatus(4);
                        //被外部联系人删除
                        rmExternalStatisticsNum.setIsDeletedByContact(1);
                        //被该人员删除
                        rmExternalStatisticsNum.setIsDeletedByPerson(1);
                        //流失
                        rmExternalStatisticsNum.setIsLost(1);
                        rmExternalStatisticsNum.setDataTime(new Date());
                    } else if (status == 2) {
                        rmExternalInfo.setStatus(2);
                        //被外部联系人删除
                        rmExternalStatisticsNum.setIsDeletedByContact(0);
                        //被该人员删除
                        rmExternalStatisticsNum.setIsDeletedByPerson(1);
                        //流失
                        rmExternalStatisticsNum.setIsLost(1);
                        rmExternalStatisticsNum.setDataTime(new Date());
                    }
                }
                //如果当前状态为3，说明已经被外部联系人删除，
                else if (currentStatus == 3) {
                    //如果当前操作是2：把外部联系人删除,更新为双向删除
                    if (status == 2) {
                        //被外部联系人删除
                        rmExternalStatisticsNum.setIsDeletedByContact(1);
                        //被该人员删除
                        rmExternalStatisticsNum.setIsDeletedByPerson(1);
                        //流失
                        rmExternalStatisticsNum.setIsLost(1);
                        rmExternalStatisticsNum.setDataTime(new Date());
                        rmExternalInfo.setStatus(4);
                    }
                }
                //当前为有效
                else if (currentStatus == 1) {
                    //如果当前操作是把外部联系人删除
                    if (status == 2) {
                        //判定为流失
                        //被外部联系人删除
                        rmExternalStatisticsNum.setIsDeletedByContact(0);
                        //被该人员删除
                        rmExternalStatisticsNum.setIsDeletedByPerson(1);
                        //流失
                        rmExternalStatisticsNum.setIsLost(1);
                        rmExternalStatisticsNum.setDataTime(new Date());
                    }
                    if (status == 3) {
                        //被客户删除
                        //被外部联系人删除
                        rmExternalStatisticsNum.setIsDeletedByContact(1);
                        //被该人员删除
                        rmExternalStatisticsNum.setIsDeletedByPerson(0);
                        //流失
                        rmExternalStatisticsNum.setIsLost(0);
                    }
                    rmExternalInfo.setStatus(status);
                }
                //todo 执行修改
                //修改
                rmExternalInfo.setDataUpdateTime(new Date());

                rmExternalInfoMapper.updateByPrimaryKeySelective(rmExternalInfo);

                rmExternalStatisticsNum.setExternalUserId(externalUserId);
                rmExternalStatisticsNum.setUserId(userId);
                rmExternalStatisticsNum.setCorpId(corpId);
                //为了保证数据一致性，当前服务处理完成后，返回
                //externalStatisticsNumService.saveExternalStatistics(rmExternalStatisticsNum);
                log.info("【删除好友成功，返回统计数据:" + rmExternalStatisticsNum);
                Integer deleteType = MapUtils.getInteger(paramMap, "deleteType");
                if (deleteType != null && deleteType == 2) {
                    // 获取当前时间
                    Calendar calendar = Calendar.getInstance();
                    // 将日期减去一天
                    calendar.add(Calendar.DAY_OF_YEAR, -1);
                    // 获取昨天的日期
                    Date yesterday = calendar.getTime();
                    rmExternalStatisticsNum.setDataTime(yesterday);
                }
                externalStatisticsNumMapper.updateExternalStatisticsNum(rmExternalStatisticsNum);
                return R.ok(rmExternalStatisticsNum, "删除好友成功");
            } else {
                return R.fail("删除好友失败，未查询到当前外部联系人");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("删除好友失败");
        }
    }

    /**
     * 同步相关人员的外部联系人数据
     *
     * @param map
     * @return
     */
    @Override
    public R<List<RmExternalStatisticsNum>> synExternalListByUserId(Map map) {
        try {
            String cursor = "";
            List<RmExternalInfo> rmExternalInfoList = new ArrayList<>();
            List<RmExternalLabel> rmExternalLabelList = new ArrayList<>();
            List<RmExternalStatisticsNum> rmExternalStatisticsNumList = new ArrayList<>();
            String corpId = MapUtils.getString(map, "corpId");
            String userId = MapUtils.getString(map, "userId");
            log.info("开始获取" + userId + "的外部联系人");
            log.info("入参：" + map);
            //查询企业标签
            //查询所有标签数据
            List<RmLabel> rmLabelList = rmLabelMapper.selecAllLabelByCorpId(corpId);
            //清除该人员符合条件的数据
            externalStatisticsNumMapper.deleteExternalStatisticsNumByUserId(userId, corpId);
            //清除该人员添加的外部联系人数据
            rmExternalInfoMapper.deleteByUserInfo(userId, corpId);
            //清除该人员的绑定居民标签
            rmExternalLabelMapper.deleteExternalByUserInfo(userId, corpId);
            R<List<Map<String, Object>>> dataListR = weChatDataService.queryBatchExternalcontactList(corpId, userId, cursor);
            if (dataListR.getCode() == R.SUCCESS) {
                List<Map<String, Object>> dataList = dataListR.getData();
                log.info("------------------------------查询到" + userId + "的" + dataList.size() + "条外部联系人------------------");
                if (dataList != null && dataList.size() > 0) {
                    for (Map<String, Object> dataObject : dataList) {
                        //TODO 1 居民信息主数据处理，逻辑判断，入库
                        String userid = MapUtils.getString(dataObject, "userid");
                        String remark = MapUtils.getString(dataObject, "remark");
                        String remarkCorpName = MapUtils.getString(dataObject, "remark_corp_name");
                        String operUserid = MapUtils.getString(dataObject, "oper_userid");
                        long createtime = MapUtils.getLong(dataObject, "createtime");
                        String description = MapUtils.getString(dataObject, "description");
                        String externalUserId = MapUtils.getString(dataObject, "external_userid");
                        String name = MapUtils.getString(dataObject, "name");
                        String avatar = MapUtils.getString(dataObject, "avatar");
                        String corpName = MapUtils.getString(dataObject, "corp_name");
                        String corpFullName = MapUtils.getString(dataObject, "corp_full_name");
                        String externaltype = MapUtils.getString(dataObject, "type");
                        String gender = MapUtils.getString(dataObject, "gender");
                        String add_way = MapUtils.getString(dataObject, "add_way");
                        String unionid = MapUtils.getString(dataObject, "unionid");
                        String position = MapUtils.getString(dataObject, "position");
                        RmExternalInfo externalInfo = new RmExternalInfo();
                        externalInfo.setExternalUserId(externalUserId);
                        externalInfo.setName(name);
                        externalInfo.setAvatar(avatar);
                        externalInfo.setCorpName(corpName);
                        externalInfo.setCorpFullName(corpFullName);
                        externalInfo.setType(externaltype);
                        externalInfo.setGender(gender);
                        externalInfo.setCorpId(corpId);
                        externalInfo.setAddWay(add_way);
                        externalInfo.setUnionId(unionid);
                        externalInfo.setPosition(position);
                        externalInfo.setUserId(userid);
                        externalInfo.setRemark(remark);
                        externalInfo.setRemarkCorpName(remarkCorpName);
                        externalInfo.setOperUserid(operUserid);
                        RmExternalStatisticsNum rmExternalStatisticsNum = new RmExternalStatisticsNum();
                        if (ObjectUtils.isEmpty(createtime)) {
                            externalInfo.setCreateTime(new Date());
                            rmExternalStatisticsNum.setAddTime(new Date());
                        } else {
                            DateTime date = DateUtil.date(createtime * 1000);// 需要将秒数转换为毫秒数
                            externalInfo.setCreateTime(date);
                            rmExternalStatisticsNum.setAddTime(date);
                        }
                        rmExternalStatisticsNum.setExternalUserId(externalUserId);
                        rmExternalStatisticsNum.setUserId(userid);
                        rmExternalStatisticsNum.setCorpId(corpId);
                        //未被外部联系人删除
                        rmExternalStatisticsNum.setIsDeletedByContact(0);
                        //外部联系人未被删除
                        rmExternalStatisticsNum.setIsDeletedByPerson(0);
                        //未流失
                        rmExternalStatisticsNum.setIsLost(0);
                        externalInfo.setDescription(description);
                        //根据  externalUserId 和  operUserId去MongoDB查询  没有就新增
                        //查询存不存在
                        RmExternalInfo paramExternalInfo = new RmExternalInfo();
                        paramExternalInfo.setCorpId(corpId);
                        paramExternalInfo.setExternalUserId(externalUserId);
                        paramExternalInfo.setUserId(userid);
                        externalInfo.setStatus(1);
                        externalInfo.setDescription(description);
                        //新增
                        externalInfo.setId(UUID.randomUUID().toString());
                        externalInfo.setDataCreateTime(new Date());
                        List<String> tagIdList = (List<String>) dataObject.get("tagIdList");
                        log.info("当前外部联系人标签："+JSON.toJSONString(tagIdList));
                        if (tagIdList != null && tagIdList.size() > 0) {
                            for (String tageId : tagIdList) {
                                if(StringUtils.isNotEmpty(tageId)){
                                    List<RmLabel> labelList = rmLabelList.stream().filter(p -> p.getLabelId().equals(tageId)).collect(Collectors.toList());
                                    if (labelList != null && labelList.size() > 0) {
                                        RmLabel label = labelList.get(0);
                                        String labelId = label.getLabelId();
                                        String groupId = label.getGroupId();
                                        RmExternalLabel rmExternalLabel = new RmExternalLabel();
                                        rmExternalLabel.setId(UUID.randomUUID().toString());
                                        rmExternalLabel.setUserId(userId);
                                        rmExternalLabel.setCorpId(corpId);
                                        rmExternalLabel.setLabelGroupId(groupId);
                                        rmExternalLabel.setLabelId(labelId);
                                        rmExternalLabel.setExternalUserId(externalUserId);
                                        rmExternalLabel.setCreateTime(new Date());
                                        rmExternalLabelList.add(rmExternalLabel);
                                    }
                                }

                            }
                        }
                        rmExternalInfoList.add(externalInfo);

                        rmExternalStatisticsNumList.add(rmExternalStatisticsNum);
                    }
                }
            } else {
                return R.fail(dataListR.getMsg());
            }
            // 每次插入的批量数量
            int batchSize = 5000;
            // 检查结果集合是否非空并且包含数据
            if (CollectionUtils.isNotEmpty(rmExternalStatisticsNumList)) {
                /*for (int i = 0; i < rmExternalStatisticsNumList.size(); i += batchSize) {
                    // 计算截取的结束位置
                    int endIndex = Math.min(i + batchSize, rmExternalStatisticsNumList.size());
                    // 获取子列表，即每次要插入的数据批量
                    List<RmExternalStatisticsNum> insertRmExternalStatisticsNumList = rmExternalStatisticsNumList.subList(i, endIndex);
                    // 将 batchList 内的数据批量插入到数据库
                    externalStatisticsNumMapper.insertExternalStatisticsNumBatch(insertRmExternalStatisticsNumList);
                }*/
                List<List<RmExternalStatisticsNum>> batchList1 = Lists.partition(rmExternalStatisticsNumList, 2000);
                if (batchList1 != null && batchList1.size() > 0) {
                    for (List<RmExternalStatisticsNum> rmExternalStatisticsNums : batchList1) {
                        externalStatisticsNumMapper.insertExternalStatisticsNumBatch(rmExternalStatisticsNums);
                    }
                }
            }

            //todo 居民信息批量
            // 每次插入的批量数量
            int batchSize2 = 5000;
            // 检查结果集合是否非空并且包含数据
            if (CollectionUtils.isNotEmpty(rmExternalInfoList)) {
                List<List<RmExternalInfo>> batchList2 = Lists.partition(rmExternalInfoList, 2000);
                if (batchList2 != null && batchList2.size() > 0) {
                    for (List<RmExternalInfo> rmExternalInfos : batchList2) {
                        // 将 batchList 内的数据批量插入到数据库
                        rmExternalInfoMapper.insertSelectiveBatch(rmExternalInfos);
                    }
                }
            }
            //todo 居民关联标签
            int batchSize3 = 5000;
            // 检查结果集合是否非空并且包含数据
            if (CollectionUtils.isNotEmpty(rmExternalLabelList)) {
                List<List<RmExternalLabel>> batchList3 = Lists.partition(rmExternalLabelList, 2000);
                if (batchList3 != null && batchList3.size() > 0) {
                    for (List<RmExternalLabel> rmExternalLabels : batchList3) {
                        // 将 batchList 内的数据批量插入到数据库
                        rmExternalLabelMapper.insertExternalLabelSelectiveBatch(rmExternalLabels);
                    }
                }
            }

//
//            if (CollectionUtils.isNotEmpty(rmExternalStatisticsNumList)) {
//                List<List<RmExternalStatisticsNum>> batchList1 = Lists.partition(rmExternalStatisticsNumList, 2000);
//                if (batchList1 != null && !batchList1.isEmpty()) {
//                    for (List<RmExternalStatisticsNum> rmExternalStatisticsNums : batchList1) {
//                        List<RmEsSynData> RMesDataList = rmExternalStatisticsNums.stream()
//                                .filter(rm -> rm != null) // 添加此过滤器以移除null元素
//                                .map(rm -> {
//                                    // 在使用字段前检查它们是否为null
//                                    if (rm.getExternalUserId() != null && rm.getUserId() != null && rm.getCorpId() != null) {
//                                        return new RmEsSynData(rm.getExternalUserId(), rm.getUserId(), 1, rm.getCorpId(), EsSynDataUpdateEnum.ADD_EXTERNAL.getCode());
//                                    }
//                                    return null; // 如果任何字段为null，则返回null
//                                })
//                                .filter(Objects::nonNull) // 移除上一步映射操作产生的null元素
//                                .collect(Collectors.toList());
//                        // 如果RMesDataList非空，则继续保存批次
//                        if (!RMesDataList.isEmpty()) {
//                            rmEsSynDataService.saveBatch(RMesDataList);
//                        }
//                    }
//                }
//            }


            log.info("=======================人员" + userId + "的数据批量处理成功================================");
            return R.ok();
        } catch (Exception e) {
            e.printStackTrace();

            return R.fail("处理失败，发生异常");
        }
    }


    /**
     * 同步联系人 企业/个人
     *
     * @param map
     * @return
     */
    @Override
    public R synExternalContacts(Map map) {
        try {
            String corpId = MapUtils.getString(map, "corpId");
            String suiteId = MapUtils.getString(map, "suiteId");
            String queryUserId = MapUtils.getString(map, "userId");
            if (StringUtils.isNotEmpty(queryUserId)) {
                String userId = queryUserId;
                log.info("=================开始处理" + userId + "的外部联系人数据==================");
                map.put("userId", userId);
                map.put("corpId", corpId);
                //查询是否已处理
                RmUserExternalSyn rmUserExternalSyn = new RmUserExternalSyn();
                rmUserExternalSyn.setUserId(userId);
                rmUserExternalSyn.setCorpId(corpId);
                rmUserExternalSyn.setSynType(0);
                RmUserExternalSyn userExternalSyn = rmUserExternalSynMapper.selectIsSynContractByUserId(rmUserExternalSyn);
                if (userExternalSyn != null && userExternalSyn.getIsSynContacts() == 1) {
                    log.info("=================" + userId + "的外部联系人数据已经处理，无需重复处理==================");
                    return R.ok();
                } else {
                    rmUserExternalSyn.setId(UUID.randomUUID().toString());
                    //开始处理
                    R<List<RmExternalStatisticsNum>> synResult = synExternalListByUserId(map);
                    if (synResult.getCode() == R.SUCCESS) {
                        //处理结束
                        rmUserExternalSyn.setIsSynContacts(1);
                        rmUserExternalSyn.setSynDate(new Date());
                        rmUserExternalSyn.setSynType(0);
                        rmUserExternalSynMapper.insertSelective(rmUserExternalSyn);
                    }
                }
                log.info("=================处理" + userId + "的外部联系人数据结束==================");
            } else {
                //查询所有人员
                WeChatCorpUser weChatCorpUser = new WeChatCorpUser();
                weChatCorpUser.setCorpId(corpId);
                //不过滤状态
                //qywxCorpUser.setStatus(1);
                weChatCorpUser.setSuiteId(suiteId);
                R<List<WeChatCorpUser>> selectCorpAllUser = weChatCorpUserService.selectAllUserByCorpId(weChatCorpUser);
                if (selectCorpAllUser.getCode() == R.SUCCESS && selectCorpAllUser.getData() != null) {
                    List<WeChatCorpUser> qywxCorpUserList = selectCorpAllUser.getData();
                    for (WeChatCorpUser corpUser : qywxCorpUserList) {
                        String userId = corpUser.getUserId();
                        log.info("=================开始处理" + userId + "的外部联系人数据==================");
                        //查询是否已处理
                        RmUserExternalSyn rmUserExternalSyn = new RmUserExternalSyn();
                        rmUserExternalSyn.setUserId(userId);
                        rmUserExternalSyn.setCorpId(corpId);
                        rmUserExternalSyn.setSynType(0);
                        RmUserExternalSyn userExternalSyn = rmUserExternalSynMapper.selectIsSynContractByUserId(rmUserExternalSyn);
                        if (userExternalSyn != null && userExternalSyn.getIsSynContacts() == 1) {
                            log.info("=================" + userId + "的外部联系人数据已经处理，无需重复处理==================");
                            continue;
                        } else {
                            Map<String, Object> dataMap = new HashMap<>();
                            dataMap.put("userId", userId);
                            dataMap.put("corpId", corpId);
                            rmUserExternalSyn.setId(UUID.randomUUID().toString());
                            //开始处理
                            R<List<RmExternalStatisticsNum>> synResult = synExternalListByUserId(dataMap);
                            if (synResult.getCode() == R.SUCCESS) {
                                rmUserExternalSyn.setSynDate(new Date());
                                rmUserExternalSyn.setIsSynContacts(1);
                                rmUserExternalSyn.setSynType(0);
                                //处理结束
                                rmUserExternalSynMapper.insertSelective(rmUserExternalSyn);
                            }
                            log.info("=================处理" + userId + "的外部联系人数据结束==================");
                            //更新处理状态
                        }
                    }
                } else {
                    return R.fail("未处理任何数据");
                }
            }
            log.info("=================外部联系人处理结束==================");
            return R.ok();
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("处理失败，发生异常:" + e.getMessage());
        }
    }

    @Override
    public R<Map> selectExternalFileInfo(RmExternalInfo rmExternalInfo) {
        try {
            Map<String, Object> resultMap = new HashMap<>();
            //居民基本信息
            List<RmExternalInfo> rmExternalInfoList = rmExternalInfoMapper.selectExternalInfoByExternalInfo(rmExternalInfo);
            if (CollectionUtils.isNotEmpty(rmExternalInfoList)) {
                resultMap.put("externalInfo", rmExternalInfoList.get(0));
            }
            //根据  externalUserId 和  corpId去查询居民档案信息
            RmExternalInfoArchives rmExternalInfoArchives = new RmExternalInfoArchives();
            rmExternalInfoArchives.setCorpId(rmExternalInfo.getCorpId());
            rmExternalInfoArchives.setExternalUserId(rmExternalInfo.getExternalUserId());
            //查询值不为空的
            rmExternalInfoArchives.setInfoValue("NOTNULL");
            List<RmExternalInfoArchives> externalInfoArchives = rmExternalInfoArchivesMapper.selectExternalInfoArchivesByExternalInfoArchives(rmExternalInfoArchives);
            if (CollectionUtils.isNotEmpty(externalInfoArchives)) {
                //居民档案信息
                resultMap.put("externalOneFileList", externalInfoArchives);
                return R.ok(resultMap, "居民档案查询成功");
            }
            return R.ok(resultMap);
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("居民档案，查询失败");
        }
    }

    @Override
    public R saveExternalArchives(Map<String, Object> map) {
        try {
            String externalUserId = MapUtils.getString(map, "externalUserId");
            String corpId = MapUtils.getString(map, "corpId");
            String userId = MapUtils.getString(map, "userId");
            RmExternalInfoArchives rmExternalInfoArchives = new RmExternalInfoArchives();
            rmExternalInfoArchives.setCorpId(corpId);
            rmExternalInfoArchives.setExternalUserId(externalUserId);
            List<RmExternalInfoArchives> externalOneFile = rmExternalInfoArchivesMapper.selectExternalInfoArchivesByExternalInfoArchives(rmExternalInfoArchives);
            //删除当前字段数据
            if(map!=null&&map.size()>0){
                for (String key : map.keySet()) {
                    Map<String, Object> paramMap = new HashMap<>();
                    paramMap.put("infoId",key);
                    paramMap.put("externalUserId",externalUserId);
                    paramMap.put("corpId",corpId);
                    rmExternalInfoArchivesMapper.deleteExternalArchivesByInfoId(paramMap);
                }
            }
            String[] keys = new String[] {
                    "externalUserId", "corpId", "userId", "suiteId"
                };
            if (CollectionUtils.isNotEmpty(externalOneFile)) {
                // 添加键值对到 map
                for (String key : map.keySet()) {
                    if(StringUtils.isNotEmpty(key)){
                        boolean contains = Arrays.asList(keys).contains(key);
                        if(contains){
                            continue;
                        }
                    }else{
                        continue;
                    }

                    Object newObject = map.get(key);
                    String newValue = "";
                    //获取原来的值
                    if (newObject != null) {
                        newValue = newObject.toString();
                    } else {
                        newValue = "";
                    }
                    List<RmExternalInfoArchives> externalOneFiles = externalOneFile.stream().filter(p -> p.getInfoId().equals(key)).collect(Collectors.toList());
                    //存在
                    if (CollectionUtils.isNotEmpty(externalOneFiles)) {
                        String id = externalOneFiles.get(0).getId();
                        rmExternalInfoArchives.setId(id);
                        rmExternalInfoArchives.setInfoValue(newValue);
                        rmExternalInfoArchives.setUpdateTime(new Date());
                        rmExternalInfoArchives.setUpdateUserId(userId);
                        rmExternalInfoArchives.setSourceType(1);
                        rmExternalInfoArchives.setId(UUID.randomUUID().toString());
                        rmExternalInfoArchives.setCreateTime(new Date());
                        rmExternalInfoArchives.setCreateUserId(userId);
                        rmExternalInfoArchives.setInfoId(externalOneFiles.get(0).getInfoId());
                        if(StringUtils.isNotEmpty(newValue)){
                            rmExternalInfoArchivesMapper.insertSelective(rmExternalInfoArchives);
                        }

                        //查询新旧值是否一致
                        RmExternalInfoArchives oldInfoArchives = externalOneFiles.get(0);
                        String edinfoValue = oldInfoArchives.getInfoValue();
                        if (StringUtils.isEmpty(edinfoValue)) {
                            edinfoValue = "";
                        }
                        if (!edinfoValue.equals(newValue)) {
                            //记录更改记录
                            RmExternalInfoArchivesHistory externalOneFileHistory = new RmExternalInfoArchivesHistory();
                            externalOneFileHistory.setUserId(userId);
                            externalOneFileHistory.setId(UUID.randomUUID().toString());
                            externalOneFileHistory.setExternalUserId(externalUserId);
                            externalOneFileHistory.setCorpId(corpId);
                            externalOneFileHistory.setSourceType(1);
                            externalOneFileHistory.setInfoId(key);
                            externalOneFileHistory.setInfoValue(newValue);
                            externalOneFileHistory.setDate(new Date());
                            //添加历史记录
                            rmExternalInfoArchivesHistoryMapper.insertSelective(externalOneFileHistory);
                        }
                    } else {
                        if (StringUtils.isNotEmpty(newValue)) {
                            //新增
                            RmExternalInfoArchives rmExternalInfoArchivesData = new RmExternalInfoArchives();
                            rmExternalInfoArchivesData.setCreateUserId(userId);
                            rmExternalInfoArchivesData.setCreateTime(new Date());
                            rmExternalInfoArchivesData.setInfoId(key);
                            rmExternalInfoArchivesData.setInfoValue(newValue);
                            rmExternalInfoArchivesData.setCorpId(corpId);
                            rmExternalInfoArchivesData.setExternalUserId(externalUserId);
                            rmExternalInfoArchivesData.setId(UUID.randomUUID().toString());
                            rmExternalInfoArchivesMapper.insertSelective(rmExternalInfoArchivesData);
                            //添加历史记录
                            RmExternalInfoArchivesHistory externalOneFileHistory = new RmExternalInfoArchivesHistory();
                            externalOneFileHistory.setUserId(userId);
                            externalOneFileHistory.setExternalUserId(externalUserId);
                            externalOneFileHistory.setCorpId(corpId);
                            externalOneFileHistory.setInfoId(key);
                            externalOneFileHistory.setInfoValue(newValue);
                            externalOneFileHistory.setDate(new Date());
                            externalOneFileHistory.setId(UUID.randomUUID().toString());
                            //添加历史记录
                            rmExternalInfoArchivesHistoryMapper.insertSelective(externalOneFileHistory);
                        }

                    }
                }
            } else {
                //添加历史记录
                for (String key : map.keySet()) {
                    String newValue = "";
                    Object newObject = map.get(key);
                    if (newObject != null) {
                        newValue = newObject.toString();
                        RmExternalInfoArchivesHistory externalOneFileHistory = new RmExternalInfoArchivesHistory();
                        externalOneFileHistory.setUserId(userId);
                        externalOneFileHistory.setExternalUserId(externalUserId);
                        externalOneFileHistory.setCorpId(corpId);
                        externalOneFileHistory.setInfoId(key);
                        externalOneFileHistory.setInfoValue(newValue);
                        externalOneFileHistory.setDate(new Date());
                        externalOneFileHistory.setId(UUID.randomUUID().toString());
                        //添加历史记录
                        rmExternalInfoArchivesHistoryMapper.insertSelective(externalOneFileHistory);
                    }
                    RmExternalInfoArchives externalOneFileData = new RmExternalInfoArchives();
                    externalOneFileData.setCreateUserId(userId);
                    externalOneFileData.setCreateTime(new Date());
                    externalOneFileData.setInfoId(key);
                    externalOneFileData.setInfoValue(newValue);
                    externalOneFileData.setCorpId(corpId);
                    externalOneFileData.setExternalUserId(externalUserId);
                    externalOneFileData.setId(UUID.randomUUID().toString());
                    rmExternalInfoArchivesMapper.insertSelective(externalOneFileData);
                }
            }
            return R.ok("居民档案保存成功");
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("居民档案保存失败，发生异常:" + e.getMessage());
        }
    }

    @Override
    public R<RmExternalInfo> selectExternalInfoByExternalUserId(RmExternalInfo externalInfo) {
        try {
            List<RmExternalInfo> rmExternalInfoList = rmExternalInfoMapper.selectExternalInfoByExternalInfo(externalInfo);
            //查询真实姓名
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("controlName", "姓名");
            paramMap.put("corpId", externalInfo.getCorpId());
            paramMap.put("externalUserId", externalInfo.getExternalUserId());
            Map resultMap = rmExternalInfoArchivesMapper.selectExternalInfoArchivesByInfo(paramMap);
            if (CollectionUtils.isNotEmpty(rmExternalInfoList)) {
                RmExternalInfo rmExternalInfo = rmExternalInfoList.get(0);
                if (resultMap != null) {
                    String infoValue = MapUtils.getString(resultMap, "info_value");
                    rmExternalInfo.setRealName(infoValue);
                }
                return R.ok(rmExternalInfo);
            } else {
                return R.fail("查询居民基本信息失败 未查询到相关信息");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("查询居民基本信息失败，发生异常");
        }

    }

    @Override
    public R updateExternalContactAvatar(RmExternalInfo rmExternalInfo) {
        try {
            RmExternalInfo update = new RmExternalInfo();
            update.setAvatar(rmExternalInfo.getAvatar());
            update.setExternalUserId(rmExternalInfo.getExternalUserId());
            update.setDataUpdateTime(new Date());
            update.setCorpId(rmExternalInfo.getCorpId());
            rmExternalInfoMapper.updateExternalAvatarByExternalUserId(update);
            return R.ok("头像更新成功");
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("头像更新失败");
        }

    }


    @Override
    public R getUnassignedExternalContacts(Map paramMap) {
        List<Map> dataList = new ArrayList<>();
        //第一次分页游标参数设置为空
        paramMap.put("cursor", null);
        //封装数据
        getUnassignedList(dataList, paramMap);

        // 根据 handover_userid 将数据重新组织
        Map<String, List<Map<String, Object>>> groupedData = new HashMap<>();
        for (Map dataMap : dataList) {
            String handoverUserId = MapUtils.getString(dataMap, "handover_userid");
            List<Map<String, Object>> infoList = (List<Map<String, Object>>) dataMap.get("info");

            groupedData.computeIfAbsent(handoverUserId, k -> new ArrayList<>())
                    .addAll(infoList);
        }

        // 构建最终返回结果
        List<Map> result = groupedData.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("handover_userid", entry.getKey());
                    map.put("info", entry.getValue());
                    return map;
                })
                .collect(Collectors.toList());

        return R.ok(result);
    }


    public void getUnassignedList(List<Map> dataList, Map paramMap) {
        String corpId = MapUtils.getString(paramMap, "corpId");
        String cursor = MapUtils.getString(paramMap, "cursor");

        R<Map> getUnassignedListR = weChatDataService.getUnassignedList(corpId, cursor);
        if (getUnassignedListR.getCode() == R.SUCCESS) {
            Map getUnassignedMap = getUnassignedListR.getData();
            List<Map> infoList = (List<Map>) MapUtils.getObject(getUnassignedMap, "info");

            if (CollectionUtils.isNotEmpty(infoList)) {
                // 分组处理
                Map<String, List<Map<String, Object>>> groupedByHandoverUserId = new HashMap<>();
                for (Map infoMap : infoList) {
                    String handoverUserId = MapUtils.getString(infoMap, "handover_userid");
                    List<Map<String, Object>> groupList = groupedByHandoverUserId.computeIfAbsent(handoverUserId, k -> new ArrayList<>());

                    Map<String, Object> info = new HashMap<>();
                    info.put("external_userid", MapUtils.getString(infoMap, "external_userid"));
                    info.put("dimission_time", MapUtils.getString(infoMap, "dimission_time"));
                    groupList.add(info);
                }

                // 将分组数据添加到 dataList
                groupedByHandoverUserId.forEach((key, value) -> {
                    Map<String, Object> unassignedMap = new HashMap<>();
                    unassignedMap.put("handover_userid", key);
                    unassignedMap.put("info", value);
                    dataList.add(unassignedMap);
                });
            }

            // 判断是否有分页游标   is_last为false  才会出现next_cursor字段
            if (!MapUtils.getBoolean(getUnassignedMap, "is_last")) {
                String nextCursor = MapUtils.getString(getUnassignedMap, "next_cursor");
                paramMap.put("cursor", nextCursor);
                // 递归调用
                getUnassignedList(dataList, paramMap);
            }
        } else {
            log.error("============================获取未分配的外部联系人请求失败===================================");
            log.error(JSON.toJSONString(getUnassignedListR));
        }
    }

}
