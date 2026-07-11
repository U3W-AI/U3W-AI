package com.wx.fbsir.business.fbs.mapper;

import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 用户场景包关系Mapper接口
 *
 * @author FBSir
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
     * 列表查询（支持按userId/status筛选，Mapper不带Page参数）
     * JOIN fbs_scene_pack 获取 packName
     *
     * @param filter 过滤条件（userId/status）
     * @return 用户权益列表（含packName）
     */
    List<FbsUserPack> selectUserPackList(@Param("filter") FbsUserPack filter);

    /**
     * 用户自助权益查询（强制 userId，分页+过滤）
     * JOIN fbs_scene_pack 获取 packName/packCode
     *
     * @param filter 过滤条件（userId必填，status/sourceType/packId可选）
     * @return 用户权益列表（含packName/packCode）
     */
    List<FbsUserPack> selectMyPacks(FbsUserPack filter);

    /**
     * 幂等检查：用户对指定包是否有非撤销权益（status!=3）
     *
     * @param userId 用户ID
     * @param packId 场景包ID
     * @return 存在则返回1，否则返回0
     */
    int selectExistsByUserIdAndPackId(@Param("userId") Long userId, @Param("packId") Long packId);

    /**
     * 批量查询用户已领取的场景包ID集合（status!=3 且未删除）
     *
     * @param userId  用户ID
     * @param packIds 场景包ID集合
     * @return 已领取的包ID集合
     */
    Set<Long> selectClaimedPackIds(@Param("userId") Long userId, @Param("packIds") Set<Long> packIds);

    /**
     * 按用户ID和状态统计数量
     *
     * @param userId 用户ID（可为null表示统计所有）
     * @param status 权益状态（1=有效, 2=已过期, 3=已撤销）
     * @return 数量
     */
    Long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * 新增用户场景包权益记录
     *
     * @param fbsUserPack 用户权益
     * @return 影响行数
     */
    int insertUserPack(FbsUserPack fbsUserPack);
}
