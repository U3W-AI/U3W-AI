package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.RmExternalInfoFieldConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author 张云龙
 */

@Mapper
public interface RmExternalInfoFieldConfigMapper {


    RmExternalInfoFieldConfig selectByPrimaryKey(String id);


    int deleteByPrimaryKey(String id);


    int insertSelective(RmExternalInfoFieldConfig rmExternalInfoFieldConfig);


    int updateByPrimaryKeySelective(RmExternalInfoFieldConfig record);

    /**
     * 初始化该企业居民信息字段配置
     * @param corpId
     */
    void initCorpExternalInfoFieldConfig(@Param("corpId")String corpId);

    RmExternalInfoFieldConfig selectByInfoNameAndCorpId(RmExternalInfoFieldConfig rmExternalInfoFieldConfig);

    String selectPhoneInfoByControName(@Param("corpId")String corpId,@Param("controlName")String controlName);
    List<RmExternalInfoFieldConfig> selectByCorpId(@Param("corpId")String corpId);

    List<RmExternalInfoFieldConfig> selectExternalInfoFileConfigByCorpId(@Param("corpId")String corpId);

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
    List<RmExternalInfoFieldConfig> selectDateControlByCorpId(@Param("corpId")String corpId);



}
