package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.RmUserExternalSyn;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RmUserExternalSynMapper {
    int deleteByPrimaryKey(String id);

    RmUserExternalSyn selectIsSynContractByUserId(RmUserExternalSyn rmUserExternalSyn);

    int insert(RmUserExternalSyn record);

    int insertSelective(RmUserExternalSyn record);

    RmUserExternalSyn selectByPrimaryKey(String id);

    int updateByPrimaryKeySelective(RmUserExternalSyn record);

    int updateByPrimaryKey(RmUserExternalSyn record);
}
