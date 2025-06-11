package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.RmExternalInfoArchives;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RmExternalInfoArchivesMapper {
    int deleteByPrimaryKey(String id);

    int insert(RmExternalInfoArchives record);

    int insertSelective(RmExternalInfoArchives record);

    RmExternalInfoArchives selectByPrimaryKey(String id);

    int updateByPrimaryKeySelective(RmExternalInfoArchives record);

    int updateByPrimaryKey(RmExternalInfoArchives record);

    List<RmExternalInfoArchives> selectExternalInfoArchivesByExternalInfoArchives(RmExternalInfoArchives record);

    Map selectExternalInfoArchivesByInfo(Map map);

    /**
     * 查询好友总数及档案总数
     * @param map
     * @return
     */
    List<Map<String, Object>> selectTheTotalNumberOfFriendsAndProfilesByUserIds(Map map);

    /**
     * 查询标签完善人数  满足任意/全部满足
     */
    Map selectLabelPerfectNumber(Map map);

    /**
     * 无标签人数
     */
    Map selectNothingLabelPerfectNumber(Map map);

    /**
     * 全部绑定标签人数
     */

    Map selectAllLabelPerfectNumber(Map map);

    /**
     * 查询档案完善人数 满足任意/全部满足
     */
    Map selectExternalArchivesNumber(Map map);

    /**
     * 全部已完善档案人数
     */
    Map selectAllExternalArchivesNumber(Map map);

    /**
     * 未完善档案人数
     */
    Map selectNothingExternalArchivesNumber(Map map);

    List<RmExternalInfoArchives> selectByInfoIdAndInfoValue(@Param("infoId") String infoId, @Param("corpId") String corpId);

    List<Map> selectFilterStatisticsResidentList(Map map);

    List<RmExternalInfoArchives> selectByInfoValueAndCorpId(@Param("corpId") String corpId, @Param("infoValue") String infoValue);



    int updateInfoValueById(@Param("id") String id,
                            @Param("oldCorpName") String oldCorpName,
                            @Param("newCorpName") String newCorpName,
                            @Param("userId") String userId);

    void deleteExternalArchivesByInfoId(Map map);

    List<Map> selectArchiveByCorpId(@Param("corpId") String corpId);

    List<RmExternalInfoArchives> selectArchiveByExternalId(@Param("corpId")String corpId,@Param("externalUserId")String externalUserId);
}
