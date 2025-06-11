package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.RmExternalStatisticsNum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RmExternalStatisticsNumMapper {

    int insertSelective(RmExternalStatisticsNum record);

    void deleteExternalStatisticsNumByUserId(@Param("userId") String userId, @Param("corpId") String corpId);

    void insertExternalStatisticsNumBatch(List<RmExternalStatisticsNum> list);

    List<String> selectFriendlyRelationshipPersonUserId(@Param("externalUserId")String  externalUserId, @Param("corpId")String corpId);

    RmExternalStatisticsNum selectExternalStatisticsNum(RmExternalStatisticsNum record);

    void updateExternalStatisticsNum(RmExternalStatisticsNum rmExternalStatisticsNum);

    List<Map> queryResidentDataOfPersonnelByUserIdList(Map map);


    List<Map> queryResidentListByUserIdList(Map map);

    /**
     * 查询全部无标签好友
     * @param map
     * @return
     */
    List<Map> queryResidentListNotLabelByUserIdList(Map map);

    List<Map> queryNewTodayResidentListByUserIdList(Map map);

    /**
     * 查询今日新增无标签好友
     * @param map
     * @return
     */
    List<Map> queryNewTodayResidentListNotLabelByUserIdList(Map map);

    List<Map> queryLossTodayResidentListByUserIdList(Map map);

    /**
     * 查询今日流失无标签好友
     * @param map
     * @return
     */
    List<Map> queryLossTodayResidentListNotLabelByUserIdList(Map map);

    List<Map> queryDeletedResidentListByUserIdList(Map map);

    /**
     * 查询被删除无标签好友
     * @param map
     * @return
     */
    List<Map> queryDeletedResidentListNotLabelByUserIdList(Map map);

    List<Map> queryMutualDeletionResidentListByUserIdList(Map map);

    /**
     * 双向删除无标签好友
     * @param map
     * @return
     */
    List<Map> queryMutualDeletionResidentListNotLabelByUserIdList(Map map);

    List<Map> queryLosingFriendlyRelationshipsByUserIdList(Map map);

    /**
     * 主动删除无标签好友
     * @param map
     * @return
     */
    List<Map> queryLosingFriendlyRelationshipsNotLabelByUserIdList(Map map);

    List<Map> queryThereIsAFriendRelationshipByUserIdList(Map map);

    /**
     * 正常联系无标签好友
     * @param map
     * @return
     */
    List<Map> queryThereIsAFriendRelationshipNotLabelByUserIdList(Map map);

    List<Map> queryUserResidentLabelAssociationDataByUserId(Map map);
    Integer selectExternalNumberOfTimesAdded(@Param("externalUserId")String externalUserId,@Param("corpId")String corpId);

    List<String> selectHowManyPeopleAddedItToByExternalUserId(@Param("externalUserId")String externalUserId,@Param("corpId")String corpId);
    List<RmExternalStatisticsNum> selectExternalUserIdAndCorpId(@Param("externalUserId")String externalUserId, @Param("corpId")String corpId);

    String selectFullPath(@Param("id") String id, @Param("corpId") String corpId);
    List<Map> selectqueryDataFromVariousOrganizations(Map map);
    Map selectDepartmentStaticsNumById(Map map);
    /**
     * 查询外部联系人的最新添加人
     */
    String selectExternalUserIdByExternalUserId(@Param("externalUserId")String externalUserId,@Param("corpId")String corpId);

    Map selectAllNum(@Param("corpId")String corpId,@Param("userIds")String userIds);

    Map selectAllNumByParentId(Map map);
    List<Map> selectCorpAllNum(@Param("corpId")String corpId,@Param("userIds")String userIds);

    Map selectExternalNumByCorpId(@Param("corpId")String corpId);

    List<Map> selectRangeDataStaticsDataNum(Map map);

    Map selectRangeDataAllById(Map map);

    //无标签好友今日新增
    Integer selectNotHaveLabelNewToadyNum(@Param("corpId")String corId, @Param("userId")String userId);
    //无标签好友今日流失

    Integer selectNotHaveLabelNewToadyLossNum(@Param("corpId")String corId, @Param("userId")String userId);

    List<RmExternalStatisticsNum> queryExternalByCorpIdAndExternalUserId(@Param("corpId") String corpId, @Param("externalUserId") String externalUserId);

    /**
     * 查询该企业下所有的外部联系人id
     */
    List<String> selectAllExternalUserIdByCorpId(@Param("corpId")String corpId);

    /**
     * 查询外部联系人的所有好友
     * @param corpId
     * @param externalUserId
     * @return
     */
    List<RmExternalStatisticsNum> selectExternalFriends(@Param("corpId") String corpId, @Param("externalUserId") String externalUserId);

    /**
     * 判断是否为好友关系
     */
    Integer selectExternalFriendsNum(@Param("corpId") String corpId, @Param("externalUserId") String externalUserId, @Param("userId") String userId);

    /**
     * 根据企业id查询未流失的好友总数
     * @param corpId
     * @return
     */
    Long selectAddExternalByCorpId(@Param("corpId") String corpId);

    /**
     * 根据企业id查询今日流失的好友总数
     */
    Long selectLossExternalByCorpId(@Param("corpId") String corpId);

    /**
     * 根据企业id查询今日新增的好友总数
     */
    Long selectNewExternalByCorpId(@Param("corpId") String corpId);

    /**
     * 根据企业id查询好友总数
     */
    Long selectAllExternalByCorpId(@Param("corpId") String corpId);

    List<Map> selectByCorpIdAndlimitNum(@Param("corpId") String corpId, @Param("limitNum") int limitNum);

    List<Map> selectByCorpIdAndUserIdListAndDays(@Param("corpId") String corpId,@Param("userIdList") List<String> userIdList, @Param("days") int days);

    List<Map> selectByCorpIdAndUserIdAndDays(@Param("corpId") String corpId, @Param("userId") String userId, @Param("days") int days,@Param("userName") String userName);

    long selectCountByCorpIdAndLabelList(@Param("corpId") String corpId, @Param("labelList") List<String> labelList);

    String selectPhoneByExternalUserId(@Param("externalUserId") String externalUserId, @Param("corpId") String corpId, @Param("userId") String userId);

    RmExternalStatisticsNum queryOneByExternalUserIdAndCorpId(@Param("externalUserId") String externalUserId, @Param("corpId") String corpId);

    List<Map> queryResidentListByUserIdListToQunFa(Map<String, String> paramMap);
}
