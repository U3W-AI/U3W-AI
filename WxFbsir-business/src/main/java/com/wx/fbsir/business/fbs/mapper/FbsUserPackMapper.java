package com.wx.fbsir.business.fbs.mapper;

import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户场景包关系Mapper接口
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@Mapper
public interface FbsUserPackMapper {

    /**
     * 查询用户对指定场景包的有效权益（status=1 且未过期）
     *
     * @param userId 用户ID
     * @param packId 场景包ID
     * @return 用户权益记录（NULL=无权益）
     */
    FbsUserPack selectActiveByUserIdAndPackId(@Param("userId") Long userId,
                                              @Param("packId") Long packId);

    /**
     * 查询用户的所有有效权益
     *
     * @param userId 用户ID
     * @return 有效权益列表
     */
    List<FbsUserPack> selectActiveByUserId(@Param("userId") Long userId);

    /**
     * 新增用户场景包权益记录
     *
     * @param fbsUserPack 用户权益
     * @return 影响行数
     */
    int insertUserPack(FbsUserPack fbsUserPack);
}
