package com.wx.fbsir.business.fbs.service.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.dto.business.scene_pack.ScenePackCreateRequest;
import com.wx.fbsir.business.fbs.dto.business.scene_pack.ScenePackDetailResponse;
import com.wx.fbsir.business.fbs.dto.business.scene_pack.ScenePackPageRequest;
import com.wx.fbsir.business.fbs.dto.business.scene_pack.ScenePackUpdateRequest;

import java.util.List;

/**
 * 场景包运营BusinessService接口（薄封装：直接调用Mapper，不包装内部领域Service）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public interface IFbsScenePackBusinessService {

    /**
     * 场景包分页列表
     *
     * @param request 分页请求
     * @return 场景包列表
     */
    List<FbsScenePack> getScenePackPage(ScenePackPageRequest request);

    /**
     * 场景包详情
     *
     * @param id 场景包ID
     * @return 详情响应
     */
    ScenePackDetailResponse getScenePackDetail(Long id);

    /**
     * 创建场景包
     *
     * @param request 创建请求
     * @param createdBy 创建人
     * @return 场景包ID
     */
    Long createScenePack(ScenePackCreateRequest request, String createdBy);

    /**
     * 编辑场景包
     *
     * @param id       场景包ID
     * @param request  编辑请求
     * @param updatedBy 更新人
     * @return 是否成功
     */
    boolean updateScenePack(Long id, ScenePackUpdateRequest request, String updatedBy);

    /**
     * 发布场景包（草稿→已发布）
     * Fail-Closed：仅 status=0（草稿）可发布
     *
     * @param id        场景包ID
     * @param updatedBy 更新人
     * @return 是否成功
     */
    boolean publishScenePack(Long id, String updatedBy);

    /**
     * 下架场景包（已发布→已下架）
     * Fail-Closed：仅 status=1（已发布）可下架
     *
     * @param id        场景包ID
     * @param updatedBy 更新人
     * @return 是否成功
     */
    boolean unpublishScenePack(Long id, String updatedBy);
}
