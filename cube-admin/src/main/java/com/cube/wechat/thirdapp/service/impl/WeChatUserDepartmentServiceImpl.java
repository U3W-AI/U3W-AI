package com.cube.wechat.thirdapp.service.impl;

import com.alibaba.fastjson.JSON;
import com.cube.common.core.domain.R;
import com.cube.common.utils.StringUtils;
import com.cube.wechat.thirdapp.entiy.WeChatCorpDepartment;
import com.cube.wechat.thirdapp.entiy.WeChatCorpUser;
import com.cube.wechat.thirdapp.entiy.WeChatHierarchicalReturnResult;
import com.cube.wechat.thirdapp.entiy.WeChatUserDepartment;
import com.cube.wechat.thirdapp.mapper.WeChatCorpDepartmentMapper;
import com.cube.wechat.thirdapp.mapper.WeChatCorpUserMapper;
import com.cube.wechat.thirdapp.mapper.WeChatUserDepartmentMapper;
import com.cube.wechat.thirdapp.service.WeChatDataService;
import com.cube.wechat.thirdapp.service.WeChatUserDepartmentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 @author sjl
  * @Created date 2024/3/4 11:10
 */
@Service
@Slf4j
public class WeChatUserDepartmentServiceImpl implements WeChatUserDepartmentService {
    private final static Logger logger = LoggerFactory.getLogger(WeChatUserDepartmentServiceImpl.class);
    @Autowired
    private WeChatUserDepartmentMapper weChatUserDepartmentMapper;
    @Autowired
    private WeChatDataService wechatDataService;
    @Autowired
    private WeChatCorpUserMapper weChatCorpUserMapper;
    @Autowired
    private WeChatCorpDepartmentMapper weChatCorpDepartmentMapper;


    @Override
    public R deleteUserDepartment(WeChatUserDepartment weChatUserDepartment) {
        weChatUserDepartmentMapper.deleteUserDepartment(weChatUserDepartment);
        return R.ok();
    }

    @Override
    public R<Map> synchronizeUserDepartment(String corpId, String suiteId) {
        logger.info("===================开始获取企业部门数据=================");
        //获取企业访问配置
        R<String> corpAccessTokenR = wechatDataService.queryCorpAccessToken(corpId);
        if (corpAccessTokenR.getCode() != R.SUCCESS) {
            return R.fail("获取企业访问凭证失败");
        }
        //查询企业部门数据
        List<WeChatCorpDepartment> qywxCorpDepartments = weChatCorpDepartmentMapper.selectAllDepartmentByCorpId(corpId, suiteId);
        if (CollectionUtils.isEmpty(qywxCorpDepartments)) {
            qywxCorpDepartments = new ArrayList<>();
        }
        logger.info("已存在部门：" + JSON.toJSONString(qywxCorpDepartments));
        //查询企业所有人员
        WeChatCorpUser weChatCorpUser = new WeChatCorpUser();
        weChatCorpUser.setCorpId(corpId);
        weChatCorpUser.setSuiteId(suiteId);
        List<WeChatCorpUser> qywxCorpUserList = weChatCorpUserMapper.selectAllUserByCorpId(weChatCorpUser);
        logger.info("已存在人员：" + JSON.toJSONString(qywxCorpUserList));
        if (CollectionUtils.isEmpty(qywxCorpUserList)) {
            qywxCorpUserList = new ArrayList<>();
        }
        //查询企业部门人员关联关系表
        List<WeChatUserDepartment> qywxUserDepartmentList = weChatUserDepartmentMapper.selectAllUserDepartmentByCorpId(corpId, suiteId);
        if (CollectionUtils.isEmpty(qywxUserDepartmentList)) {
            qywxUserDepartmentList = new ArrayList<>();
        }
        String accessToken = corpAccessTokenR.getData();
        if (StringUtils.isNotEmpty(accessToken)) {
            R<Map> departmentIdListR = wechatDataService.queryCorpDepartmentIdList(corpId);
            if (departmentIdListR.getCode() != R.SUCCESS) {
                logger.error("===================获取企业部门数据失败=================");
                return R.fail("获取企业部门数据失败");
            } else {
                logger.info("===================开始获取企业部门下人员数据=================");
                Map departmentIdListRData = departmentIdListR.getData();
                List<WeChatCorpDepartment> insertCorpDepartmentList = new ArrayList<>();
                List<WeChatCorpDepartment> updateCorpDepartmentList = new ArrayList<>();
                List<WeChatCorpUser> insertUserList = new ArrayList<>();
                List<WeChatCorpUser> updateUserList = new ArrayList<>();
                List<WeChatUserDepartment> insertUserDepartmentList = new ArrayList<>();
                List<Map> departmentIdMapList = (List<Map>) departmentIdListRData.get("department_id");
                if (CollectionUtils.isNotEmpty(departmentIdMapList)) {
                    boolean hasOneId = departmentIdMapList.parallelStream()
                            .anyMatch(p -> MapUtils.getInteger(p, "id") == 1);
                    //判断是否存在部门1。不存在则新增部门1
                    if (!hasOneId) {
                        Map<String, Object> departmentMap_1 = new HashMap<>();
                        departmentMap_1.put("id",1);
                        departmentMap_1.put("parentid",0);
                        departmentMap_1.put("order",000000000);
                        departmentIdMapList.add(departmentMap_1);
                    }
                    for (Map map : departmentIdMapList) {
                        logger.info("部门------>" + map);
                        WeChatCorpDepartment qywxCorpDepartment = new WeChatCorpDepartment();
                        String departmentId = MapUtils.getString(map, "id");
                        qywxCorpDepartment.setDepartmentId(departmentId);
                        qywxCorpDepartment.setDepartmentStatus(1);
                        String parentid = MapUtils.getString(map, "parentid");
                        if(!departmentId.equals("1")){
                            List<Map> collected = departmentIdMapList.stream().filter(p -> MapUtils.getString(p, "id").equals(parentid)).collect(Collectors.toList());
                            if(collected!=null&&collected.size()>0){
                                qywxCorpDepartment.setDepartmentParentId(parentid);
                            }else{
                                qywxCorpDepartment.setDepartmentParentId("1");
                            }
                        }else{
                            qywxCorpDepartment.setDepartmentParentId(parentid);
                        }
                        qywxCorpDepartment.setDepartmentOrder(MapUtils.getString(map, "order"));
                        qywxCorpDepartment.setSuiteId(suiteId);
                        qywxCorpDepartment.setCorpId(corpId);
                        qywxCorpDepartment.setDepartmentName("");
                        //校验是否存在
                        List<WeChatCorpDepartment> corpDepartmentListEd = qywxCorpDepartments.stream().filter(p -> p.getDepartmentId().equals(departmentId)).collect(Collectors.toList());
                        if (CollectionUtils.isNotEmpty(corpDepartmentListEd)) {
                            //存在
                            WeChatCorpDepartment corpDepartment = corpDepartmentListEd.get(0);
                            //更新部门状态为有效
                            corpDepartment.setDepartmentStatus(1);
                            corpDepartment.setUpdateDate(new Date());
                            corpDepartment.setDepartmentParentId(qywxCorpDepartment.getDepartmentParentId());
                            updateCorpDepartmentList.add(corpDepartment);
                        } else {
                            //新增
                            qywxCorpDepartment.setCreateDate(new Date());
                            qywxCorpDepartment.setId(UUID.randomUUID().toString());
                            //新增部门状态有效
                            qywxCorpDepartment.setDepartmentStatus(1);
                            qywxCorpDepartment.setUpdateDate(new Date());
                            insertCorpDepartmentList.add(qywxCorpDepartment);
                        }
                        R<Map> childUserR = wechatDataService.queryDepartmentChildUser(accessToken, departmentId);

                        logger.error(departmentId + "============>下人员" + JSON.toJSONString(childUserR));
                        if (childUserR.getCode() == R.SUCCESS) {
                            Map responseUserMap = childUserR.getData();
                            List<Map> userList = (List<Map>) responseUserMap.get("userlist");
                            logger.error(departmentId + "============>下人员集合:" + JSON.toJSONString(userList));
                            if (CollectionUtils.isNotEmpty(userList)) {
                                for (Map userMap : userList) {
                                    String systemUserId = UUID.randomUUID().toString();
                                    WeChatCorpUser qywxCorpUser = new WeChatCorpUser();
                                    String userid = MapUtils.getString(userMap, "userid");
                                   // R<Map> personDetailR = wechatDataService.queryPersonnelDetails(userid, accessToken);
                          /*          if (personDetailR.getCode() == R.SUCCESS) {
                                        Map userInfoMap = personDetailR.getData();
                                        logger.error("===================获取人员详情数据成功=================\n" + JSON.toJSONString(userInfoMap));
                                        String mainDepartment = MapUtils.getString(userInfoMap, "main_department");
                                        Integer status = MapUtils.getInteger(userInfoMap, "status");
                                        //主部门id
                                        qywxCorpUser.setDepartmentId(mainDepartment);
                                        qywxCorpUser.setStatus(status);
                                        List<Integer> orderList = (List<Integer>) userInfoMap.get("order");
                                        if (orderList != null && orderList.size() > 0) {
                                            Integer order = orderList.get(0);
                                            qywxCorpUser.setOrder(order);
                                        }
                                        //获取人员所属部门列表
                                        List<Integer> departmentList = (List<Integer>) userInfoMap.get("department");
                                        if (departmentList != null && departmentList.size() > 0) {
                                            if (StringUtils.isEmpty(mainDepartment)) {
                                                //默认第一个为主部门
                                                qywxCorpUser.setDepartmentId(departmentList.get(0).toString());
                                            }
                                        }
                                        String thumbAvatar = MapUtils.getString(userInfoMap, "thumb_avatar");
                                        qywxCorpUser.setThumbAvatar(thumbAvatar);
                                        String open_userid = MapUtils.getString(userMap, "open_userid");
                                        qywxCorpUser.setUserId(userid);
                                        qywxCorpUser.setOpenUserid(open_userid);
                                        qywxCorpUser.setCorpId(corpId);
                                        qywxCorpUser.setSuiteId(suiteId);
                                        qywxCorpUser.setUpdateDate(new Date());
                                        qywxCorpUser.setUserName("");
                                        qywxCorpUser.setPosition("");

                                        //是否存在
                                        List<QywxCorpUser> collectUserList = qywxCorpUserList.stream().filter(p -> p.getUserId().equals(userid) && p.getCorpId().equals(corpId)).collect(Collectors.toList());
                                        if (CollectionUtils.isNotEmpty(collectUserList)) {
                                            //存在
                                            QywxCorpUser corpUser = collectUserList.get(0);
                                            systemUserId = corpUser.getId();
                                            qywxCorpUser.setId(systemUserId);
                                            updateUserList.add(qywxCorpUser);
                                        } else {
                                            //不存在
                                            qywxCorpUser.setId(systemUserId);
                                            qywxCorpUser.setCreateDate(new Date());
                                            qywxCorpUser.setStatus(status);
                                            List<QywxCorpUser> collect = insertUserList.stream().filter(p -> p.getUserId().equals(userid) && p.getCorpId().equals(corpId)).collect(Collectors.toList());
                                            if (CollectionUtils.isEmpty(collect)) {
                                                insertUserList.add(qywxCorpUser);
                                            }
                                        }
                                    }*/

                                    qywxCorpUser.setThumbAvatar("");
                                    qywxCorpUser.setDepartmentId("");
                                    String open_userid = MapUtils.getString(userMap, "open_userid");
                                    qywxCorpUser.setUserId(userid);
                                    qywxCorpUser.setOpenUserid(open_userid);
                                    qywxCorpUser.setCorpId(corpId);
                                    qywxCorpUser.setSuiteId(suiteId);
                                    qywxCorpUser.setUpdateDate(new Date());
                                    qywxCorpUser.setUserName("");
                                    qywxCorpUser.setPosition("");

                                    //是否存在
                                    List<WeChatCorpUser> collectUserList = qywxCorpUserList.stream().filter(p -> p.getUserId().equals(userid) && p.getCorpId().equals(corpId)).collect(Collectors.toList());
                                    if (CollectionUtils.isNotEmpty(collectUserList)) {
                                        //存在
                                        WeChatCorpUser corpUser = collectUserList.get(0);
                                        systemUserId = corpUser.getId();
                                        qywxCorpUser.setId(systemUserId);
                                        updateUserList.add(qywxCorpUser);
                                    } else {
                                        //不存在
                                        qywxCorpUser.setId(systemUserId);
                                        qywxCorpUser.setCreateDate(new Date());
                                        qywxCorpUser.setStatus(1);
                                        List<WeChatCorpUser> collect = insertUserList.stream().filter(p -> p.getUserId().equals(userid) && p.getCorpId().equals(corpId)).collect(Collectors.toList());
                                        if (CollectionUtils.isEmpty(collect)) {
                                            insertUserList.add(qywxCorpUser);
                                        }
                                    }

                                    //用户关联部门
                                    WeChatUserDepartment userDepartment = new WeChatUserDepartment();
                                    userDepartment.setDepartmentId(departmentId);
                                    userDepartment.setUserId(userid);
                                    userDepartment.setCorpId(corpId);
                                    userDepartment.setSuiteId(suiteId);
                                    userDepartment.setSystemUserId(systemUserId);
                                    //校验人员部门关系是否存在
                                    List<WeChatUserDepartment> collectUserDepartmentLst = qywxUserDepartmentList.stream().filter(p -> p.getDepartmentId().equals(departmentId) && p.getUserId().equals(userid)).collect(Collectors.toList());
                                    if (CollectionUtils.isEmpty(collectUserDepartmentLst)) {
                                        //不存在，新增
                                        insertUserDepartmentList.add(userDepartment);
                                    }
                                }
                            }
                        }
                    }
                }
                //批量新增部门
                if (CollectionUtils.isNotEmpty(insertCorpDepartmentList)) {
                    logger.info("==============开始批量新增部门==================");
                    weChatCorpDepartmentMapper.insertBatch(insertCorpDepartmentList);
                    logger.info("==============批量新增部门结束==================");
                }
                //批量更新部门
                if (CollectionUtils.isNotEmpty(updateCorpDepartmentList)) {
                    logger.info("==============开始批量更新部门==================");
                    weChatCorpDepartmentMapper.updateBatchByPrimaryKey(updateCorpDepartmentList);
                    logger.info("==============批量更新部门结束==================");
                }
                //批量新增人员
                if (CollectionUtils.isNotEmpty(insertUserList)) {
                    logger.info("==============开始批量新增人员==================");
                    weChatCorpUserMapper.insertBatchCorpUser(insertUserList);
                    logger.info("==============批量新增人员结束==================");
                }
                //批量更新人员
                if (CollectionUtils.isNotEmpty(updateUserList)) {
                    logger.info("==============开始批量更新人员==================");
                    weChatCorpUserMapper.updateBatchByPrimaryKey(updateUserList);
                    logger.info("==============批量更新人员结束==================");
                }
                //批量新增人员部门关系
                if (CollectionUtils.isNotEmpty(insertUserDepartmentList)) {
                    logger.info("==============开始批量新增人员部门关系==================");
                    weChatUserDepartmentMapper.insertBatch(insertUserDepartmentList);
                    logger.info("==============批量新增人员部门关系结束==================");
                }
            }
        }
        //更新部门全路径
        weChatCorpDepartmentMapper.updateDepartmentFullPath(corpId);
        logger.info("==============开始异步更新人员详细信息==================");
        CompletableFuture.runAsync(() ->  executeDataModificationInNewThread(corpId,suiteId));
        return R.ok(null, "处理完成");
    }

    public void executeDataModificationInNewThread(String corpId,String suiteId) {
        logger.info("==============异步开始更新企业人员详情==================");
        WeChatCorpUser weChatCorpUserParam = new WeChatCorpUser();
        weChatCorpUserParam.setCorpId(corpId);
        weChatCorpUserParam.setSuiteId(suiteId);
        //获取企业访问配置
        R<String> corpAccessTokenR = wechatDataService.queryCorpAccessToken(corpId);
        if (corpAccessTokenR.getCode() != R.SUCCESS) {
            return;
        }
        List<WeChatCorpUser> weChatCorpUserList = weChatCorpUserMapper.selectAllUserByCorpId(weChatCorpUserParam);
        if(CollectionUtils.isNotEmpty(weChatCorpUserList)){
            for (WeChatCorpUser weChatCorpUser : weChatCorpUserList) {
                String userId = weChatCorpUser.getUserId();
                R<Map> personDetailR = wechatDataService.queryPersonnelDetails(userId, corpAccessTokenR.getData());
                if (personDetailR.getCode() == R.SUCCESS) {
                    Map userInfoMap = personDetailR.getData();
                    logger.error("===================获取人员详情数据成功=================\n" + JSON.toJSONString(userInfoMap));
                    String mainDepartment = MapUtils.getString(userInfoMap, "main_department");
                    Integer status = MapUtils.getInteger(userInfoMap, "status");
                    //主部门id
                    weChatCorpUser.setDepartmentId(mainDepartment);
                    weChatCorpUser.setStatus(status);
                    List<Integer> orderList = (List<Integer>) userInfoMap.get("order");
                    if (orderList != null && orderList.size() > 0) {
                        Integer order = orderList.get(0);
                        weChatCorpUser.setOrder(order);
                    }
                    //获取人员所属部门列表
                    List<Integer> departmentList = (List<Integer>) userInfoMap.get("department");
                    if (departmentList != null && departmentList.size() > 0) {
                        if (StringUtils.isEmpty(mainDepartment)) {
                            //默认第一个为主部门
                            weChatCorpUser.setDepartmentId(departmentList.get(0).toString());
                        }
                    }
                    String thumbAvatar = MapUtils.getString(userInfoMap, "thumb_avatar");
                    weChatCorpUser.setThumbAvatar(thumbAvatar);
                    weChatCorpUser.setCorpId(corpId);
                    weChatCorpUser.setSuiteId(suiteId);
                    weChatCorpUser.setUpdateDate(new Date());
                    weChatCorpUserMapper.updateByPrimaryKeySelective(weChatCorpUser);
                }

            }

        }
        logger.info("==============异步更新企业人员详情结束==================");
    }
    /**
     * 递归查询指定部门下人员数据
     * @param qywxUserDepartment
     * @return
     */
    @Override
    public R<List<String>> queryPersonnelUnderTheDepartment(WeChatUserDepartment weChatUserDepartment) {
        List<String> userIdList = weChatUserDepartmentMapper.queryPersonnelUnderTheDepartment(weChatUserDepartment);
        return R.ok(userIdList);
    }

    /**
     * 递归查询指定部门下人员数据-包含上级
     * @param qywxUserDepartment
     * @return
     */
  /*  @Override
    public R<List<String>> queryPersonnelUnderTheDepartmentParent(QywxUserDepartment qywxUserDepartment) {
        List<String> userIdList = weChatUserDepartmentMapper.queryPersonnelUnderTheDepartment(qywxUserDepartment);
        return R.ok(userIdList);
    }
*/
    @Override
    public R<List<WeChatHierarchicalReturnResult>> selectUserListByCorpId(WeChatUserDepartment qywxUserDepartment) {
        List<WeChatHierarchicalReturnResult> weChatHierarchicalReturnResults = weChatUserDepartmentMapper.selectUserListByCorpId(qywxUserDepartment);
        return R.ok(weChatHierarchicalReturnResults);
    }

    @Override
    public R<List<Map>> selectUserDepartmentListByCorpId(WeChatUserDepartment weChatUserDepartment) {
        List<Map> weChatHierarchicalReturnResults = weChatUserDepartmentMapper.selectUserDepartmentListByCorpId(weChatUserDepartment);
        return R.ok(weChatHierarchicalReturnResults);
    }
}
