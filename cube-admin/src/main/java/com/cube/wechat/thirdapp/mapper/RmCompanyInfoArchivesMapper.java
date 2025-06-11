package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.RmCompanyInfoArchives;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RmCompanyInfoArchivesMapper {
    /**
     * 按字段新增
     * @param record
     * @return
     */
    int insertSelective(RmCompanyInfoArchives record);

    /**
     * 按infovalue查询，判断是否存在
     */
    List<RmCompanyInfoArchives> selectByInfoId(@Param("infoValue") String infoValue,@Param("corpId") String corpId,@Param("infoId") String infoId);
    RmCompanyInfoArchives selectByPrimaryKey(@Param("id") String id);


    int updateByPrimaryKeySelective(RmCompanyInfoArchives record);
    int updateByPrimaryKeySelectiveAndCropId(RmCompanyInfoArchives record);


    /**
     * 如果以字母开头，则按英文字母的首字母排序；如果以汉字开头，则按照汉字的拼音首字母排序。
     */
    List<RmCompanyInfoArchives> selectCompanyIdsAndCorpId(@Param("list") List<String> companyIds, @Param("corpId") String corpId);

    /**
     *
     *根据infoValue迷糊搜索
     */
    List<String> selectByInfoValue(@Param("name") String name, @Param("corpId") String corpId);

    int deleteByPrimaryKey(String id);

    int deleteByCompanyIdAndCropId(@Param("companyId") String companyId, @Param("corpId") String corpId);

    //根据infoId删除信息
    int deleteByInfoId(@Param("infoId") String infoId);

    void saveBatch(@Param("archives") List<RmCompanyInfoArchives> archives);
}
