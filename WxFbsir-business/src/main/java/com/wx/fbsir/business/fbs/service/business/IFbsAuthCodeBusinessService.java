package com.wx.fbsir.business.fbs.service.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.dto.business.auth_code.AuthCodeDetailResponse;
import com.wx.fbsir.business.fbs.dto.business.auth_code.AuthCodeGenerateRequest;
import com.wx.fbsir.business.fbs.dto.business.auth_code.AuthCodePageRequest;

import java.util.List;
import java.util.Map;

/**
 * 授权码运营BusinessService接口（薄封装：直接调用Mapper，不包装内部领域Service）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public interface IFbsAuthCodeBusinessService {

    /**
     * 授权码分页列表
     *
     * @param request 分页请求
     * @return 授权码列表
     */
    List<FbsAuthCode> getAuthCodePage(AuthCodePageRequest request);

    /**
     * 批量生成授权码
     *
     * @param request  生成请求
     * @param createdBy 创建人
     * @return 生成结果（ID→授权码的Map）
     */
    Map<Long, String> generateAuthCodeBatch(AuthCodeGenerateRequest request, String createdBy);

    /**
     * 禁用授权码（available: 1→0）
     * Fail-Closed：仅 available=1（启用）可禁用
     *
     * @param id 授权码ID
     * @return 是否成功
     */
    boolean disableAuthCode(Long id);

    /**
     * 启用授权码（available: 0→1）
     * Fail-Closed：仅 available=0 AND status IN(0,1) 可启用
     *
     * @param id 授权码ID
     * @return 是否成功
     */
    boolean enableAuthCode(Long id);

    /**
     * 撤销授权码（status: → 4）
     * Fail-Closed：仅 status != 4（未撤销）可撤销
     *
     * @param id 授权码ID
     * @return 是否成功
     */
    boolean revokeAuthCode(Long id);
}
