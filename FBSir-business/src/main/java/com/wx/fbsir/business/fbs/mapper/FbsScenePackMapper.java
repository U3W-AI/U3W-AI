package com.wx.fbsir.business.fbs.mapper;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 场景包Mapper接口
 *
 * TODO (OpenSpec #add-fbs-rights-foundation): 延期不做：
 *   - fbs_pack_version 版本历史管理（当前使用 currentVersion + contentSnapshot 代替）
 *     → 延期至 OpenSpec #2（平台侧运营）
 *
 * @author FBSir
 * @date 2026-04-08
 */
@Mapper
public interface FbsScenePackMapper {

    /**
     * 根据ID查询场景包
     *
     * @param id 主键
     * @return 场景包
     */
    FbsScenePack selectById(@Param("id") Long id);

    /**
     * 根据场景包编码查询
     *
     * @param packCode 场景包编码（业务唯一键）
     * @return 场景包
     */
    FbsScenePack selectByPackCode(@Param("packCode") String packCode);

    /**
     * 列表查询（支持按status/packType筛选，Mapper不带Page参数）
     * 分页由Controller层startPage()触发，PageHelper通过ThreadLocal拦截SQL
     *
     * @param filter 过滤条件（status/packType等）
     * @return 场景包列表
     */
    List<FbsScenePack> selectScenePackList(@Param("filter") FbsScenePack filter);

    /**
     * 可领取平台场景包查询（用户侧）
     * <p>
     * 条件：owner_type=1, status=1, visible_scope='ALL', points_rule_code IS NULL,
     * 用户未拥有（NOT EXISTS），keyword 模糊搜索。
     * </p>
     * <p>MVP 说明：end_time 暂以 status=1（已发布）代替。</p>
     *
     * @param userId  当前用户ID
     * @param keyword 搜索关键字（packName/packCode 模糊，可为 null）
     * @return 可领取场景包列表
     */
    List<FbsScenePack> selectClaimablePacks(@Param("userId") Long userId,
                                             @Param("keyword") String keyword);

    /**
     * 新增场景包
     *
     * @param fbsScenePack 场景包
     * @return 影响行数
     */
    int insertScenePack(FbsScenePack fbsScenePack);

    /**
     * 修改场景包
     *
     * @param fbsScenePack 场景包
     * @return 影响行数
     */
    int updateScenePack(FbsScenePack fbsScenePack);
}
