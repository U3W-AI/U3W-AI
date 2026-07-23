package com.wx.fbsir.business.point.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 积分Mapper接口
 * 
 * @author FBSir
 * @date 2025-12-08
 */
@Mapper
public interface PointsMapper {
    /**
     * 获取用户积分余额
     * 
     * @param userId 用户ID
     * @return 积分余额
     */
    public Integer getUserPoints(@Param("userId") Long userId);

    /**
     * 更新用户积分余额
     * 
     * @param userId 用户ID
     * @param points 积分余额
     * @return 结果
     */
    public int updateUserPoints(@Param("userId") Long userId, @Param("points") Integer points);

    /**
     * Updates a balance only when it still equals the value read by the caller.
     *
     * @param userId user ID
     * @param expectedPoints balance observed by the caller
     * @param points replacement balance
     * @return affected rows; zero means a concurrent balance change won
     */
    public int updateUserPointsIfBalance(@Param("userId") Long userId,
                                         @Param("expectedPoints") Integer expectedPoints,
                                         @Param("points") Integer points);
}

