package com.cube.wechat.thirdapp.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cube.common.core.domain.R;
import com.cube.common.utils.StringUtils;
import com.cube.wechat.thirdapp.constant.LabelConstants;
import com.cube.wechat.thirdapp.entiy.LoginUser;
import com.cube.wechat.thirdapp.entiy.RmLabel;
import com.cube.wechat.thirdapp.entiy.RmLabelGroup;
import com.cube.wechat.thirdapp.entiy.TagParam;
import com.cube.wechat.thirdapp.mapper.RmExternalLabelMapper;
import com.cube.wechat.thirdapp.mapper.RmLabelGroupMapper;
import com.cube.wechat.thirdapp.mapper.RmLabelMapper;
import com.cube.wechat.thirdapp.param.LabelParam;
import com.cube.wechat.thirdapp.service.ILabelService;
import com.cube.wechat.thirdapp.service.WeChatDataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 标签 服务层实现
 *
 * @author 张云龙
 */

@Service
@Slf4j
public class LabelServiceImpl implements ILabelService {

    @Autowired
    private WeChatDataService weChatDataService;

//    @Autowired
//    private RmEsSynDataService rmEsSynDataService;

    @Autowired
    private RmLabelGroupMapper rmLabelGroupMapper;
    @Autowired
    private RmLabelMapper rmLabelMapper;

    @Autowired
    private RmExternalLabelMapper rmExternalLabelMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<List<Map>> saveQywxLabelData(List<Map> mapList, String corpId) {
        List<Map> dataList = new ArrayList<>();
        //查询企业已存在标签
        RmLabelGroup rmLabelGroup = new RmLabelGroup();
        rmLabelGroup.setCorpId(corpId);
        List<RmLabelGroup> rmLabelGroups = rmLabelGroupMapper.selectLabelGroupByCorpId(rmLabelGroup);

        //查询所有标签数据
        List<RmLabel> rmLabelList = rmLabelMapper.selecAllLabelByCorpId(corpId);
        if (CollectionUtils.isEmpty(mapList)) {
            return R.ok();
        } else {
            log.info("企业微信关联label:" + JSON.toJSONString(mapList));
            for (Map labelMap : mapList) {
                String groupName = MapUtils.getString(labelMap, "groupName");
                String labelName = MapUtils.getString(labelMap, "labelName");
                String labelId = MapUtils.getString(labelMap, "labelId");
                List<RmLabelGroup> collect = rmLabelGroups.stream().filter(p -> p.getGroupName().equals(groupName)).collect(Collectors.toList());
                //标签组存在
                if (CollectionUtils.isNotEmpty(collect)) {
                    String groupId = collect.get(0).getGroupId();
                    //查询对应标签是否存在
                    if (CollectionUtils.isNotEmpty(rmLabelList)) {
                        List<RmLabel> labelCollectors = rmLabelList.stream().filter(p -> (p.getLabelName().equals(labelName) && p.getGroupId().equals(groupId)) || (p.getLabelId().equals(labelId) && p.getGroupId().equals(groupId))).collect(Collectors.toList());
                        //标签存在
                        if (CollectionUtils.isNotEmpty(labelCollectors)) {
                            String id = labelCollectors.get(0).getLabelId();
                            Map<String, Object> resultItemMap = new HashMap<>();
                            resultItemMap.put("labelId", id);
                            resultItemMap.put("labelGroupId", groupId);
                            dataList.add(resultItemMap);
                        }
                    }
                }
            }

            log.info("居民关联标签：" + dataList);
            return R.ok(dataList);
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public R deleteLabelAndGroup(LabelParam labelParam) {
        //获取用户id
        LoginUser currentUser = LoginUser.getCurrentUser();
        String userId = currentUser.getUserId();
        String corpId = currentUser.getCorpId();
        //根据标签组id删除标签组和  标签组下的标签
        if (ObjectUtil.isNotEmpty(labelParam.getGroupId())) {
            RmLabelGroup rmLabelGroup = new RmLabelGroup();
            //1：删除
            rmLabelGroup.setIsDelete(1);
            rmLabelGroup.setId(labelParam.getGroupId());
            //查询
            RmLabelGroup labelGroup = rmLabelGroupMapper.selectByPrimaryKey(labelParam.getGroupId());
            if (ObjectUtil.isEmpty(labelGroup)) {
                return R.fail("标签组数据不存在");
            }
            if (labelGroup.getIsSystem() == 1) {
                return R.fail("系统数据不允许删除");
            }

            // 企业微信创建  不允许删除
            if (labelGroup.getCreateUser().equals(LabelConstants.QywxCreate)) {
                return R.fail("该标签由企微创建，不允许删除!若要删除请至企微后台进行操作");
            }

            //调企微 同步删除标签组   删除标签组后  标签组下的子标签  企微会全部删除
            R delCorpTag = delCorpTag(null, corpId, labelGroup.getGroupId());
            if (delCorpTag.getCode() != R.SUCCESS) {
                return R.fail(delCorpTag.getMsg());
            }

            //删除标签组
            rmLabelGroupMapper.updateByPrimaryKeySelective(rmLabelGroup);

            //根据标签组id和企业id删除rm_external_label表 外部联系人与标签的关联关系
            rmExternalLabelMapper.deleteByLabelIdAndlabelGroupId(null, corpId, labelGroup.getId());
            rmExternalLabelMapper.deleteByLabelIdAndlabelGroupId(null, corpId, labelGroup.getGroupId());

            RmLabel rmLabel = new RmLabel();
            rmLabel.setGroupId(labelGroup.getGroupId());
            rmLabel.setCorpId(corpId);
            // 1是删除
            rmLabel.setIsDelete(1);
            rmLabel.setUpdateBy(userId);
            rmLabel.setUpdateTime(new Date());
            //删除标签组下 所有的标签
            rmLabelMapper.updateByGroupId(rmLabel);
            // todo 同步 es
           // rmEsSynDataService.saveEsData(null, null, corpId, EsSynDataUpdateEnum.DELETE_LABEL.getCode(), 0);

        } else {

            //只删除标签
            if (CollectionUtils.isNotEmpty(labelParam.getLabelIds())) {
                for (String labelId : labelParam.getLabelIds()) {
                    RmLabel rmLabel = new RmLabel();
                    rmLabel.setId(labelId);
                    rmLabel.setIsDelete(1);
                    rmLabel.setUpdateBy(userId);
                    rmLabel.setUpdateTime(new Date());
                    //查看是否删除成功
                    RmLabel label = rmLabelMapper.selectByPrimaryKey(labelId);
                    if (ObjectUtil.isEmpty(label)) {
                        return R.fail("标签数据不存在");
                    }

                    if (label.getIsSystem() == 1) {
                        return R.fail("系统数据不允许删除");
                    }
                    // 企业微信创建  不允许删除
                    if (label.getCreateBy().equals(LabelConstants.QywxCreate)) {
                        return R.fail("该标签由企微创建，不允许删除!若要删除请至企微后台进行操作");
                    }

                    //调企微 同步删除标签 标签删除后 标签组会自动删除
                    R delCorpTag = delCorpTag(label.getLabelId(), corpId, null);
                    if (delCorpTag.getCode() != R.SUCCESS) {
                        return R.fail(delCorpTag.getMsg());
                    }

                    //删除标签
                    rmLabelMapper.updateByPrimaryKeySelective(rmLabel);
                    //根据标签id和企业id删除rm_external_label表 外部联系人与标签的关联关系
                    rmExternalLabelMapper.deleteByLabelIdAndlabelGroupId(label.getId(), corpId, null);
                    rmExternalLabelMapper.deleteByLabelIdAndlabelGroupId(label.getLabelId(), corpId, null);

                    // 如果标签删除完成  就查标签组下是否为空 为空就删标签组
                    List<RmLabel> rmLabels = rmLabelMapper.selectLabelByGroupIdAndCorpId(label);
                    //标签组下子标签为空后 标签组会自动删除
                    if (CollectionUtils.isEmpty(rmLabels)) {
                        RmLabelGroup rmLabelGroup = new RmLabelGroup();
                        rmLabelGroup.setGroupId(label.getGroupId());
                        rmLabelGroup.setCorpId(corpId);
                        rmLabelGroup.setIsDelete(1);
                        rmLabelGroupMapper.updateByGroupIdAndCorpId(rmLabelGroup);
                    }
                    //todo 同步 es
                  //  rmEsSynDataService.saveEsData(null, null, corpId, EsSynDataUpdateEnum.DELETE_LABEL.getCode(), 0);
                }
            }
        }
        return R.ok();
    }


    @Override
    public R isTop(LabelParam labelParam) {

        if (CollectionUtils.isNotEmpty(labelParam.getGroupIds())) {
            for (String groupId : labelParam.getGroupIds()) {
                RmLabelGroup rmLabelGroup = new RmLabelGroup();
                rmLabelGroup.setId(groupId);

                //前端可判断 1 传true 更新成0    0传false  更新成1
                //true 是置顶  false 否
                if (labelParam.getIsTop()) {
                    //置顶
                    rmLabelGroup.setIsTop(1);
                    rmLabelGroupMapper.updateByPrimaryKeySelective(rmLabelGroup);

                } else {
                    //取消置顶
                    rmLabelGroup.setIsTop(0);
                    rmLabelGroupMapper.updateByPrimaryKeySelective(rmLabelGroup);

                }

            }

        }
        return R.ok();
    }

    @Override
    public R getGroupAndLabel(String corpId) {
        //获取用户id
        LoginUser currentUser = LoginUser.getCurrentUser();
        if (StringUtils.isEmpty(corpId)) {
            corpId = currentUser.getCorpId();
        }
        RmLabelGroup rmLabelGroup = new RmLabelGroup();
        rmLabelGroup.setCorpId(corpId);
        //根据企业查询标签组
        List<RmLabelGroup> rmLabelGroups = rmLabelGroupMapper.selectLabelGroupByCorpId(rmLabelGroup);
        if (CollectionUtils.isNotEmpty(rmLabelGroups)) {
            for (RmLabelGroup labelGroup : rmLabelGroups) {
                //构造查询参数
                RmLabel rmLabel = new RmLabel();
                rmLabel.setGroupId(labelGroup.getGroupId());
                rmLabel.setCorpId(corpId);
                //根据groupId 和 corpId  去标签表里查
                List<RmLabel> rmLabelList = rmLabelMapper.selectLabelByGroupIdAndCorpId(rmLabel);
                labelGroup.setLabelList(rmLabelList);
            }
        }
        return R.ok(rmLabelGroups);
    }

    @Override
    public R updateLabelName(LabelParam labelParam) {
        //获取用户id
        LoginUser currentUser = LoginUser.getCurrentUser();
        String userId = currentUser.getUserId();
        String corpId = currentUser.getCorpId();

        //如果标签组id 和标签组名称都不为空
        if (StringUtils.isNotEmpty(labelParam.getGroupId()) && StringUtils.isNotEmpty(labelParam.getGroupName())) {
            RmLabelGroup labelGroup = rmLabelGroupMapper.selectByPrimaryKey(labelParam.getGroupId());

            if (ObjectUtil.isEmpty(labelGroup) || labelGroup.getIsDelete() == 1) {
                return R.fail("没有该数据");
            }
            if (labelGroup.getIsSystem() == 1) {
                return R.fail("系统数据不允许修改");
            }
            if (labelGroup.getCreateUser().equals(LabelConstants.QywxCreate)) {
                return R.fail("该标签由企微创建，不允许修改!若要修改请至企微后台进行操作");
            }


//            RmLabelGroup searchGroup = new RmLabelGroup();
//            searchGroup.setGroupName(labelParam.getGroupName());
//            searchGroup.setCorpId(corpId);
//            //查询标签组是否已存在
//            List<RmLabelGroup> labelGroupList = rmLabelGroupMapper.selectLabelGroupByGroupName(searchGroup);
//            if (CollectionUtils.isNotEmpty(labelGroupList)) {
//                for (RmLabelGroup group : labelGroupList) {
//                    if (!group.getId().equals(labelGroup.getId())) {
//                        return R.fail("标签名称已存在，不允许修改");
//                    }
//                }
//            }

            //远程调用企微 编辑标签组名称
            R editCorpTag = editCorpTag(labelGroup.getGroupId(), labelParam.getGroupName(), corpId);
            if (editCorpTag.getCode() != R.SUCCESS) {
                return R.fail(editCorpTag.getMsg());
            }

            //去修改标签组名称
            RmLabelGroup rmLabelGroup = new RmLabelGroup();
            rmLabelGroup.setId(labelParam.getGroupId());
            rmLabelGroup.setGroupName(labelParam.getGroupName());
            rmLabelGroupMapper.updateByPrimaryKeySelective(rmLabelGroup);
            //todo 同步 es
           // rmEsSynDataService.saveEsData(null, null, corpId, EsSynDataUpdateEnum.UPDATE_LABEL.getCode(), 0);
        }
        //如果标签id 和标签名称都不为空
        if (StringUtils.isNotEmpty(labelParam.getLabelName()) && StringUtils.isNotEmpty(labelParam.getLabelId())) {
            RmLabel rmLabel = rmLabelMapper.selectByPrimaryKey(labelParam.getLabelId());
            if (rmLabel == null) {
                return R.fail("没有该数据");
            }
            if (rmLabel.getIsDelete() == 1) {
                return R.fail("该数据已被删除");
            }
            if (!rmLabel.getCorpId().equals(corpId)) {
                return R.fail("不是该企业下的标签数据");
            }
            if (rmLabel.getIsSystem() == 1) {
                return R.fail("系统数据不允许修改");
            }
            if (rmLabel.getCreateBy().equals(LabelConstants.QywxCreate)) {
                return R.fail("该标签由企微创建，不允许修改!若要修改请至企微后台进行操作");
            }

//            //查询标签是否存在 只要有一个存在  保存失败
//            List<String> labelNames = new ArrayList<>();
//            labelNames.add(labelParam.getLabelName());
//            List<RmLabel> rmLabels = rmLabelMapper.selectLabelNames(labelNames, corpId);
//            if (CollectionUtils.isNotEmpty(rmLabels)) {
//                for (RmLabel label : rmLabels) {
//                    if (!label.getId().equals(rmLabel.getId())) {
//                        return R.fail("保存的标签名称已存在");
//                    }
//                }
//            }


            //远程调用企微 编辑标签名称
            R editCorpTag = editCorpTag(rmLabel.getLabelId(), labelParam.getLabelName(), corpId);
            if (editCorpTag.getCode() != R.SUCCESS) {
                return R.fail(editCorpTag.getMsg());
            }

            //更新
            RmLabel label = new RmLabel();
            label.setId(labelParam.getLabelId());
            label.setLabelName(labelParam.getLabelName());
            label.setUpdateBy(userId);
            label.setUpdateTime(new Date());
            rmLabelMapper.updateByPrimaryKeySelective(label);
            //todo 同步 es
           // rmEsSynDataService.saveEsData(null, null, corpId, EsSynDataUpdateEnum.UPDATE_LABEL.getCode(), 0);
        }
        return R.ok();
    }

    @Override
    public R getLabelByName(String labelName) {
        //获取用户id
        LoginUser currentUser = LoginUser.getCurrentUser();
        String userId = currentUser.getUserId();
        String corpId = currentUser.getCorpId();
        RmLabel rmLabel = new RmLabel();
        rmLabel.setLabelName(labelName);
        rmLabel.setCorpId(corpId);

        List<RmLabel> rmLabels = rmLabelMapper.getLabelByName(rmLabel);

        return R.ok(rmLabels);
    }

    @Override
    public R getLabelInfoByGroupId(String groupId) {

        //获取用户id
        LoginUser currentUser = LoginUser.getCurrentUser();
        String corpId = currentUser.getCorpId();
        if (StringUtils.isEmpty(groupId)) {
            return R.fail("标签组id不存在");
        }

        RmLabelGroup rmLabelGroup = rmLabelGroupMapper.selectByPrimaryKey(groupId);
        if (ObjectUtil.isEmpty(rmLabelGroup) || rmLabelGroup.getIsDelete() == 1) {
            return R.fail("标签组不存在");
        }
        RmLabel rmLabel = new RmLabel();
        rmLabel.setGroupId(rmLabelGroup.getGroupId());
        rmLabel.setCorpId(corpId);
        List<RmLabel> rmLabels = rmLabelMapper.selectLabelByGroupIdAndCorpId(rmLabel);
        rmLabelGroup.setLabelList(rmLabels);
        return R.ok(rmLabelGroup);
    }


    @Override
    public R getQywxLabelData(String corpId) {

        //默认系统拉取
        String userId = "System";
        if (StringUtils.isEmpty(corpId)) {
            LoginUser currentUser = LoginUser.getCurrentUser();
            corpId = currentUser.getCorpId();
            userId = LoginUser.getCurrentUser().getUserId();
        }

        //调企微接口
        R<Map> queryCropLabelList = weChatDataService.getCorpTagList(corpId);
        //查询本系统标签组
        RmLabelGroup rmLabelGroupEd = new RmLabelGroup();
        rmLabelGroupEd.setCorpId(corpId);
        //查询企业所有已存在标签组
        List<RmLabelGroup> rmLabelGroupList = rmLabelGroupMapper.selectLabelGroupByCorpId(rmLabelGroupEd);
        if (CollectionUtils.isEmpty(rmLabelGroupList)) {
            rmLabelGroupList = new ArrayList<>();
        }
        //查询企业所有已存在标签
        List<RmLabel> rmLabelList = rmLabelMapper.selecAllLabelByCorpId(corpId);
        if (CollectionUtils.isEmpty(rmLabelList)) {
            rmLabelList = new ArrayList<>();
        }
        //批量新增-标签组
        List<RmLabelGroup> insertGroupList = new ArrayList<>();
        //批量修改-标签组
        List<RmLabelGroup> updateGroupList = new ArrayList<>();
        List<Map> resultList = new ArrayList<>();
        //批量新增-标签
        List<RmLabel> insertLabelList = new ArrayList<>();
        //批量修改-标签
        List<RmLabel> updateLabelList = new ArrayList<>();


        if (queryCropLabelList.getCode() == R.SUCCESS) {
            Map dataMap = queryCropLabelList.getData();
            List<Map> tagGroupList = (List<Map>) MapUtils.getObject(dataMap, "tag_group");
            if (CollectionUtils.isNotEmpty(tagGroupList)) {
                for (Map tagGroupMap : tagGroupList) {
                    List<Map> tagResultList = new ArrayList<>();
                    String groupId = MapUtils.getString(tagGroupMap, "group_id");
                    String groupName = MapUtils.getString(tagGroupMap, "group_name");
                    Boolean deleted = MapUtils.getBoolean(tagGroupMap, "deleted");
                    RmLabelGroup rmLabelGroup = new RmLabelGroup();
                    rmLabelGroup.setGroupType(1);
                    //未删除
                    rmLabelGroup.setIsDelete(0);
                    if (deleted != null && deleted) {
                        //删除
                        rmLabelGroup.setIsDelete(1);
                    }
                    rmLabelGroup.setGroupName(groupName);
                    rmLabelGroup.setGroupId(groupId);
                    rmLabelGroup.setCorpId(corpId);
                    String groupIdKey = UUID.randomUUID().toString();
                    //查询标签组是否已存在
                    List<RmLabelGroup> groupColletc = rmLabelGroupList.stream().filter(p -> p.getGroupName().equals(groupName) || (StringUtils.isNotEmpty(p.getGroupId()) && p.getGroupId().equals(groupId))).collect(Collectors.toList());
                    if (CollectionUtils.isNotEmpty(groupColletc)) {
                        groupIdKey = groupColletc.get(0).getId();
                        rmLabelGroup.setId(groupIdKey);
                        updateGroupList.add(rmLabelGroup);
                    } else {
                        //新增
                        rmLabelGroup.setCreateUser(userId);
                        rmLabelGroup.setCreateTime(new Date());
                        rmLabelGroup.setId(groupIdKey);
                        rmLabelGroup.setIsTop(0);
                        rmLabelGroup.setGroupId(groupId);
                        insertGroupList.add(rmLabelGroup);
                    }
                    List<Map> tagList = (List<Map>) MapUtils.getObject(tagGroupMap, "tag");
                    if (CollectionUtils.isNotEmpty(tagList)) {
                        for (Map tagMap : tagList) {
                            String labelId = MapUtils.getString(tagMap, "id");
                            String labelName = MapUtils.getString(tagMap, "name");
                            Boolean labelDelete = MapUtils.getBoolean(tagMap, "deleted");
                            RmLabel rmLabel = new RmLabel();
                            rmLabel.setIsDelete(0);
                            if (labelDelete != null && labelDelete) {
                                rmLabel.setIsDelete(1);
                            }
                            rmLabel.setLabelName(labelName);
                            rmLabel.setLabelId(labelId);
                            rmLabel.setCorpId(corpId);
                            rmLabel.setGroupId(groupId);
                            //查询是否已存在
                            String finalGroupIdKey = groupIdKey;
                            List<RmLabel> labelCollect = rmLabelList.stream().filter(p -> p.getLabelName().equals(labelName) && p.getGroupId().equals(groupId)).collect(Collectors.toList());
                            //已存在
                            if (CollectionUtils.isNotEmpty(labelCollect)) {
                                //修改
                                rmLabel.setUpdateTime(new Date());
                                rmLabel.setUpdateBy(userId);
                                rmLabel.setId(rmLabelList.get(0).getId());
                                rmLabel.setUpdateTime(new Date());
                                rmLabel.setUpdateBy(userId);
                                updateLabelList.add(rmLabel);
                            } else {
                                //新增
                                rmLabel.setCreateBy(userId);
                                rmLabel.setCreateTime(new Date());
                                rmLabel.setId(UUID.randomUUID().toString());
                                rmLabel.setCreateTime(new Date());
                                rmLabel.setCreateBy(userId);
                                insertLabelList.add(rmLabel);
                            }
                        }
                    }
                }
            }
            //批量新增标签组
            if (CollectionUtils.isNotEmpty(insertGroupList)) {
                rmLabelGroupMapper.insertBatchInsertLabelGroupSelective(insertGroupList);
            }
            //批量更新标签组
          /*  if (CollectionUtils.isNotEmpty(updateGroupList)) {
                rmLabelGroupMapper.updateBatchRmLabelGroupByPrimaryKeySelective(updateGroupList);
            }*/
            //批量新增标签
            if (CollectionUtils.isNotEmpty(insertLabelList)) {
                rmLabelMapper.insertBatchRmLabel(insertLabelList);
            }
            //批量更新标签
           /* if (CollectionUtils.isNotEmpty(updateLabelList)) {
                rmLabelMapper.updateBatchRmLabel(updateLabelList);
            }*/
        } else {
            return R.fail("远程调用失败");
        }
        return R.ok();
    }

    @Override
    public R initializeAntifraudLabel(String corpId) {

        //默认系统拉取
        //查询初始化数据
        RmLabelGroup rmLabelGroup = rmLabelGroupMapper.selectInitializeAntifraudLabel();
        if (ObjectUtil.isEmpty(rmLabelGroup)) {
            return R.fail("初始化AI反诈自动画像标签失败");
        }
        List<RmLabel> labels = rmLabelMapper.selectInitializeAntifraudLabel(rmLabelGroup.getId());
        if (CollectionUtils.isEmpty(labels)) {
            return R.fail("初始化AI反诈自动画像标签失败");
        }
        //查询企业已有标签组
        RmLabelGroup paramGroup = new RmLabelGroup();
        paramGroup.setCorpId(corpId);
        List<RmLabelGroup> rmLabelGroups = rmLabelGroupMapper.selectLabelGroupByCorpId(paramGroup);
        if (CollectionUtils.isEmpty(rmLabelGroups)) {
            rmLabelGroups = new ArrayList<>();
        }
        List<RmLabelGroup> collect = rmLabelGroups.stream().filter(p -> p.getGroupName().equals(rmLabelGroup.getGroupName())).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(collect)) {
            log.info("==============================当前企业已存在AI反诈标签组====================");
            return null;
        }
        List<String> collectLabelNames = labels.stream().map(RmLabel::getLabelName).collect(Collectors.toList());

        //给新企业同步初始化数据
        LabelParam labelParam = new LabelParam();
        labelParam.setGroupName(rmLabelGroup.getGroupName());
        labelParam.setLabelNames(collectLabelNames);
        labelParam.setCorpId(corpId);
        labelParam.setIsSystem(1);
        saveLabelAndGroup(labelParam);


        return R.ok();
    }

    @Override
    public R<Map> createExternalTag(TagParam tagParam) {

        //标签或标签组的ID
        String tagId = tagParam.getTagId();
        String corpId = tagParam.getCorpId();
        String tagType = tagParam.getTagType();
        // 标签 tag,标签组 tag_group
        //创建标签
        if (tagType.equals("tag")) {

            //去企微查询详情
            JSONObject jsonObject = new JSONObject();
            List<String> tags = new ArrayList<>();
            tags.add(tagId);
            jsonObject.put("tag_id", tags);
            // 查询企微是否删除
            R<Map> corpTagDeleted = weChatDataService.checkCorpTagDeleted(jsonObject, corpId);
            if (corpTagDeleted.getCode() != R.SUCCESS) {
                return R.fail("查看企业客户详情标签失败");
            }
            Map tagDeletedData = corpTagDeleted.getData();
            if (tagDeletedData == null) {
                return R.fail("查看企业客户详情标签失败");
            }
            List<Map> mapList = (List<Map>) MapUtils.getObject(tagDeletedData, "tag_group");
            Map tagGroupMap = mapList.get(0);
            List<Map> tagList = (List<Map>) MapUtils.getObject(tagGroupMap, "tag");
            Map tagMap = tagList.get(0);

            String tagName = MapUtils.getString(tagMap, "name");
            //库里 标签如果为空
            List<RmLabel> rmLabels = rmLabelMapper.selectByQywxLabelIdAndCropId(tagId, corpId, null);
            if (CollectionUtils.isEmpty(rmLabels)) {

                String qywxTagGroupId = MapUtils.getString(tagGroupMap, "group_id");
                JSONObject jsonObjectGroup = new JSONObject();
                List<String> tagGroups = new ArrayList<>();
                tagGroups.add(qywxTagGroupId);
                jsonObjectGroup.put("group_id", tagGroups);

                // 查询企微是否删除
                R<Map> corpTagGroup = weChatDataService.checkCorpTagDeleted(jsonObjectGroup, corpId);
                if (corpTagGroup.getCode() != R.SUCCESS) {
                    return R.fail("查看企业客户详情标签失败");
                }
                Map data = corpTagGroup.getData();
                List<Map> groupList = (List<Map>) MapUtils.getObject(data, "tag_group");
                Map map = groupList.get(0);
                String groupName = MapUtils.getString(map, "group_name");

                List<RmLabelGroup> rmLabelGroups = rmLabelGroupMapper.selectByQywxGroupIdAndCorpId(qywxTagGroupId, corpId, null);

                if (CollectionUtils.isEmpty(rmLabelGroups)) {
                    RmLabelGroup rmLabelGroup = new RmLabelGroup();
                    String id = UUID.randomUUID().toString();
                    rmLabelGroup.setId(id);
                    rmLabelGroup.setGroupId(qywxTagGroupId);
                    rmLabelGroup.setGroupName(groupName);
                    rmLabelGroup.setCreateUser(LabelConstants.QywxCreate);
                    rmLabelGroup.setCreateTime(new Date());
                    rmLabelGroup.setCorpId(corpId);
                    rmLabelGroup.setGroupType(1);
                    rmLabelGroup.setIsDelete(0);
                    rmLabelGroupMapper.insertSelective(rmLabelGroup);

                    RmLabel rmLabel = new RmLabel();
                    rmLabel.setId(UUID.randomUUID().toString());
                    rmLabel.setLabelId(tagId);
                    rmLabel.setLabelName(tagName);
                    rmLabel.setType(1);
                    rmLabel.setIsDelete(0);
                    rmLabel.setCreateTime(new Date());
                    rmLabel.setCreateBy(LabelConstants.QywxCreate);
                    rmLabel.setCorpId(corpId);
                    //企业微信的groupId
                    rmLabel.setGroupId(qywxTagGroupId);
                    rmLabelMapper.insertSelective(rmLabel);
                } else {
                    RmLabel rmLabel = new RmLabel();
                    rmLabel.setId(UUID.randomUUID().toString());
                    rmLabel.setLabelId(tagId);
                    rmLabel.setLabelName(tagName);
                    rmLabel.setType(1);
                    rmLabel.setIsDelete(0);
                    rmLabel.setCreateTime(new Date());
                    rmLabel.setCreateBy(LabelConstants.QywxCreate);
                    rmLabel.setCorpId(corpId);
                    rmLabel.setGroupId(qywxTagGroupId);
                    rmLabelMapper.insertSelective(rmLabel);
                }
            }
        }
//        else if (tagType.equals("tag_group")){
//
//            JSONObject jsonObjectGroup = new JSONObject();
//            List<String> tagGroups = new ArrayList<>();
//            tagGroups.add(tagId);
//            jsonObjectGroup.put("group_id", tagGroups);
//
//            // 查询企微是否删除
//            R<Map> corpTagGroup = weChatDataService.checkCorpTagDeleted(jsonObjectGroup, corpId);
//            if (corpTagGroup.getCode() != R.SUCCESS) {
//                return R.fail("查看企业客户详情标签失败");
//            }
//            Map data = corpTagGroup.getData();
//            List<Map> groupList = (List<Map>) MapUtils.getObject(data, "tag_group");
//            Map map = groupList.get(0);
//            String groupName = MapUtils.getString(map, "group_name");
//
//            List<RmLabelGroup> rmLabelGroups =  rmLabelGroupMapper.selectByQywxGroupIdAndCorpId(tagId,corpId,groupName);
//            if (CollectionUtils.isEmpty(rmLabelGroups)){
//
//                RmLabelGroup rmLabelGroup = new RmLabelGroup();
//                String id = UUID.randomUUID().toString();
//                rmLabelGroup.setId(id);
//                rmLabelGroup.setGroupId(tagId);
//                rmLabelGroup.setGroupName(groupName);
//                rmLabelGroup.setCreateUser(LabelConstants.QywxCreate);
//                rmLabelGroup.setCreateTime(new Date());
//                rmLabelGroup.setCorpId(corpId);
//                rmLabelGroup.setGroupType(1);
//                rmLabelGroup.setIsDelete(0);
//                rmLabelGroupMapper.insertSelective(rmLabelGroup);
//            }
//
//        }
        return R.ok();
    }

    @Override
    public R<Map> updateExternalTag(TagParam tagParam) {

        //标签或标签组的ID
        String tagId = tagParam.getTagId();
        String corpId = tagParam.getCorpId();
        String tagType = tagParam.getTagType();
        // 标签 tag,标签组 tag_group
        //更新标签
        if (tagType.equals("tag")) {

            //去企微查询详情
            JSONObject jsonObject = new JSONObject();
            List<String> tags = new ArrayList<>();
            tags.add(tagId);
            jsonObject.put("tag_id", tags);
            // 查询企微是否删除
            R<Map> corpTagDeleted = weChatDataService.checkCorpTagDeleted(jsonObject, corpId);
            if (corpTagDeleted.getCode() != R.SUCCESS) {
                return R.fail("查看企业客户详情标签失败");
            }
            Map tagDeletedData = corpTagDeleted.getData();
            if (tagDeletedData == null) {
                return R.fail("查看企业客户详情标签失败");
            }
            List<Map> mapList = (List<Map>) MapUtils.getObject(tagDeletedData, "tag_group");
            Map tagGroupMap = mapList.get(0);
            List<Map> tagList = (List<Map>) MapUtils.getObject(tagGroupMap, "tag");
            Map tagMap = tagList.get(0);

            String tagName = MapUtils.getString(tagMap, "name");
            //库里 标签如果为空
            List<RmLabel> rmLabels = rmLabelMapper.selectByQywxLabelIdAndCropId(tagId, corpId, null);
            if (CollectionUtils.isNotEmpty(rmLabels)) {
                RmLabel rmLabel = new RmLabel();
                rmLabel.setId(rmLabels.get(0).getId());
                rmLabel.setLabelId(tagId);
                rmLabel.setLabelName(tagName);
                rmLabel.setType(1);
                rmLabel.setIsDelete(0);
                rmLabel.setUpdateTime(new Date());
                rmLabel.setUpdateBy(LabelConstants.QywxUpdate);
                rmLabelMapper.updateByPrimaryKeySelective(rmLabel);
            }
        } else if (tagType.equals("tag_group")) {

            JSONObject jsonObjectGroup = new JSONObject();
            List<String> tagGroups = new ArrayList<>();
            tagGroups.add(tagId);
            jsonObjectGroup.put("group_id", tagGroups);

            // 查询企微是否删除
            R<Map> corpTagGroup = weChatDataService.checkCorpTagDeleted(jsonObjectGroup, corpId);
            if (corpTagGroup.getCode() != R.SUCCESS) {
                return R.fail("查看企业客户详情标签失败");
            }
            Map data = corpTagGroup.getData();
            List<Map> groupList = (List<Map>) MapUtils.getObject(data, "tag_group");
            Map map = groupList.get(0);
            String groupName = MapUtils.getString(map, "group_name");

            List<RmLabelGroup> rmLabelGroups = rmLabelGroupMapper.selectByQywxGroupIdAndCorpId(tagId, corpId, null);
            if (CollectionUtils.isNotEmpty(rmLabelGroups)) {

                RmLabelGroup rmLabelGroup = new RmLabelGroup();
                rmLabelGroup.setId(rmLabelGroups.get(0).getId());
                rmLabelGroup.setGroupId(tagId);
                rmLabelGroup.setGroupName(groupName);
                rmLabelGroup.setUpdateUser(LabelConstants.QywxUpdate);
                rmLabelGroup.setUpdateTime(new Date());
                rmLabelGroup.setCorpId(corpId);
                rmLabelGroup.setGroupType(1);
                rmLabelGroup.setIsDelete(0);
                rmLabelGroupMapper.updateByPrimaryKeySelective(rmLabelGroup);
            }

        }
        //todo 同步 es
       // rmEsSynDataService.saveEsData(null, null, corpId, EsSynDataUpdateEnum.UPDATE_LABEL.getCode(), 0);
        return R.ok();
    }

    @Override
    public R<Map> deleteExternalTag(TagParam tagParam) {
        //标签或标签组的ID
        String tagId = tagParam.getTagId();
        String corpId = tagParam.getCorpId();
        String tagType = tagParam.getTagType();
        //删除标签
        if (tagType.equals("tag")) {
            //直接更新为1  然后删除关联关系
            List<RmLabel> rmLabels = rmLabelMapper.selectByQywxLabelIdAndCropId(tagId, corpId, null);
            if (CollectionUtils.isNotEmpty(rmLabels)) {
                for (RmLabel rmLabel : rmLabels) {
                    String id = rmLabel.getId();
                    //查看是否删除成功
                    RmLabel deleteRmLabel = new RmLabel();
                    deleteRmLabel.setId(id);
                    deleteRmLabel.setIsDelete(1);
                    deleteRmLabel.setUpdateBy(LabelConstants.QywxDelete);
                    deleteRmLabel.setUpdateTime(new Date());
                    rmLabelMapper.updateByPrimaryKeySelective(deleteRmLabel);
                    //根据标签id和企业id删除rm_external_label表 外部联系人与标签的关联关系
                    rmExternalLabelMapper.deleteByLabelIdAndlabelGroupId(id, corpId, null);
                    rmExternalLabelMapper.deleteByLabelIdAndlabelGroupId(tagId, corpId, null);
                }
                //todo 同步 es
              //  rmEsSynDataService.saveEsData(null, null, corpId, EsSynDataUpdateEnum.DELETE_LABEL.getCode(), 0);
            }


        } else if (tagType.equals("tag_group")) {

//            List<RmLabel> rmLabels = rmLabelMapper.selectByQywxLabelIdAndCropId(tagId, corpId, null);
            List<RmLabelGroup> rmLabelGroups = rmLabelGroupMapper.selectByQywxGroupIdAndCorpId(tagId, corpId, null);

            if (CollectionUtils.isNotEmpty(rmLabelGroups)) {
                for (RmLabelGroup labelGroup : rmLabelGroups) {
                    RmLabelGroup rmLabelGroup = new RmLabelGroup();

                    if (labelGroup.getIsSystem() == 1) {
                        return R.fail("系统数据不允许删除");
                    }
                    //1：删除
                    rmLabelGroup.setIsDelete(1);
                    rmLabelGroup.setId(labelGroup.getId());
                    rmLabelGroup.setUpdateUser(LabelConstants.QywxDelete);
                    rmLabelGroup.setUpdateTime(new Date());

                    //删除标签组
                    rmLabelGroupMapper.updateByPrimaryKeySelective(rmLabelGroup);

                    //根据标签组id和企业id删除rm_external_label表 外部联系人与标签的关联关系
                    rmExternalLabelMapper.deleteByLabelIdAndlabelGroupId(null, corpId, labelGroup.getId());
                    rmExternalLabelMapper.deleteByLabelIdAndlabelGroupId(null, corpId, labelGroup.getGroupId());
                    RmLabel rmLabel = new RmLabel();
                    rmLabel.setGroupId(labelGroup.getGroupId());
                    rmLabel.setCorpId(corpId);
                    // 1是删除
                    rmLabel.setIsDelete(1);
                    rmLabel.setUpdateBy(LabelConstants.QywxDelete);
                    rmLabel.setUpdateTime(new Date());
                    //删除标签组下 所有的标签
                    rmLabelMapper.updateByGroupId(rmLabel);
                }
                //todo 同步 es
             //   rmEsSynDataService.saveEsData(null, null, corpId, EsSynDataUpdateEnum.DELETE_LABEL.getCode(), 0);
            }
        }
        return R.ok();
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public R saveLabelAndGroup(LabelParam labelParam) {

        String corpId = null;
        String userId = "Antifraud";
        if (StringUtils.isEmpty(labelParam.getCorpId())) {
            //        获取用户id
            LoginUser currentUser = LoginUser.getCurrentUser();
            userId = currentUser.getUserId();
            corpId = currentUser.getCorpId();
        } else {
            corpId = labelParam.getCorpId();
        }

        String groupId = UUID.randomUUID().toString();

        Integer isSystem = 0;
        if (labelParam.getIsSystem() != null && labelParam.getIsSystem() == 1) {
            isSystem = 1;
        } else {
            isSystem = 0;
        }
        //封装查询参数
        RmLabelGroup rmLabelGroup = new RmLabelGroup();
        rmLabelGroup.setGroupName(labelParam.getGroupName());
        rmLabelGroup.setCorpId(corpId);
        //组id为空 新增标签组
        if (labelParam.getGroupId() == null) {
            //查询标签组是否已存在
            List<RmLabelGroup> labelGroupList = rmLabelGroupMapper.selectLabelGroupByGroupName(rmLabelGroup);
            if (CollectionUtils.isNotEmpty(labelGroupList)) {
                return R.fail("标签组名称已存在");
            }

//            //查询标签是否存在 只要有一个存在  保存失败
//            if (CollectionUtils.isNotEmpty(labelParam.getLabelNames())) {
//                List<RmLabel> rmLabels = rmLabelMapper.selectLabelNames(labelParam.getLabelNames(), corpId);
//                if (CollectionUtils.isNotEmpty(rmLabels)) {
//                    return R.fail("保存的标签名称已存在");
//                }
//            }

            //新增标签组
            rmLabelGroup.setCreateTime(new Date());
            rmLabelGroup.setGroupType(1);
            rmLabelGroup.setIsDelete(0);
            rmLabelGroup.setId(groupId);
            rmLabelGroup.setIsTop(0);
            rmLabelGroup.setCreateUser(userId);

            rmLabelGroup.setIsSystem(isSystem);
            rmLabelGroupMapper.insertSelective(rmLabelGroup);
            List<String> labelNames = labelParam.getLabelNames();
            if (CollectionUtils.isNotEmpty(labelNames)) {
                for (String labelName : labelNames) {
                    //查询标签在同一标签组下是否已存在
                    RmLabel rmLabel = new RmLabel();
                    rmLabel.setCorpId(corpId);
                    rmLabel.setGroupId(groupId);
                    rmLabel.setLabelName(labelName);
                    rmLabel.setType(1);

                    //新增 标签
                    String labelId = UUID.randomUUID().toString();
                    rmLabel.setCreateBy(userId);
                    rmLabel.setCreateTime(new Date());
                    rmLabel.setIsDelete(0);
                    rmLabel.setId(labelId);
                    rmLabel.setIsSystem(isSystem);
                    //远程插入企微
                    Map<String, String> qywxMap = addCorpTag(labelParam.getGroupName(), labelName, corpId);
                    if (qywxMap != null) {
                        String qywxGroupId = MapUtils.getString(qywxMap, "groupId");
                        rmLabelGroup.setGroupId(qywxGroupId);
                        String qywxLabelId = MapUtils.getString(qywxMap, "labelId");
                        rmLabel.setLabelId(qywxLabelId);
                        rmLabel.setGroupId(qywxGroupId);
                    }
                    rmLabelMapper.insertSelective(rmLabel);
                }
            }
            //更新  qywxGroupId
            rmLabelGroupMapper.updateByPrimaryKeySelective(rmLabelGroup);
        } else {
            //已存在
            RmLabelGroup rmLabelGroupToDB = rmLabelGroupMapper.selectByPrimaryKey(labelParam.getGroupId());
            if (rmLabelGroupToDB == null) {
                return R.fail("标签组不存在");
            }
            if (rmLabelGroupToDB.getIsSystem() == 1) {
                return R.fail("系统数据不允许新增");
            }

//            //查询标签是否存在 只要有一个存在  保存失败
//            if (CollectionUtils.isNotEmpty(labelParam.getLabelNames())) {
//                List<RmLabel> rmLabels = rmLabelMapper.selectLabelNames(labelParam.getLabelNames(), corpId);
//                if (CollectionUtils.isNotEmpty(rmLabels)) {
//                    return R.fail("保存的标签名称已存在");
//                }
//            }
            List<String> labelNames = labelParam.getLabelNames();
            if (CollectionUtils.isNotEmpty(labelNames)) {
                for (String labelName : labelNames) {
                    //查询标签在同一标签组下是否已存在
                    RmLabel rmLabel = new RmLabel();
                    rmLabel.setCorpId(corpId);
                    rmLabel.setGroupId(rmLabelGroupToDB.getGroupId());
                    rmLabel.setLabelName(labelName);
                    rmLabel.setType(1);
                    rmLabel.setIsSystem(isSystem);
                    List<RmLabel> rmLabelList = rmLabelMapper.selectLabelByLabelGroupByLabelName(rmLabel);
                    //不存在，新增 标签
                    String labelId = UUID.randomUUID().toString();
                    if (CollectionUtils.isEmpty(rmLabelList)) {
                        rmLabel.setCreateBy(userId);
                        rmLabel.setCreateTime(new Date());
                        rmLabel.setIsDelete(0);
                        rmLabel.setId(labelId);

                        //远程插入企微
                        Map<String, String> qywxMap = addCorpTag(rmLabelGroupToDB.getGroupName(), labelName, corpId);
                        if (qywxMap == null) {
                            return R.fail("远程请求企微失败");
                        }
                        String qywxLabelId = MapUtils.getString(qywxMap, "labelId");
                        rmLabel.setLabelId(qywxLabelId);
                        rmLabelMapper.insertSelective(rmLabel);
                    } else {
                        //存在。修改
                        String id = rmLabelList.get(0).getId();
                        rmLabel.setUpdateBy(userId);
                        rmLabel.setUpdateTime(new Date());
                        rmLabel.setId(id);
                        rmLabelMapper.updateByPrimaryKeySelective(rmLabel);
                    }
                }
            }
        }
        return R.ok();
    }


    /**
     * 封装数据
     *
     * @param groupName 标签组名称
     * @param labelName 标签名称
     * @param corpId    企业id
     * @return
     */
    private Map<String, String> addCorpTag(String groupName, String labelName, String corpId) {
        JSONObject jsonObject = new JSONObject();
        // 添加 group_name 字段
        jsonObject.put("group_name", groupName);
        // 创建一个空的 JSON 数组
        JSONArray jsonArray = new JSONArray();
        JSONObject tag = new JSONObject();
        tag.put("name", labelName);
        jsonArray.add(tag);
        // 将 JSON 数组添加到 JSON 对象中
        jsonObject.put("tag", jsonArray);
        // 远程调企微
        R<Map> result = weChatDataService.addCorpTag(jsonObject, corpId);
        if (result.getCode() == R.SUCCESS) {
            Map<String, String> resultMap = new HashMap<>();
            Map dataMap = result.getData();

            Map tagGroupMap = (Map) MapUtils.getObject(dataMap, "tag_group");
            String groupId = MapUtils.getString(tagGroupMap, "group_id");
            List<Map> tagList = (List<Map>) MapUtils.getObject(tagGroupMap, "tag");


            if (CollectionUtils.isNotEmpty(tagList)) {
                for (Map tagMap : tagList) {
                    String labelId = MapUtils.getString(tagMap, "id");
                    resultMap.put("groupId", groupId);
                    resultMap.put("labelId", labelId);
                }
            }
            return resultMap;
        }
        return null;
    }


    /**
     * 编辑企业客户标签
     *
     * @param id     qywx标签id 或  qywx标签组id
     * @param name   标签名称 或 标签组名称
     * @param corpId 企业id
     * @return
     */
    private R editCorpTag(String id, String name, String corpId) {
        JSONObject jsonObject = new JSONObject();
        // 添加 id 字段
        jsonObject.put("id", id);
        // 添加 name 字段
        jsonObject.put("name", name);
        // 远程调企微
        R<Map> result = weChatDataService.editCorpTag(jsonObject, corpId);
        if (result.getCode() != R.SUCCESS) {
            return R.fail(result.getMsg());
        }
        return R.ok();
    }


    private R delCorpTag(String tagId, String corpId, String groupId) {
        JSONObject jsonObject = new JSONObject();
        // 添加 id 字段
        JSONArray jsonArray = new JSONArray();
        if (StringUtils.isNotEmpty(tagId) && StringUtils.isEmpty(groupId)) {
            jsonArray.add(tagId);
            // 添加 name 字段
            jsonObject.put("tag_id", jsonArray);
        }
        if (StringUtils.isEmpty(tagId) && StringUtils.isNotEmpty(groupId)) {
            jsonArray.add(groupId);
            jsonObject.put("group_id", jsonArray);
        }

        // 远程调企微
        R<Map> result = weChatDataService.delCorpTag(jsonObject, corpId);
        if (result.getCode() != R.SUCCESS) {
            return R.fail(result.getMsg());
        }

        // 查询企微是否删除
        R<Map> corpTagDeleted = weChatDataService.checkCorpTagDeleted(jsonObject, corpId);
        if (corpTagDeleted.getCode() != R.SUCCESS) {
            return R.fail("查看企业客户详情标签失败");
        }
        Map tagDeletedData = corpTagDeleted.getData();
        if (tagDeletedData == null) {
            return R.fail("查看企业客户详情标签失败");
        }
        List<Map> mapList = (List<Map>) MapUtils.getObject(tagDeletedData, "tag_group");
        Map tagGroupMap = mapList.get(0);
        if (StringUtils.isNotEmpty(groupId)) {
            if (!MapUtils.getString(tagGroupMap, "group_id").equals(groupId)) {
                return R.fail(groupId + "不相等");
            }
            Boolean deleted = MapUtils.getBoolean(tagGroupMap, "deleted");
            if (deleted) {
                return R.ok(groupId + "标签组删除成功");
            }
        }
        if (StringUtils.isNotEmpty(tagId)) {
            List<Map> tagList = (List<Map>) MapUtils.getObject(tagGroupMap, "tag");
            Map tagMap = tagList.get(0);
            if (!MapUtils.getString(tagMap, "id").equals(tagId)) {
                return R.fail(tagId + "不相等");
            }
            Boolean deleted = MapUtils.getBoolean(tagMap, "deleted");
            if (deleted) {
                return R.ok(tagId + "标签删除成功");
            }


        }
        return R.fail();
    }


    @Override
    public void synCorpLabelData(String corpId) {
        R<Map> corpTagListR = weChatDataService.getCorpTagList(corpId);
        if (corpTagListR.getCode() == R.SUCCESS) {
            Map resultMap = corpTagListR.getData();
            List<Map> tagGroupList = (List<Map>) resultMap.get("tag_group");
            if (tagGroupList != null && tagGroupList.size() > 0) {
                for (Map map : tagGroupList) {
                    RmLabelGroup rmLabelGroup = new RmLabelGroup();
                    String wxGroupId = MapUtils.getString(map, "group_id");
                    String group_name = MapUtils.getString(map, "group_name");
                    Boolean deleted = MapUtils.getBoolean(map, "deleted");
                    rmLabelGroup.setGroupId(wxGroupId);
                    rmLabelGroup.setGroupType(1);
                    rmLabelGroup.setGroupName(group_name);
                    rmLabelGroup.setCorpId(corpId);
                    if (deleted != null && deleted) {
                        rmLabelGroup.setIsDelete(1);
                    } else {
                        rmLabelGroup.setIsDelete(0);
                    }
                    //查询该标签是否已存在
                    RmLabelGroup labelGroupEd = rmLabelGroupMapper.selectLabelGroupByGroupId(rmLabelGroup);
                    if (labelGroupEd != null) {
                        rmLabelGroup.setId(labelGroupEd.getId());
                        rmLabelGroupMapper.updateByPrimaryKeySelective(rmLabelGroup);
                    } else {
                        //新增
                        rmLabelGroup.setCreateTime(new Date());
                        rmLabelGroup.setCreateUser("system");
                        rmLabelGroup.setId(UUID.randomUUID().toString());
                        rmLabelGroup.setIsDelete(0);

                        rmLabelGroup.setCreateTime(new Date());
                        rmLabelGroup.setIsTop(0);
                        rmLabelGroupMapper.insertSelective(rmLabelGroup);
                    }
                    List<Map> tagList = (List<Map>) map.get("tag");
                    if (tagList != null && tagList.size() > 0) {
                        for (Map labelMap : tagList) {
                            RmLabel rmLabel = new RmLabel();
                            String wxLabelId = MapUtils.getString(labelMap, "id");
                            String labelName = MapUtils.getString(labelMap, "name");
                            rmLabel.setLabelId(wxLabelId);
                            rmLabel.setCorpId(corpId);
                            rmLabel.setLabelName(labelName);
                            rmLabel.setType(1);
                            //查询是否已存在
                            RmLabel selectLabelByLabelId = rmLabelMapper.selectLabelByLabelId(rmLabel);
                            Boolean deletedLabel = MapUtils.getBoolean(labelMap, "deleted");
                            if (deletedLabel != null && deletedLabel) {
                                rmLabel.setIsDelete(1);
                            } else {
                                rmLabel.setIsDelete(0);
                            }
                            rmLabel.setGroupId(rmLabelGroup.getGroupId());
                            if (selectLabelByLabelId != null) {
                                //修改
                                rmLabel.setUpdateBy("system");
                                rmLabel.setId(selectLabelByLabelId.getId());
                                rmLabelMapper.updateByPrimaryKeySelective(rmLabel);
                            } else {
                                //新增
                                rmLabel.setId(UUID.randomUUID().toString());
                                rmLabel.setCreateTime(new Date());
                                rmLabel.setCreateBy("system");
                                rmLabelMapper.insertSelective(rmLabel);
                            }
                        }
                    }
                }
            }

        }


    }


}
