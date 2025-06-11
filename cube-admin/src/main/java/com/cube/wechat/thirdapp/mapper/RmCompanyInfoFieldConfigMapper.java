package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.RmCompanyInfoFieldConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author 张云龙
 */

@Mapper
public interface RmCompanyInfoFieldConfigMapper {


    RmCompanyInfoFieldConfig selectByPrimaryKey(String id);


    int deleteByPrimaryKey(String id);


    int insertSelective(RmCompanyInfoFieldConfig rmExternalInfoFieldConfig);


    int updateByPrimaryKeySelective(RmCompanyInfoFieldConfig record);

    /**
     * 初始化该企业居民信息字段配置
     * @param corpId
     */
    void initCorpExternalInfoFieldConfig(@Param("corpId")String corpId);

    RmCompanyInfoFieldConfig selectByInfoNameAndCorpId(RmCompanyInfoFieldConfig rmExternalInfoFieldConfig);

    String selectPhoneInfoByControName(@Param("corpId")String corpId,@Param("controlName")String controlName);
    List<RmCompanyInfoFieldConfig> selectByCorpId(@Param("corpId")String corpId);

    List<RmCompanyInfoFieldConfig> selectExternalInfoFileConfigByCorpId(@Param("corpId")String corpId);

    //查询是否已存在
    Integer selectIsUserFileConfigByCorpId(@Param("corpId")String corpId);

    /**
     * 查询企业信息字段长度
     * @param corpId
     * @return
     */
    int selectCorpExternalInfoFieldConfigCount(@Param("corpId")String corpId);

    /**
     * 查询企业下开启的日期组件列表
     */
    List<RmCompanyInfoFieldConfig> selectDateControlByCorpId(@Param("corpId")String corpId);

    /**
     * 获取企业信息字段为企业名称和企业简称的配置信息
     */
    List<RmCompanyInfoFieldConfig> selectInfoNameAndShortNameByCorpId(@Param("corpId")String corpId);


    List<RmCompanyInfoFieldConfig> selectComPanyInfoByCorpId(@Param("corpId")String corpId);
}
