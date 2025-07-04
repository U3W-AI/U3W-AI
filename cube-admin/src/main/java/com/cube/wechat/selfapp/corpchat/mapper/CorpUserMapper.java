package com.cube.wechat.selfapp.corpchat.mapper;

import cn.felord.domain.contactbook.department.DeptInfo;
import com.cube.wechat.selfapp.corpchat.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CorpUserMapper {

    int deleteDept();
    int deleteUser();
    int deleteUserRole();
    int deleteUserPost();

    int saveDept(List<DeptInfo> list);

    int saveUser(List<UserInfo> list);

    int initRole();

    int initPost();
}
