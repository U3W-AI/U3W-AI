package com.cube.wechat.thirdapp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.fastjson.JSON;
import com.cube.common.core.domain.R;
import com.cube.common.utils.StringUtils;
import com.cube.wechat.thirdapp.constant.BasicConstant;
import com.cube.wechat.thirdapp.entiy.*;
import com.cube.wechat.thirdapp.entiy.vo.CompanyVo;
import com.cube.wechat.thirdapp.mapper.*;
import com.cube.wechat.thirdapp.param.CompanyExcel;
import com.cube.wechat.thirdapp.param.CompanyParam;
import com.cube.wechat.thirdapp.param.GetCompanyParam;
import com.cube.wechat.thirdapp.param.ManageParam;
import com.cube.wechat.thirdapp.service.CompanyService;
import com.cube.wechat.thirdapp.service.WeChatThirdCompanyService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author 张云龙
 */
@Service
@Slf4j
public class CompanyServiceImpl implements CompanyService {
    @Autowired
    private RmCompanyMapper rmCompanyMapper;

    @Autowired
    private RmCompanyManageMapper rmCompanyManageMapper;

    @Autowired
    private RmCompanyLabelRelationMapper rmCompanyLabelRelationMapper;

    @Autowired
    private RmCompanyLabelMapper rmCompanyLabelMapper;

    @Autowired
    private RmCompanyInfoArchivesMapper rmCompanyInfoArchivesMapper;

    @Autowired
    private RmCompanyInfoFieldConfigMapper rmCompanyInfoFieldConfigMapper;

    @Autowired
    private RmExternalInfoMapper rmExternalInfoMapper;

    @Autowired
    private RmCompanyExternalMapper rmCompanyExternalMapper;

    @Autowired
    private RmExternalInfoFieldConfigMapper rmExternalInfoFieldConfigMapper;

    @Autowired
    private RmExternalInfoArchivesMapper rmExternalInfoArchivesMapper;

    @Autowired
    private BasicConstant constant;

    @Autowired
    private WeChatThirdCompanyService weChatThirdCompanyService;

//    @Autowired
//    private RmEsSynDataService rmEsSynDataService;


    @Override
    public R saveCompany(CompanyParam companyParam) {
        LoginUser currentUser = LoginUser.getCurrentUser();
        String corpId = currentUser.getCorpId();
        String userId = currentUser.getUserId();
        //
        String companyFullName = companyParam.getCompanyFullName();
        String companyShortName = companyParam.getCompanyShortName();
        if (StringUtils.isNotEmpty(companyFullName)) {
            //判断是全称是否重复
            List<RmCompany> rmCompanyList = rmCompanyMapper.selectByFullName(companyFullName, corpId);
            if (CollectionUtils.isNotEmpty(rmCompanyList)) {
                return R.fail("企业全称已存在");
            }
        }


        //封装企业信息
        RmCompany rmCompany = new RmCompany();
        String companyId = UUID.randomUUID().toString();
        rmCompany.setId(companyId);
        rmCompany.setCorpId(corpId);
        rmCompany.setCreateBy(userId);
        rmCompany.setCreateTime(new Date());
        rmCompany.setCompanyFullName(companyFullName);
        rmCompany.setCompanyShortName(companyShortName);
        rmCompany.setCompanyLogo(companyParam.getCompanyLogo());
        rmCompany.setCreditCode(companyParam.getCreditCode());
        //判断是否有字段信息
        if (CollectionUtil.isNotEmpty(companyParam.getInfoList())) {
            List<CompanyInfoParam> infoList = companyParam.getInfoList();
            for (CompanyInfoParam companyInfoParam : infoList) {
                RmCompanyInfoArchives rmCompanyInfoArchives = new RmCompanyInfoArchives();

                rmCompanyInfoArchives.setId(UUID.randomUUID().toString());
                rmCompanyInfoArchives.setCompanyId(companyId);
                rmCompanyInfoArchives.setCorpId(corpId);
                rmCompanyInfoArchives.setInfoId(companyInfoParam.getInfoId());
                rmCompanyInfoArchives.setInfoValue(companyInfoParam.getInfoValue());
                rmCompanyInfoArchives.setCreateUserId(userId);
                rmCompanyInfoArchives.setCreateTime(new Date());
                rmCompanyInfoArchivesMapper.insertSelective(rmCompanyInfoArchives);
            }
        }
        //封装企业管理范围
        List<ManageParam> manageList = companyParam.getManageList();
        if (CollectionUtils.isNotEmpty(manageList)) {
            List<RmCompanyManage> rmCompanyManages = manageList.stream().map(item -> {
                RmCompanyManage rmCompanyManage = new RmCompanyManage();
                rmCompanyManage.setId(UUID.randomUUID().toString());
                rmCompanyManage.setCompanyId(companyId);
                rmCompanyManage.setRangeId(item.getRangeId());
                rmCompanyManage.setParentId(item.getParentId());
                rmCompanyManage.setDataType(item.getDataType());
                rmCompanyManage.setCorpId(corpId);
                rmCompanyManage.setUniqueId(item.getRangeId() + "_" + item.getParentId());
                return rmCompanyManage;
            }).collect(Collectors.toList());
            rmCompanyManageMapper.saveBatch(rmCompanyManages);

        }
        //获取企业标签集合
        List<String> labelIds = companyParam.getLabelIds();
        if (CollectionUtils.isNotEmpty(labelIds)) {
            for (String labelId : labelIds) {
                RmCompanyLabelRelation rmCompanyLabelRelation = new RmCompanyLabelRelation();
                rmCompanyLabelRelation.setLabelId(labelId);
                rmCompanyLabelRelation.setId(UUID.randomUUID().toString());
                //查询标签所属标签组
                RmCompanyLabel companyLabel = rmCompanyLabelMapper.selectByPrimaryKey(labelId);
                if (companyLabel != null) {
                    String groupId = companyLabel.getGroupId();
                    rmCompanyLabelRelation.setGroupId(groupId);
                }
                rmCompanyLabelRelation.setCompanyId(companyId);
                rmCompanyLabelRelation.setCorpId(corpId);
                rmCompanyLabelRelation.setCreateTime(new Date());
                rmCompanyLabelRelationMapper.insertSelective(rmCompanyLabelRelation);
            }
        }
        //保存企业信息
        rmCompanyMapper.insertSelective(rmCompany);
        return R.ok();
    }

    @Override
    public R updateCompany(CompanyParam companyParam) {
        LoginUser currentUser = LoginUser.getCurrentUser();
        String corpId = currentUser.getCorpId();
        String userId = currentUser.getUserId();

        String companyId = companyParam.getCompanyId();

        String companyFullName = companyParam.getCompanyFullName();
        String companyShortName = companyParam.getCompanyShortName();

        if (StringUtils.isNotEmpty(companyFullName)) {
            //判断是全称是否重复
            List<RmCompany> rmCompanyList = rmCompanyMapper.selectByFullName(companyFullName, corpId);
            if (CollectionUtils.isNotEmpty(rmCompanyList)) {
                for (RmCompany rmCompany : rmCompanyList) {
                    if (!rmCompany.getId().equals(companyId)) {
                        return R.fail("企业全称已存在");
                    }
                }
            }
        }


        //更新企业信息
        RmCompany rmCompany = new RmCompany();
        rmCompany.setId(companyId);
        if (StringUtils.isNotEmpty(companyFullName)) {
            rmCompany.setCompanyFullName(companyFullName);
        }
        if (StringUtils.isNotEmpty(companyShortName)) {
            rmCompany.setCompanyShortName(companyShortName);
        }
        if (StringUtils.isNotEmpty(companyParam.getCompanyLogo())) {
            rmCompany.setCompanyLogo(companyParam.getCompanyLogo());
        }

        if (StringUtils.isNotEmpty(companyParam.getCreditCode())) {
            rmCompany.setCreditCode(companyParam.getCreditCode());
        }
        rmCompany.setUpdateBy(userId);
        rmCompany.setUpdateTime(new Date());


        companyParam.setCorpId(corpId);
        companyParam.setUserId(userId);
        //替换企业档案名称
        replaceArchivesCompanyName(companyParam);

        //更新企业信息
        rmCompanyMapper.updateByPrimaryKeySelective(rmCompany);

        List<CompanyInfoParam> infoParams = companyParam.getInfoList();

        //处理配置字段内容
        if (CollectionUtils.isNotEmpty(infoParams)) {


            //先删除
            rmCompanyInfoArchivesMapper.deleteByCompanyIdAndCropId(companyId, corpId);
            for (CompanyInfoParam infoParam : infoParams) {

                //开始更新
                RmCompanyInfoArchives companyInfo = new RmCompanyInfoArchives();
                companyInfo.setId(UUID.randomUUID().toString());
                companyInfo.setInfoValue(infoParam.getInfoValue());
                companyInfo.setCorpId(corpId);
                companyInfo.setInfoId(infoParam.getInfoId());
//                updateCompanyInfo.setUpdateUserId(userId);
//                updateCompanyInfo.setUpdateTime(new Date());
                //根据主键更新infoValue
//                rmCompanyInfoArchivesMapper.updateByPrimaryKeySelectiveAndCropId(updateCompanyInfo);
                companyInfo.setCompanyId(companyId);
                companyInfo.setCreateUserId(userId);
                companyInfo.setCreateTime(new Date());
                rmCompanyInfoArchivesMapper.insertSelective(companyInfo);
            }
        }

        //根据企业id和公司id删除
        rmCompanyManageMapper.deleteByCompanyIdAndCorpId(companyId, corpId);

        //封装企业管理范围
        List<ManageParam> manageList = companyParam.getManageList();
        if (CollectionUtils.isNotEmpty(manageList)) {
            List<RmCompanyManage> rmCompanyManages = manageList.stream().map(item -> {
                RmCompanyManage rmCompanyManage = new RmCompanyManage();
                rmCompanyManage.setId(UUID.randomUUID().toString());
                rmCompanyManage.setCompanyId(companyId);
                rmCompanyManage.setRangeId(item.getRangeId());
                rmCompanyManage.setParentId(item.getParentId());
                rmCompanyManage.setDataType(item.getDataType());
                rmCompanyManage.setCorpId(corpId);
                rmCompanyManage.setUniqueId(item.getRangeId() + "_" + item.getParentId());
                return rmCompanyManage;
            }).collect(Collectors.toList());
            rmCompanyManageMapper.saveBatch(rmCompanyManages);
        }


        //处理标签
        rmCompanyLabelRelationMapper.deleteByCompanyIdAndCorpId(companyId, corpId);
        //获取企业标签集合
        List<String> labelIds = companyParam.getLabelIds();
        if (CollectionUtils.isNotEmpty(labelIds)) {
            for (String labelId : labelIds) {
                RmCompanyLabelRelation rmCompanyLabelRelation = new RmCompanyLabelRelation();
                rmCompanyLabelRelation.setLabelId(labelId);
                rmCompanyLabelRelation.setId(UUID.randomUUID().toString());
                rmCompanyLabelRelation.setCompanyId(companyId);
                //查询标签所属标签组
                RmCompanyLabel companyLabel = rmCompanyLabelMapper.selectByPrimaryKey(labelId);
                if (companyLabel != null) {
                    String groupId = companyLabel.getGroupId();
                    rmCompanyLabelRelation.setGroupId(groupId);
                }
                rmCompanyLabelRelation.setCorpId(corpId);
                rmCompanyLabelRelation.setCreateTime(new Date());
                rmCompanyLabelRelationMapper.insertSelective(rmCompanyLabelRelation);
            }
        }

        // todo 同步 es
       // rmEsSynDataService.saveEsData(null, null, corpId, EsSynDataUpdateEnum.UPDATE_ARCHIVES.getCode(), 0);
        return R.ok();
    }


    //替换档案企业名称
    private void replaceArchivesCompanyName(CompanyParam companyParam) {
        //先根据企业id查询旧数据的企业信息
        String corpId = companyParam.getCorpId();
        String userId = companyParam.getUserId();
        RmCompany rmCompany = rmCompanyMapper.selectByPrimaryKey(companyParam.getCompanyId());
        if (rmCompany != null) {

            //拿到企业名称
            String companyFullName = rmCompany.getCompanyFullName();

            // 拼成  "corpName":"腾昕科技"
            String oldCorpName = "\"corpName\":\"" + companyFullName + "\"";

            String newCorpName = "\"corpName\":\"" + companyParam.getCompanyFullName() + "\"";

            //根据企业名称去查档案数据
            List<RmExternalInfoArchives> externalInfoArchives = rmExternalInfoArchivesMapper.selectByInfoValueAndCorpId(corpId, oldCorpName);
            //如果不为空 循环替换
            if (CollectionUtils.isNotEmpty(externalInfoArchives)) {
                for (RmExternalInfoArchives externalInfoArchive : externalInfoArchives) {
                    String id = externalInfoArchive.getId();
                    rmExternalInfoArchivesMapper.updateInfoValueById(id, oldCorpName, newCorpName, userId);
                }
            }

        }

    }


    public R getCompanyList(GetCompanyParam companyParam) {
        Integer pageIndex = companyParam.getPageIndex();
        Integer pageSize = companyParam.getPageSize();

        LoginUser currentUser = LoginUser.getCurrentUser();
        String corpId = currentUser.getCorpId();

        List<String> companyIds = null;

        //查询全部 不传参数
        if (StringUtils.isEmpty(companyParam.getName()) && CollectionUtils.isEmpty(companyParam.getLabelIds())) {
            companyIds = rmCompanyMapper.selectAllCompanyIdsByCorpId(corpId);
        } else if (StringUtils.isNotEmpty(companyParam.getName()) && CollectionUtils.isEmpty(companyParam.getLabelIds())) {
            companyIds = rmCompanyMapper.selectLikeName(companyParam.getName(), corpId);
        } else if (StringUtils.isEmpty(companyParam.getName()) && CollectionUtils.isNotEmpty(companyParam.getLabelIds())) {
            companyIds = rmCompanyLabelRelationMapper.selectCompanyIdsByLabelIds(companyParam.getLabelIds(), corpId);
        }

        if (CollectionUtils.isEmpty(companyIds)) {
            return R.ok();
        }

        // 在分页之前获取总记录数
        int total = companyIds.size();

        // 开始分页并获取分页后的公司ID
        PageHelper.startPage(pageIndex, pageSize);
        List<String> pagedCompanyIds = companyIds.subList(pageIndex * pageSize, Math.min((pageIndex + 1) * pageSize, total));

        List<CompanyVo> companyVos = new ArrayList<>();

        //封装企业信息
        List<RmCompanyInfoArchives> rmCompanyInfoArchives = rmCompanyInfoArchivesMapper.selectCompanyIdsAndCorpId(pagedCompanyIds, corpId);
        Map<String, List<RmCompanyInfoArchives>> archivesMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(rmCompanyInfoArchives)) {
            archivesMap = rmCompanyInfoArchives.stream()
                    .collect(Collectors.groupingBy(RmCompanyInfoArchives::getCompanyId,
                            Collectors.mapping(item -> item, Collectors.toList())));
        }

        //封装标签
        List<RmCompanyLabelRelation> rmCompanyLabelRelations = rmCompanyLabelRelationMapper.selectByCompanyIdAndCorpId(pagedCompanyIds, corpId);
        Map<String, List<RmCompanyLabelRelation>> relationMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(rmCompanyLabelRelations)) {
            relationMap = rmCompanyLabelRelations.stream()
                    .collect(Collectors.groupingBy(RmCompanyLabelRelation::getCompanyId,
                            Collectors.mapping(item -> item, Collectors.toList())));
        }

        for (String companyId : pagedCompanyIds) {
            RmCompany rmCompany = rmCompanyMapper.selectByPrimaryKey(companyId);

            CompanyVo companyVo = new CompanyVo();
            companyVo.setId(companyId);
            companyVo.setCompanyFullName(rmCompany.getCompanyFullName());
            companyVo.setCompanyShortName(rmCompany.getCompanyShortName());
            companyVo.setCompanyLogo(rmCompany.getCompanyLogo());
            companyVo.setCreateTime(rmCompany.getCreateTime());
            companyVo.setCreateBy(rmCompany.getCreateBy());

            List<RmCompanyInfoArchives> infoArchivesList = MapUtils.getObject(archivesMap, companyId);
            List<Map> infoMaps = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(infoArchivesList)) {
                for (RmCompanyInfoArchives infoArchives : infoArchivesList) {
                    String infoId = infoArchives.getInfoId();
                    String infoValue = infoArchives.getInfoValue();
                    String id = infoArchives.getId();
                    Map<String, String> infoMap = new HashMap<>();
                    infoMap.put("id", id);
                    infoMap.put("infoId", infoId);
                    infoMap.put("infoValue", infoValue);
                    infoMaps.add(infoMap);
                }
            }
            companyVo.setInfoMaps(infoMaps);

            List<RmCompanyLabelRelation> labelRelations = MapUtils.getObject(relationMap, companyId);
            List<Map> labelNames = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(labelRelations)) {
                for (RmCompanyLabelRelation labelRelation : labelRelations) {
                    Map<String, String> labelMap = new HashMap<>();
                    String labelId = labelRelation.getLabelId();
                    RmCompanyLabel rmCompanyLabel = rmCompanyLabelMapper.selectByPrimaryKey(labelId);
                    String labelName = rmCompanyLabel.getLabelName();

                    labelMap.put("labelId", labelId);
                    labelMap.put("labelName", labelName);
                    labelNames.add(labelMap);
                }
            }
            companyVo.setLabelNames(labelNames);

            companyVos.add(companyVo);
        }

        // 获取分页后的结果
        PageInfo<CompanyVo> pageInfo = new PageInfo<>(companyVos);

        Map<String, Object> map = new HashMap<>();
        map.put("total", total);
        map.put("rows", pageInfo.getList());
        return R.ok(map);
    }


    @Override
    public R getCompanyById(String id) {
        LoginUser currentUser = LoginUser.getCurrentUser();
        String corpId = currentUser.getCorpId();

        RmCompany rmCompany = rmCompanyMapper.selectByPrimaryKey(id);
        if (ObjectUtils.isEmpty(rmCompany) || !rmCompany.getCorpId().equals(corpId)) {
            return R.fail("没有该数据");
        }

        Map<String, Object> resutMap = new HashMap<>();
        resutMap.put("id", id);
        resutMap.put("companyLogo", rmCompany.getCompanyLogo());
        resutMap.put("companyFullName", rmCompany.getCompanyFullName());
        resutMap.put("companyShortName", rmCompany.getCompanyShortName());
        resutMap.put("creditCode", rmCompany.getCreditCode());
        resutMap.put("createTime", rmCompany.getCreateTime());
        resutMap.put("createBy", rmCompany.getCreateBy());

        List<String> companyIds = new ArrayList<>();
        companyIds.add(id);
        //查询关联标签
        List<Map> labelNames = new ArrayList<>();
        List<RmCompanyLabelRelation> labelRelations = rmCompanyLabelRelationMapper.selectByCompanyIdAndCorpId(companyIds, corpId);
        if (CollectionUtils.isNotEmpty(labelRelations)) {
            for (RmCompanyLabelRelation labelRelation : labelRelations) {
                Map<String, String> labelMap = new HashMap<>();
                RmCompanyLabel rmCompanyLabel = rmCompanyLabelMapper.selectByPrimaryKey(labelRelation.getLabelId());
                String labelName = rmCompanyLabel.getLabelName();
                labelMap.put("labelId", labelRelation.getLabelId());
                labelMap.put("labelName", labelName);
                labelNames.add(labelMap);
            }
        }

        resutMap.put("labelNames", labelNames);
        //封装companyInfo信息
        List<RmCompanyInfoArchives> rmCompanyInfoArchives = rmCompanyInfoArchivesMapper.selectCompanyIdsAndCorpId(companyIds, corpId);
        List<Map> infoMaps = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(rmCompanyInfoArchives)) {
            for (RmCompanyInfoArchives infoArchives : rmCompanyInfoArchives) {
                String infoId = infoArchives.getInfoId();
                String infoValue = infoArchives.getInfoValue();
                RmCompanyInfoFieldConfig rmCompanyInfoFieldConfig = rmCompanyInfoFieldConfigMapper.selectByPrimaryKey(infoId);
                Map<String, Object> infoMap = new HashMap<>();
                infoMap.put("id", infoArchives.getId());
                infoMap.put("infoId", infoId);
                if (rmCompanyInfoFieldConfig.getIsThereMultiple() != null && rmCompanyInfoFieldConfig.getIsThereMultiple() == 1) {
                    infoMap.put("infoValue", JSON.parseObject(infoValue, List.class));
                } else {
                    infoMap.put("infoValue", infoValue);
                }
                infoMap.put("controlType", rmCompanyInfoFieldConfig.getControlType());
                infoMap.put("isThreeMultiple", rmCompanyInfoFieldConfig.getIsThereMultiple());
                infoMaps.add(infoMap);
            }
        }
        resutMap.put("infoMaps", infoMaps);


        //封装管理范围
        List<RmCompanyManage> manageList = rmCompanyManageMapper.selectByCompanyIdAndCropId(id, corpId);
        List<Map> manageMapList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(manageList)) {
            for (RmCompanyManage rmCompanyManage : manageList) {
                Map<String, Object> manageMap = new HashMap<>();
                manageMap.put("rangeId", rmCompanyManage.getRangeId());
                manageMap.put("parentId", rmCompanyManage.getParentId());
                manageMap.put("dataType", rmCompanyManage.getDataType());
                manageMap.put("uniqueId", rmCompanyManage.getUniqueId());
                manageMapList.add(manageMap);
            }
        }
        resutMap.put("manageList", manageMapList);

        return R.ok(resutMap);
    }

    @Override
    public R getCompanyByExternalUserId(String externalUserId) {
        LoginUser currentUser = LoginUser.getCurrentUser();
        String corpId = currentUser.getCorpId();
        String userId = currentUser.getUserId();

        RmExternalInfo rmExternalInfo = new RmExternalInfo();
        rmExternalInfo.setCorpId(corpId);
        rmExternalInfo.setExternalUserId(externalUserId);
        rmExternalInfo.setUserId(userId);
        //状态 1、有效2、把客户删除 3 被客户删除 4、双向删除,
        rmExternalInfo.setStatus(1);

        //外部联系人的类型，1表示该外部联系人是微信用户，2表示该外部联系人是企业微信用户
        rmExternalInfo.setType("2");
        RmExternalInfo externalInfo = rmExternalInfoMapper.selectExternalInfoByExternalInfoOne(rmExternalInfo);
        if (ObjectUtils.isEmpty(externalInfo)) {
            return R.ok(null);
        }
        //全称
        String corpFullName = externalInfo.getCorpFullName();
        //简称
        String corpName = externalInfo.getCorpName();

        if (StringUtils.isEmpty(corpFullName) && StringUtils.isEmpty(corpName)) {
            return R.ok(null);
        }

        //如果 corpFullName 有值，则 result 为 corpFullName；如果 corpFullName 没有值但 corpName 有值，则 result 为 corpName。如果两个都没有值，则 result 为 null。
        String result = !StringUtils.isEmpty(corpFullName) ? corpFullName : corpName;


        return R.ok(result);
    }


    @Override
    public R saveExcelData(List<CompanyExcel> companyExcelList) throws NoSuchFieldException {
        LoginUser currentUser = LoginUser.getCurrentUser();
        String corpId = currentUser.getCorpId();
        String userId = currentUser.getUserId();

        int count = 0;

        // 查询标签
        Map<String, RmCompanyLabel> labelMap = getLabelMap(corpId);

        // 查询配置字段
        Map<String, RmCompanyInfoFieldConfig> companyInfoFieldConfigMap = getCompanyInfoFieldConfigMap(corpId);

        List<RmCompany> companies = new ArrayList<>();
        List<RmCompanyLabelRelation> labelRelations = new ArrayList<>();
        List<RmCompanyInfoArchives> archives = new ArrayList<>();

        for (CompanyExcel companyExcel : companyExcelList) {
            String companyFullName = companyExcel.getCompanyFullName();
            String companyShortName = companyExcel.getCompanyShortName();
            String companyId = UUID.randomUUID().toString();

            RmCompany rmCompany = new RmCompany();
            if (StringUtils.isNotEmpty(companyFullName)) {
                rmCompany.setCompanyFullName(companyFullName);
            }
            if (StringUtils.isNotEmpty(companyShortName)) {
                rmCompany.setCompanyShortName(companyShortName);
            }
            rmCompany.setId(companyId);
            rmCompany.setCreateBy(userId);
            rmCompany.setCreateTime(new Date());
            rmCompany.setCorpId(corpId);
            companies.add(rmCompany);

            // 获取单位类型并保存标签关系
            String unitType = companyExcel.getUnitType();
            if (StringUtils.isNotEmpty(unitType)) {
                List<String> unitTypeList = Arrays.asList(unitType.split(","));
                for (String labelName : unitTypeList) {
                    RmCompanyLabel companyLabel = MapUtils.getObject(labelMap, labelName);
                    if (ObjectUtils.isNotEmpty(companyLabel)) {
                        RmCompanyLabelRelation rmCompanyLabelRelation = new RmCompanyLabelRelation();
                        rmCompanyLabelRelation.setLabelId(companyLabel.getId());
                        rmCompanyLabelRelation.setId(UUID.randomUUID().toString());
                        rmCompanyLabelRelation.setCompanyId(companyId);
                        rmCompanyLabelRelation.setGroupId(companyLabel.getGroupId());
                        rmCompanyLabelRelation.setCorpId(corpId);
                        rmCompanyLabelRelation.setCreateTime(new Date());
                        labelRelations.add(rmCompanyLabelRelation);
                    }
                }
            }
            // 处理其他信息字段并保存档案数据
            saveArchives(companyInfoFieldConfigMap, companyExcel, companyId, corpId, userId, archives);
            count++;
        }

        // 批量插入公司信息
        rmCompanyMapper.saveBatch(companies);
        // 批量插入标签关系
        rmCompanyLabelRelationMapper.saveBatch(labelRelations);
        // 批量插入档案数据
        rmCompanyInfoArchivesMapper.saveBatch(archives);

        return R.ok(null, "上传Excel一共" + companyExcelList.size() + "条数据，处理了" + count + "条数据");
    }


    private void saveArchives(Map<String, RmCompanyInfoFieldConfig> configMap, CompanyExcel companyExcel, String companyId, String corpId, String userId, List<RmCompanyInfoArchives> archives) throws NoSuchFieldException {
        String[][] fields = {
                {"residentUnit", companyExcel.getResidentUnit()},
                {"superiorUnit", companyExcel.getSuperiorUnit()},
                {"fullAddress", companyExcel.getFullAddress()},
                {"dutyPhone", companyExcel.getDutyPhone()},
                {"legalRepresentative", companyExcel.getLegalRepresentative()},
                {"legalRepresentativePhone", companyExcel.getLegalRepresentativePhone()},
                {"principalPerson", companyExcel.getPrincipalPerson()},
                {"principalPersonPhone", companyExcel.getPrincipalPersonPhone()},
                {"liaisonOfficer", companyExcel.getLiaisonOfficer()},
                {"liaisonOfficerPhone", companyExcel.getLiaisonOfficerPhone()},
                {"passOfficer", companyExcel.getPassOfficer()},
                {"passOfficerPhone", companyExcel.getPassOfficerPhone()},
                {"fireOfficer", companyExcel.getFireOfficer()},
                {"fireOfficerPhone", companyExcel.getFireOfficerPhone()},
                {"counterTerrorismLiaison", companyExcel.getCounterTerrorismLiaison()},
                {"counterTerrorismLiaisonPhone", companyExcel.getCounterTerrorismLiaisonPhone()}
        };

        for (String[] field : fields) {
            if (StringUtils.isNotEmpty(field[1])) {
                String excelPropertyValue = getExcelPropertyValue(CompanyExcel.class, field[0]);
                createAndSaveArchives(configMap, excelPropertyValue, field[1], companyId, corpId, userId, archives);
            }
        }
    }

    private void createAndSaveArchives(Map<String, RmCompanyInfoFieldConfig> configMap, String configKey, String infoValue, String companyId, String corpId, String userId, List<RmCompanyInfoArchives> archives) {
        RmCompanyInfoFieldConfig rmCompanyInfoFieldConfig = MapUtils.getObject(configMap, configKey);
        if (rmCompanyInfoFieldConfig != null) {
            RmCompanyInfoArchives rmCompanyInfoArchives = new RmCompanyInfoArchives();
            rmCompanyInfoArchives.setId(UUID.randomUUID().toString());
            rmCompanyInfoArchives.setCompanyId(companyId);
            rmCompanyInfoArchives.setCorpId(corpId);
            rmCompanyInfoArchives.setInfoId(rmCompanyInfoFieldConfig.getId());
            rmCompanyInfoArchives.setInfoValue(infoValue);
            rmCompanyInfoArchives.setCreateUserId(userId);
            rmCompanyInfoArchives.setCreateTime(new Date());
            archives.add(rmCompanyInfoArchives);
        }
    }

    public String getExcelPropertyValue(Class<?> clazz, String propertyName) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(propertyName);
        if (field.isAnnotationPresent(ExcelProperty.class)) {
            ExcelProperty excelProperty = field.getAnnotation(ExcelProperty.class);
            return excelProperty.value()[0];
        }
        return null;
    }

    private Map<String, RmCompanyLabel> getLabelMap(String corpId) {
        List<RmCompanyLabel> companyLabels = rmCompanyLabelMapper.selectAllLabelByCorpId(corpId);
        if (CollectionUtils.isNotEmpty(companyLabels)) {
            return companyLabels.stream().collect(Collectors.toMap(RmCompanyLabel::getLabelName, Function.identity(), (k1, k2) -> k1));
        }
        return null;
    }

    private Map<String, RmCompanyInfoFieldConfig> getCompanyInfoFieldConfigMap(String corpId) {
        List<RmCompanyInfoFieldConfig> rmCompanyInfoFieldConfigs = rmCompanyInfoFieldConfigMapper.selectComPanyInfoByCorpId(corpId);
        if (CollectionUtils.isNotEmpty(rmCompanyInfoFieldConfigs)) {
            return rmCompanyInfoFieldConfigs.stream().collect(Collectors.toMap(RmCompanyInfoFieldConfig::getInfoName, Function.identity(), (k1, k2) -> k1));
        }
        return null;
    }


    @Override
    public R schedSaveCompanyExternalRation(Map req) {
        //req不为空   就是加好友
        if (req != null) {
            String externalUserId = MapUtils.getString(req, "externalUserId");
            String userId = MapUtils.getString(req, "userId");
            String corpId = MapUtils.getString(req, "corpId");
            //加好友来自哪个企业
            String corpName = MapUtils.getString(req, "corpName");

            //查询公司是否存在
            List<RmCompany> rmCompanyList = rmCompanyMapper.selectByFullName(corpName, corpId);
            //存在
            if (CollectionUtils.isNotEmpty(rmCompanyList)) {
                RmCompany rmCompany = rmCompanyList.get(0);
                req.put("companyId", rmCompany.getId());
                createAndSaveExternalArchives(req, null);
            } else {
                //不存在
                RmCompany rmCompany = new RmCompany();
                String companyId = UUID.randomUUID().toString();
                rmCompany.setId(companyId);
                rmCompany.setCompanyFullName(corpName);
                rmCompany.setCompanyShortName(corpName);
                rmCompany.setCorpId(corpId);
                rmCompany.setCreateBy(userId);
                rmCompany.setCreateTime(new Date());
                //保存企业
                rmCompanyMapper.insertSelective(rmCompany);
                req.put("companyId", companyId);
                createAndSaveExternalArchives(req, null);
            }

        } else {

            //todo 查询公司详情
            WeChatThirdCompany weChatThirdCompany = new WeChatThirdCompany();
            weChatThirdCompany.setSuiteId(constant.getSuiteID());
            R<List<WeChatThirdCompany>> selectAllCompany = weChatThirdCompanyService.selectAllCompany(weChatThirdCompany);
            System.err.println("查询出的公司信息:" + JSON.toJSONString(selectAllCompany));
            if (selectAllCompany != null && selectAllCompany.getData() != null) {
                List<WeChatThirdCompany> qywxThirdCompanyList = selectAllCompany.getData();
                for (WeChatThirdCompany thirdCompany : qywxThirdCompanyList) {
                    //todo 循环企业
//                //查询当前企业外部联系人 档案为空的-认为是新数据
                    List<RmExternalInfo> rmExternalInfoList = rmExternalInfoMapper.selectByStatusAndType(thirdCompany.getCorpId());
                    List<Map> archivesMap = rmExternalInfoArchivesMapper.selectArchiveByCorpId(thirdCompany.getCorpId());

                    if (CollectionUtils.isNotEmpty(rmExternalInfoList)) {
                        for (RmExternalInfo externalInfo : rmExternalInfoList) {

                            //获取外部联系人的企业名称
                            String corpName = externalInfo.getCorpName();
                            String userId = externalInfo.getUserId();
                            String corpId = externalInfo.getCorpId();
                            String externalUserId = externalInfo.getExternalUserId();

                            Map<String, String> paramMap = new HashMap<>();
                            paramMap.put("externalUserId", externalUserId);
                            paramMap.put("userId", userId);
                            paramMap.put("corpId", corpId);

                            if (StringUtils.isNotEmpty(corpName)) {
                                paramMap.put("corpName", corpName);
                                //存在公司
//                            List<RmCompany> companyListCollected = rmCompanyList.stream().
//                                    filter(p -> p.getCompanyFullName().equals(corpName) && p.getCorpId().equals(corpId)).
//                                    collect(Collectors.toList());
                                //查询当前企业已维护的公司   这里要实时去查  因为下边新增完了   上边只查一次  新增过的公司  永远为空
                                List<RmCompany> rmCompanyList = rmCompanyMapper.selectByFullName(corpName, thirdCompany.getCorpId());


                             /*   List<Map> collect = archivesMap.stream().filter(p -> MapUtils.getString(p, "external_user_id").equals(externalUserId)
                                        && MapUtils.getString(p, "user_id").equals(userId)).collect(Collectors.toList());*/
                                //过滤出外部联系人id匹配存在的档案
                                List<Map> hasArchives = archivesMap.stream().filter(p -> MapUtils.getString(p, "external_user_id").equals(externalUserId)
                                        ).collect(Collectors.toList());

                                //存在
                                if (CollectionUtils.isNotEmpty(rmCompanyList)) {
                                    RmCompany rmCompany = rmCompanyList.get(0);
                                    paramMap.put("companyId", rmCompany.getId());
                                    createAndSaveExternalArchives(paramMap, hasArchives);
                                } else {
                                    //不存在
                                    RmCompany rmCompany = new RmCompany();
                                    String companyId = UUID.randomUUID().toString();
                                    rmCompany.setId(companyId);
                                    rmCompany.setCompanyFullName(corpName);
                                    rmCompany.setCompanyShortName(corpName);
                                    rmCompany.setCorpId(corpId);
                                    rmCompany.setCreateBy(userId);
                                    rmCompany.setCreateTime(new Date());
                                    //保存企业
                                    rmCompanyMapper.insertSelective(rmCompany);
                                    paramMap.put("companyId", companyId);
                                    createAndSaveExternalArchives(paramMap, hasArchives);
                                }
                            }
                        }
                    }
                }

            }
        }
        return R.ok();
    }

    /**
     * @param paramMap 入参
     * @param collect  根据 externalUserId有单位档案的数据
     */
    private void createAndSaveExternalArchives(Map<String, String> paramMap, List<Map> collect) {
        String corpName = MapUtils.getString(paramMap, "corpName");
        String corpId = MapUtils.getString(paramMap, "corpId");
        String companyId = MapUtils.getString(paramMap, "companyId");
        String externalUserId = MapUtils.getString(paramMap, "externalUserId");
        String userId = MapUtils.getString(paramMap, "userId");

        //查出该企业所有的配置字段
        List<RmExternalInfoFieldConfig> rmExternalInfoFieldConfigs = rmExternalInfoFieldConfigMapper.selectByCorpId(corpId);
        if (CollectionUtils.isEmpty(rmExternalInfoFieldConfigs)) {
            //初始化企业字段
            rmExternalInfoFieldConfigMapper.initCorpExternalInfoFieldConfig(corpId);
            rmExternalInfoFieldConfigs = rmExternalInfoFieldConfigMapper.selectByCorpId(corpId);
        }
        if (CollectionUtils.isNotEmpty(rmExternalInfoFieldConfigs)) {

          /*  Map<String, RmExternalInfoFieldConfig> fieldConfigMap = rmExternalInfoFieldConfigs.stream().collect(Collectors.toMap(RmExternalInfoFieldConfig::getInfoName, Function.identity(), (k1, k2) -> k1));

            RmExternalInfoFieldConfig fieldConfig = MapUtils.getObject(fieldConfigMap, "单位");*/

            RmExternalInfoFieldConfig fieldConfig = rmExternalInfoFieldConfigs.stream().filter(p -> p.getControlName().equals("单位")).findFirst().get();

            //如果没有单位档案
            if (CollectionUtils.isEmpty(collect)) {
                if (fieldConfig != null) {
                    List<Map<String, Object>> infoValueList = new ArrayList<>();
                    RmExternalInfoArchives rmExternalInfoArchives = new RmExternalInfoArchives();
                    rmExternalInfoArchives.setId(UUID.randomUUID().toString());
                    rmExternalInfoArchives.setInfoId(fieldConfig.getId());
                    rmExternalInfoArchives.setExternalUserId(externalUserId);
                    rmExternalInfoArchives.setCreateUserId(userId);
                    rmExternalInfoArchives.setCorpId(corpId);
                    rmExternalInfoArchives.setCreateTime(new Date());
                    Map<String, Object> infoValueMap = getStringObjectMap(corpName, companyId);
                    infoValueList.add(infoValueMap);
                    String infoString = JSON.toJSONString(infoValueList);

                    //封装JSON
                    rmExternalInfoArchives.setInfoValue(infoString);
                    rmExternalInfoArchivesMapper.insertSelective(rmExternalInfoArchives);
                    // todo 同步 es
                   // rmEsSynDataService.saveEsData(null, externalUserId, corpId, EsSynDataUpdateEnum.UPDATE_ARCHIVES.getCode(), 0);
                }
            } else {
                //有单位档案
                Map map = collect.get(0);
                String infoValue = MapUtils.getString(map, "info_value");
                String reiaId = MapUtils.getString(map, "reiaId");
                //但是 infoValue为空
                if (StringUtils.isEmpty(infoValue)) {
                    List<Map<String, Object>> infoValueList = new ArrayList<>();
                    RmExternalInfoArchives rmExternalInfoArchives = new RmExternalInfoArchives();
                    Map<String, Object> infoValueMap = getStringObjectMap(corpName, companyId);
                    infoValueList.add(infoValueMap);
                    String infoString = JSON.toJSONString(infoValueList);
                    rmExternalInfoArchives.setId(reiaId);
                    //封装JSON
                    rmExternalInfoArchives.setInfoValue(infoString);
                    rmExternalInfoArchivesMapper.updateByPrimaryKeySelective(rmExternalInfoArchives);
                    // todo 同步 es
                   // rmEsSynDataService.saveEsData(userId, externalUserId, corpId, EsSynDataUpdateEnum.UPDATE_ARCHIVES.getCode(), 0);
                } else {

                    List<Map<String, Object>> list = JSON.parseObject(infoValue, List.class);
                    Map<String, Object> infoValueMap = getStringObjectMap(corpName, companyId);
                    list.add(infoValueMap);
                    String infoString = JSON.toJSONString(list);

                    RmExternalInfoArchives rmExternalInfoArchives = new RmExternalInfoArchives();
                    rmExternalInfoArchives.setId(reiaId);
                    //封装JSON
                    rmExternalInfoArchives.setInfoValue(infoString);
                    rmExternalInfoArchivesMapper.updateByPrimaryKeySelective(rmExternalInfoArchives);
                    // todo 同步 es
                   // rmEsSynDataService.saveEsData(null, externalUserId, corpId, EsSynDataUpdateEnum.UPDATE_ARCHIVES.getCode(), 0);
                }
            }


        }

    }

    private Map<String, Object> getStringObjectMap(String corpName, String companyId) {
        Map<String, Object> infoValueMap = new HashMap<>();
        infoValueMap.put("corpName", corpName);
        infoValueMap.put("workType", null);
        infoValueMap.put("companyId", companyId);
        return infoValueMap;
    }


}
