package com.cube.wechat.selfapp.app.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2025年07月11日 09:52
 */
@Mapper
public interface SysHostWhitelistMapper {
    int selectActiveByHostId(@Param("hostId") String hostId);
}
