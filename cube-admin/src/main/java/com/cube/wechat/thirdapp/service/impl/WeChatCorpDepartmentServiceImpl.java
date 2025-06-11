package com.cube.wechat.thirdapp.service.impl;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.WeChatCorpDepartment;
import com.cube.wechat.thirdapp.entiy.WeChatUserDepartment;
import com.cube.wechat.thirdapp.mapper.WeChatCorpDepartmentMapper;
import com.cube.wechat.thirdapp.mapper.WeChatUserDepartmentMapper;
import com.cube.wechat.thirdapp.param.WeChatCorpDepartmentParam;
import com.cube.wechat.thirdapp.service.WeChatCorpDepartmentService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author sjl
 * @Created date 2024/3/1 10:00
 */
@Service
public class WeChatCorpDepartmentServiceImpl implements WeChatCorpDepartmentService {
    @Autowired
    private WeChatCorpDepartmentMapper weChatCorpDepartmentMapper;
    @Autowired
    private WeChatUserDepartmentMapper weChatUserDepartmentMapper;

    @Override
    public R saveCorpDepartment(List<WeChatCorpDepartmentParam> qywxCorpDepartmentParamList) {
        if (CollectionUtils.isNotEmpty(qywxCorpDepartmentParamList)) {
            for (WeChatCorpDepartmentParam qywxCorpDepartmentParam : qywxCorpDepartmentParamList) {
                //企业id
                String corpId = qywxCorpDepartmentParam.getCorpId();
                //应用id
                String suiteId = qywxCorpDepartmentParam.getSuiteId();

                //保存最新的
                List<WeChatCorpDepartment> qywxCorpDepartmentList = qywxCorpDepartmentParam.getQywxCorpDepartmentList();
                if (qywxCorpDepartmentList != null && qywxCorpDepartmentList.size() > 0) {
                    for (WeChatCorpDepartment weChatCorpDepartment : qywxCorpDepartmentList) {
                        //查询是否已存在
                        weChatCorpDepartment.setCorpId(corpId);
                        weChatCorpDepartment.setSuiteId(suiteId);
                        WeChatCorpDepartment qywxCorpDepartmentSelected = weChatCorpDepartmentMapper.selectDepartmentByIdAndSuiteIdAndCorpId(weChatCorpDepartment);
                        if (ObjectUtils.isNotEmpty(qywxCorpDepartmentSelected)) {
                            //存在，更新状态为有效
                            qywxCorpDepartmentSelected.setDepartmentStatus(1);
                            qywxCorpDepartmentSelected.setUpdateDate(new Date());
                            weChatCorpDepartmentMapper.updateByPrimaryKeySelective(qywxCorpDepartmentSelected);
                        } else {
                            //不存在，新增
                            weChatCorpDepartment.setId(UUID.randomUUID().toString());
                            weChatCorpDepartment.setDepartmentStatus(1);
                            weChatCorpDepartment.setCreateDate(new Date());
                            weChatCorpDepartment.setSuiteId(suiteId);
                            weChatCorpDepartment.setCorpId(corpId);
                            weChatCorpDepartmentMapper.insertSelective(weChatCorpDepartment);
                        }
                    }
                }
            }
        }
        return R.ok();
    }

    @Override
    public R updateCorpDepartment(WeChatCorpDepartment weChatCorpDepartment) {
        try {
            //查询是否已存在
            WeChatCorpDepartment corpDepartment = weChatCorpDepartmentMapper.selectDepartmentByIdAndSuiteIdAndCorpId(weChatCorpDepartment);
            if (ObjectUtils.isNotEmpty(corpDepartment)) {
                //更新
                weChatCorpDepartment.setUpdateDate(new Date());
                weChatCorpDepartment.setDepartmentStatus(1);
                weChatCorpDepartment.setId(corpDepartment.getId());
                weChatCorpDepartmentMapper.updateByPrimaryKeySelective(weChatCorpDepartment);
            } else {
                //新增
                weChatCorpDepartment.setId(UUID.randomUUID().toString());
                weChatCorpDepartment.setCreateDate(new Date());
                weChatCorpDepartment.setDepartmentStatus(1);
                weChatCorpDepartmentMapper.insertSelective(weChatCorpDepartment);
            }
            //更新企业部门路径
            weChatCorpDepartmentMapper.updateDepartmentFullPath(weChatCorpDepartment.getCorpId());
            return R.ok();
        }catch (Exception e){
            e.printStackTrace();
            return R.fail("更新部门失败，发生异常");
        }
    }

    @Override
    public R deleteCorpDepartment(WeChatCorpDepartment weChatCorpDepartment) {
        try {
            //查询是否已存在
            WeChatCorpDepartment corpDepartment = weChatCorpDepartmentMapper.selectDepartmentByIdAndSuiteIdAndCorpId(weChatCorpDepartment);
            if (ObjectUtils.isNotEmpty(corpDepartment)) {
                //更新为无效状态
                weChatCorpDepartment.setUpdateDate(new Date());
                weChatCorpDepartment.setDepartmentStatus(0);
                weChatCorpDepartment.setId(corpDepartment.getId());
                weChatCorpDepartmentMapper.updateByPrimaryKeySelective(weChatCorpDepartment);
                //删除人员和部门绑定关系
                WeChatUserDepartment weChatUserDepartment = new WeChatUserDepartment();
                weChatUserDepartment.setDepartmentId(weChatUserDepartment.getDepartmentId());
                weChatUserDepartment.setSuiteId(weChatUserDepartment.getSuiteId());
                weChatUserDepartment.setCorpId(weChatUserDepartment.getCorpId());
                weChatUserDepartmentMapper.deleteUserDeparementByDepartmentId(weChatUserDepartment);
            }
            return R.ok("部门删除成功");
        }catch (Exception e){
            e.printStackTrace();
            return R.fail("部门删除失败，发生异常");
        }

    }

    @Override
    public R deleteAllCorpDepartment(WeChatCorpDepartment weChatCorpDepartment) {
        weChatCorpDepartmentMapper.updateStautsByCorpIdAndSuiteId("0", weChatCorpDepartment.getCorpId(), weChatCorpDepartment.getSuiteId());
        return R.ok();
    }

    @Override
    public R<List<Map>> selectTreeUserDepartment(WeChatCorpDepartment qywxCorpDepartment) {
        List<Map> treeUserDepartment = weChatCorpDepartmentMapper.selectTreeUserDepartment(qywxCorpDepartment);
        return R.ok(treeUserDepartment);
    }

    /**
     * 查询部门组
     *
     * @return
     */

    @Override
    public R<List<Map>> selectPrimaryDepartment(Map map) {
        List<Map> dataList = new ArrayList<>();
        List<Map> departmentList;
        String parentId = MapUtils.getString(map, "parentId");
        //查询一级
        if (StringUtils.isEmpty(parentId) || (StringUtils.isNotEmpty(parentId) && parentId.equals("1"))) {
            //查询一级部门
            departmentList = weChatCorpDepartmentMapper.selectPrimaryDepartment(map);
            map.put("parentId", 1);
        } else {
            //查询下级部门
            departmentList = weChatCorpDepartmentMapper.selectSubordinateDepartments(map);
        }
        if (CollectionUtils.isNotEmpty(departmentList)) {
            dataList.addAll(departmentList);
        }
        //查询平级人员
        List<Map> userList = weChatCorpDepartmentMapper.selectSubordinatePersonnel(map);
        if (CollectionUtils.isNotEmpty(userList)) {
            dataList.addAll(userList);
        }
        return R.ok(dataList);
    }

    @Override
    public R<List<Map>> selectMainDepartment(Map map) {
        String parentId = MapUtils.getString(map, "parentId");
        if (StringUtils.isEmpty(parentId) || (StringUtils.isNotEmpty(parentId) && parentId.equals("1"))) {
            //查询一级部门
            List<Map> departmentList = weChatCorpDepartmentMapper.selectPrimaryDepartment(map);
            return R.ok(departmentList);
        }else{
            //查询下级部门
            List<Map> departmentList = weChatCorpDepartmentMapper.selectSubordinateDepartments(map);
            return R.ok(departmentList);
        }
    }

    @Override
    public R<List<Map>> selectManagetDepartment(Map map) {
        List<Map> rangeList = (List<Map>) map.get("rangeList");
        if (rangeList != null && rangeList.size() > 0) {

        }
        return null;
    }

    @Override
    public R selectAllDepartment(String corpId, String suiteId) {
        List<Map<String, List<String>>> res = new ArrayList<>();
        //查询所有部门id
        List<WeChatCorpDepartment> weChatCorpDepartments = weChatCorpDepartmentMapper.selectAllDepartmentByCorpId(corpId, suiteId);
        // 初始化部门Map，用于保存部门ID及其对应的部门信息
        Map<String, Map<String, Object>> departmentMap = new HashMap<>();
        // 将所有部门加入到部门Map中
        for (WeChatCorpDepartment weChatCorpDepartment : weChatCorpDepartments) {
            Map<String, Object> departmentInfo = new HashMap<>();
            departmentInfo.put("departmentId", weChatCorpDepartment.getDepartmentId());
            departmentInfo.put("otherDepartments", new ArrayList<String>());
            departmentMap.put(weChatCorpDepartment.getDepartmentId(), departmentInfo);
        }
        // 获取所有父部门ID
        HashSet<String> parentIds = new HashSet<>();
        for (WeChatCorpDepartment weChatCorpDepartment : weChatCorpDepartments) {
            parentIds.add(weChatCorpDepartment.getDepartmentParentId());
        }
        // 遍历每个父部门ID
        for (String parentId : parentIds) {
            if (parentId.equals("0")){
                continue;
            }
            StringBuilder sb = new StringBuilder();
            List<String> arrayList = new ArrayList<>();
            String id = parentId;
            // 迭代查询子部门直到没有子部门
            while (true) {
                List<String> departmentIds = weChatCorpDepartmentMapper.selectAllDepartmentIdByCorpId(corpId, suiteId, parentId);
                arrayList.addAll(departmentIds); // 将子部门ID添加到列表中
                if (departmentIds.isEmpty()) {
                    departmentMap.get(id).put("otherDepartments", arrayList); // 更新部门ID的其他部门信息
                    break;
                }
                // 将数组中的元素用逗号连接起来
                for (int i = 0; i < departmentIds.size(); i++) {
                    sb.append(departmentIds.get(i));
                    if (i < departmentIds.size() - 1) {
                        sb.append(",");
                    }
                }
                parentId = sb.toString(); // 更新父部门ID为子部门ID
                sb.setLength(0); // 清空StringBuilder，准备下一次迭代
            }
        }
        // 初始化部门数组
        List<Map<String, Object>> departmentArray = new ArrayList<>(departmentMap.values());
        departmentArray.sort((department1, department2) -> {
            String departmentId1 = (String) department1.get("departmentId");
            String departmentId2 = (String) department2.get("departmentId");
            return Integer.compare(Integer.parseInt(departmentId1), Integer.parseInt(departmentId2));
        });
        //查询数据
        for (Map<String, Object> department : departmentArray) {
            HashMap<String, List<String>> map = new HashMap<>();
            String departmentId = (String) department.get("departmentId");
            HashSet<String> hashSet = new HashSet<>();
            List<String> strings = weChatUserDepartmentMapper.selectUserDepartmentByDepartmentId(corpId, suiteId, departmentId);
            if (strings != null && !strings.isEmpty()) {
                hashSet.addAll(strings);
            }
            List<String> childIds = (List<String>) department.get("otherDepartments");
            if (childIds != null && !childIds.isEmpty()){
                for (String childId : childIds) {
                    //通过部门id查询人员id
                    List<String> userDepartments = weChatUserDepartmentMapper.selectUserDepartmentByDepartmentId(corpId, suiteId, childId);
                    if (userDepartments != null && !userDepartments.isEmpty()) {
                        hashSet.addAll(userDepartments);
                    }
                }
            }
            ArrayList<String> list = new ArrayList<>(hashSet);
            map.put(departmentId,list);
            res.add(map);
        }
        return R.ok(res);
    }
}
