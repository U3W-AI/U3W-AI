package com.cube.wechat.selfapp.officeaccount.mapper;


import com.alibaba.fastjson.JSONObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface OfficeAccountMapper {

    int saveOfficeAccount(@Param("list") List<JSONObject> list);

    List<Map> getOpenIdList();

    int updateOfficeAccount(@Param("list") List<JSONObject> list);
    int updateOfficeAccountTwo(@Param("list") List<JSONObject> list);
}
