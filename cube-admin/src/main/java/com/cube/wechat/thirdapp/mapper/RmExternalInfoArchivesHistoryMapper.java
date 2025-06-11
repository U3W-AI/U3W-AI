package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.RmExternalInfoArchivesHistory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RmExternalInfoArchivesHistoryMapper {
    int deleteByPrimaryKey(String id);

    int insert(RmExternalInfoArchivesHistory record);

    int insertSelective(RmExternalInfoArchivesHistory record);

    RmExternalInfoArchivesHistory selectByPrimaryKey(String id);

    int updateByPrimaryKeySelective(RmExternalInfoArchivesHistory record);

    int updateByPrimaryKey(RmExternalInfoArchivesHistory record);

    List<RmExternalInfoArchivesHistory> selectArchivesHistoryByArchivesHistory(RmExternalInfoArchivesHistory rmExternalInfoArchivesHistory);
}
