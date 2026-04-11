package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.business.fbs.dto.self.*;
import com.wx.fbsir.business.fbs.service.business.IFbsUserSelfServiceBusinessService;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户自助服务Controller
 * 路径前缀：/my/*
 *
 * @author wxfbsir
 * @date 2026-04-10
 */
@RestController
@RequestMapping("/my")
public class FbsUserSelfServiceController extends BaseController {

    @Autowired
    private IFbsUserSelfServiceBusinessService userSelfService;

    /**
     * GET /my/packs
     * 查询当前用户的权益列表（分页）
     */
    @GetMapping("/packs")
    public TableDataInfo getMyPacks(MyPacksQueryDTO query) {
        startPage();
        List<MyPackItemDTO> list = userSelfService.getMyPacks(query);
        return getDataTable(list);
    }

    /**
     * POST /my/auth-code/activate
     * 激活授权码
     */
    @PostMapping("/auth-code/activate")
    public AjaxResult activateAuthCode(@RequestBody AuthCodeActivateRequestDTO req) {
        AuthCodeActivateResponseDTO resp = userSelfService.activateAuthCode(req);
        return AjaxResult.success(resp);
    }

    /**
     * GET /my/scene-packs
     * 查询当前用户可领取的平台场景包列表（分页）
     */
    @GetMapping("/scene-packs")
    public TableDataInfo getClaimableScenePacks(MyScenePacksQueryDTO query) {
        startPage();
        List<MyScenePackItemDTO> list = userSelfService.getClaimableScenePacks(query);
        return getDataTable(list);
    }

    /**
     * POST /my/scene-pack/claim
     * 领取场景包（平台分发）
     */
    @PostMapping("/scene-pack/claim")
    public AjaxResult claimScenePack(@RequestBody ScenePackClaimRequestDTO req) {
        ScenePackClaimResponseDTO resp = userSelfService.claimScenePack(req);
        return AjaxResult.success(resp);
    }

    // ========== 6.5 统一异常处理 ==========

    /**
     * 统一异常处理——透传 ServiceException 的 code 和 message
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(ServiceException.class)
    public AjaxResult handleServiceException(ServiceException e) {
        Integer code = e.getCode();
        if (code != null) {
            return AjaxResult.error(code, e.getMessage());
        }
        return AjaxResult.error(e.getMessage());
    }

    /**
     * 其他运行时异常兜底
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(RuntimeException.class)
    public AjaxResult handleRuntimeException(RuntimeException e) {
        return AjaxResult.error(500, e.getMessage());
    }
}
