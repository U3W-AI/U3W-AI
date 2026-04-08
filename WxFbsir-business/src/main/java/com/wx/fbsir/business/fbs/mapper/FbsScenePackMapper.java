package com.wx.fbsir.business.fbs.mapper;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 场景包Mapper接口
 *
 * TODO (OpenSpec #add-fbs-rights-foundation): 延期不做：
 *   - fbs_pack_version 版本历史管理（当前使用 currentVersion + contentSnapshot 代替）
 *     → 延期至 OpenSpec #2（平台侧运营）
 *
 * @author wxfbsir
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
