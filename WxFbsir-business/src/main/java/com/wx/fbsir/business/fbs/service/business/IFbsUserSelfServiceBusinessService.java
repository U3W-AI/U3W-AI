package com.wx.fbsir.business.fbs.service.business;

import com.wx.fbsir.business.fbs.dto.self.*;

import java.util.List;

/**
 * 用户自助服务BusinessService接口
 * <p>
 * 路径前缀：/my/*
 * </p>
 *
 * @author wxfbsir
 * @date 2026-04-10
 */
public interface IFbsUserSelfServiceBusinessService {

    /**
     * 查询当前用户的权益列表（分页，由 Controller startPage() 驱动）
     *
     * @param query 查询条件（含分页+过滤）
     * @return MyPackItemDTO 列表，total 由 PageHelper 注入到 PageInfo
     */
    List<MyPackItemDTO> getMyPacks(MyPacksQueryDTO query);

    /**
     * 激活授权码
     *
     * @param req 授权码激活请求
     * @return 激活响应
     */
    AuthCodeActivateResponseDTO activateAuthCode(AuthCodeActivateRequestDTO req);

    /**
     * 查询当前用户可领取的平台场景包列表（分页，由 Controller startPage() 驱动）
     *
     * @param query 查询条件（含分页+keyword搜索）
     * @return MyScenePackItemDTO 列表，total 由 PageHelper 注入到 PageInfo
     */
    List<MyScenePackItemDTO> getClaimableScenePacks(MyScenePacksQueryDTO query);

    /**
     * 领取场景包（平台分发）
     *
     * @param req 领取请求
     * @return 领取响应
     */
    ScenePackClaimResponseDTO claimScenePack(ScenePackClaimRequestDTO req);
}
