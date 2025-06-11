package com.cube.wechat.thirdapp.mapper;
import com.cube.wechat.thirdapp.entiy.WeChatThirdCompany;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface WeChatThirdCompanyMapper {
    int deleteByPrimaryKey(Integer id);

    int insertSelective(WeChatThirdCompany record);

    WeChatThirdCompany selectByPrimaryKey(Integer id);

    WeChatThirdCompany selectByCorpId(@Param("corpId")String corpId,@Param("suiteId")String suiteId);

    List<WeChatThirdCompany> selectAllCorp(@Param("suiteId")String suiteId);

    String selectCorpServerName(Map map);

    int updateByPrimaryKeySelective(WeChatThirdCompany record);

}
