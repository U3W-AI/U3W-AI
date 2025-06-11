package com.cube.wechat.thirdapp.service.impl;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.WeChatCorpUser;
import com.cube.wechat.thirdapp.entiy.WeChatCorpUserResult;
import com.cube.wechat.thirdapp.entiy.WeChatThirdCompany;
import com.cube.wechat.thirdapp.entiy.WeChatUserDepartment;
import com.cube.wechat.thirdapp.mapper.WeChatCorpUserMapper;
import com.cube.wechat.thirdapp.mapper.WeChatThirdCompanyMapper;
import com.cube.wechat.thirdapp.mapper.WeChatUserDepartmentMapper;
import com.cube.wechat.thirdapp.service.WeChatCorpUserService;
import com.cube.wechat.thirdapp.util.ZQUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author sjl
 * @Created date 2024/3/1 15:02
 */
@Service
@Slf4j
public class WeChatCorpUserServiceImpl implements WeChatCorpUserService {

    @Autowired
    private WeChatCorpUserMapper weChatCorpUserMapper;
    @Autowired
    private WeChatUserDepartmentMapper weChatUserDepartmentMapper;

    @Autowired
    private WeChatThirdCompanyMapper weChatThirdCompanyMapper;


    @Override
    public R<List<WeChatCorpUser>> saveCorpUser(List<WeChatCorpUser> weChatCorpUserList) {
        if (CollectionUtils.isNotEmpty(weChatCorpUserList)) {
            for (WeChatCorpUser weChatCorpUser : weChatCorpUserList) {
                WeChatCorpUser corpUser = weChatCorpUserMapper.selectCorpUserByOpenUserId(weChatCorpUser);
                if (ObjectUtils.isNotEmpty(corpUser)) {
                    weChatCorpUser.setId(corpUser.getId());
                    weChatCorpUser.setUpdateDate(new Date());
                    weChatCorpUserMapper.updateByPrimaryKeySelective(weChatCorpUser);
                } else {
                    //新增当前应用内id
                    weChatCorpUser.setId(UUID.randomUUID().toString());
                    weChatCorpUser.setCreateDate(new Date());
                    weChatCorpUserMapper.insertSelective(weChatCorpUser);
                }
                //清空用户所属部门
                WeChatUserDepartment weChatUserDepartment = new WeChatUserDepartment();
                weChatUserDepartment.setCorpId(weChatCorpUser.getCorpId());
                weChatUserDepartment.setSuiteId(weChatCorpUser.getSuiteId());
                weChatUserDepartment.setUserId(weChatCorpUser.getUserId());
                weChatUserDepartmentMapper.deleteUserDepartmentByUserId(weChatUserDepartment);
                //保存人员所属部门
                List<Integer> departmentIdList = weChatCorpUser.getDepartmentIdList();
                if (departmentIdList != null && departmentIdList.size() > 0) {
                    for (Integer departmmentId : departmentIdList) {
                        WeChatUserDepartment userDepartment = new WeChatUserDepartment();
                        userDepartment.setSystemUserId(weChatCorpUser.getId());
                        userDepartment.setDepartmentId(departmmentId.toString());
                        userDepartment.setUserId(weChatCorpUser.getUserId());
                        userDepartment.setCorpId(weChatCorpUser.getCorpId());
                        userDepartment.setSuiteId(weChatCorpUser.getSuiteId());
                        List<WeChatUserDepartment> qywxUserDepartmentList = weChatUserDepartmentMapper.selectUserDepartByUserId(userDepartment);
                        if (CollectionUtils.isEmpty(qywxUserDepartmentList)) {
                            weChatUserDepartmentMapper.insertSelective(userDepartment);
                        }
                    }
                }


            }
        }
        return R.ok(weChatCorpUserList);
    }

    @Override
    public R updateCorpUserStatus(WeChatCorpUser qywxCorpUser) {
        //将当前应用&当前企业下的所有用户，设置为禁用状态
        weChatCorpUserMapper.updateCorpUserStatus(qywxCorpUser);
        //删除用户对应部门数据
        WeChatUserDepartment weChatUserDepartment = new WeChatUserDepartment();
        weChatUserDepartment.setCorpId(qywxCorpUser.getCorpId());
        weChatUserDepartment.setSuiteId(qywxCorpUser.getSuiteId());
        weChatUserDepartmentMapper.deleteUserDepartment(weChatUserDepartment);
        return R.ok();
    }

    @Override
    public R selectCorpUserInfo(WeChatCorpUser corpUser) {
        try {
            WeChatCorpUserResult qywxCorpUserResult = new WeChatCorpUserResult();
            WeChatCorpUser qywxCorpUser = weChatCorpUserMapper.selectCorpUserInfo(corpUser);
            if (qywxCorpUser != null) {
                qywxCorpUserResult.setId(qywxCorpUser.getId());
                qywxCorpUserResult.setUserId(qywxCorpUser.getUserId());
                qywxCorpUserResult.setOpenUserid(qywxCorpUser.getOpenUserid());
                qywxCorpUserResult.setCorpId(qywxCorpUser.getCorpId());
                qywxCorpUserResult.setSuiteId(qywxCorpUser.getSuiteId());
                qywxCorpUserResult.setThumbAvatar(qywxCorpUser.getThumbAvatar());
                qywxCorpUserResult.setDepartmentId(qywxCorpUser.getDepartmentId());
                //查询用户所属部门信息
                String userId = qywxCorpUser.getUserId();
                String id = qywxCorpUser.getId();
                WeChatUserDepartment weChatUserDepartment = new WeChatUserDepartment();
                weChatUserDepartment.setUserId(qywxCorpUser.getOpenUserid());
                weChatUserDepartment.setSystemUserId(id);
                weChatUserDepartment.setCorpId(corpUser.getCorpId());
                weChatUserDepartment.setSuiteId(corpUser.getSuiteId());
                List<WeChatUserDepartment> userDepartmentList = weChatUserDepartmentMapper.selectUserDeptByParam(weChatUserDepartment);
                qywxCorpUserResult.setUserDepartmentList(userDepartmentList);
                return R.ok(qywxCorpUserResult, "查询成功");
            }
            return R.fail(-1, "未查询到有效用户");
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail(-2, "查询失败，发生异常");
        }

    }

    @Override
    public R updateCorpUserAvatar(WeChatCorpUser corpUser) {
        weChatCorpUserMapper.updateCorpUserAvatar(corpUser);
        return R.ok();
    }

    @Override
    public R deleteCorpUser(WeChatCorpUser weChatCorpUser) {
        try {
            weChatCorpUserMapper.deleteCorpUserByUserId(weChatCorpUser);
            //清空所属部门
            /*QywxUserDepartment qywxUserDepartment = new QywxUserDepartment();
            qywxUserDepartment.setCorpId(qywxCorpUser.getCorpId());
            qywxUserDepartment.setSuiteId(qywxCorpUser.getSuiteId());
            qywxUserDepartment.setUserId(qywxCorpUser.getOpenUserid());
            //删除 人员与部门 关联关系
            qywxUserDepartmentMapper.deleteUserDepartmentByUserId(qywxUserDepartment);*/
            //删除用户关联角色
            return R.ok();
        }catch (Exception e){
            e.printStackTrace();
            return R.fail("删除成员失败，发生异常");
        }
    }

    @Override
    public R<WeChatCorpUser> selectCorpUserId(WeChatCorpUser weChatCorpUser) {
        WeChatCorpUser weChatCorpUserEd = weChatCorpUserMapper.selectCorpUserId(weChatCorpUser);
        return R.ok(weChatCorpUserEd);
    }

    @Override
    public R<List<WeChatCorpUser>> selectAllUserByCorpId(WeChatCorpUser weChatCorpUser) {
        List<WeChatCorpUser> weChatCorpUserList = weChatCorpUserMapper.selectAllUserByCorpId(weChatCorpUser);
        return R.ok(weChatCorpUserList);
    }

    @Override
    public R<WeChatCorpUser> selectUserBasicInformation(WeChatCorpUser weChatCorpUser) {
        WeChatCorpUser weChatCorpUserResult= weChatCorpUserMapper.selectUserBasicInformationByUserId(weChatCorpUser);
        return R.ok(weChatCorpUserResult);
    }

    @Override
    public R selectUserNameByUserIds(Map map) {

        String suiteId = MapUtils.getString(map, "suiteId");
        //查询有效企业
        List<WeChatThirdCompany> companyList = weChatThirdCompanyMapper.selectAllCorp(suiteId);
        if (CollectionUtils.isNotEmpty(companyList)) {
            for (WeChatThirdCompany weChatThirdCompany : companyList) {
                String corpId = weChatThirdCompany.getCorpId();
                //查询该企业下 名字为空的 已激活人员
                List<WeChatCorpUser> weChatCorpUsers = weChatCorpUserMapper.selectUserNameIsNullByCorpId(corpId, suiteId);

                //对 open_userid 进行去重并收集成集合
                List<String> distinctOpenUserIds = weChatCorpUsers.stream()
                        .map(WeChatCorpUser::getOpenUserid).
                        distinct().
                        collect(Collectors.toList());

                if (CollectionUtils.isNotEmpty(distinctOpenUserIds)){
                    //拿到所有的用户信息
                    List<Map> userInfoMaps = ZQUtil.getNameByOpenUserIds(distinctOpenUserIds);
                    if (CollectionUtils.isNotEmpty(userInfoMaps)){
                        log.info("该企业:{}下有:{}条用户要从增强组件更新名称",corpId,userInfoMaps.size());
                        for (WeChatCorpUser weChatCorpUser : weChatCorpUsers) {
                            for (Map userInfoMap : userInfoMaps) {
                                if (weChatCorpUser.getOpenUserid().equals(MapUtils.getString(userInfoMap,"userId"))) {

                                    WeChatCorpUser updateWeChatCorpUser = new WeChatCorpUser();
                                    updateWeChatCorpUser.setUserName(MapUtils.getString(userInfoMap,"name"));
                                    updateWeChatCorpUser.setId(weChatCorpUser.getId());
                                    updateWeChatCorpUser.setUpdateDate(new Date());
                                    //更新用戶名称
                                    weChatCorpUserMapper.updateByPrimaryKeySelective(updateWeChatCorpUser);
                                }
                            }
                        }
                    }
                }
                log.info("=========该企业:{}从增强组件更新名称完成=============",corpId);
            }
        }
        return R.ok();
    }
}
