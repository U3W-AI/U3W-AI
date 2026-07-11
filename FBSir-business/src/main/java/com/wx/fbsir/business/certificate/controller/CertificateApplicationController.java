package com.wx.fbsir.business.certificate.controller;

import com.wx.fbsir.business.certificate.domain.CertificateApplication;
import com.wx.fbsir.business.certificate.domain.CertificateApplicationManagementVO;
import com.wx.fbsir.business.certificate.service.ICertificateApplicationService;
import com.wx.fbsir.business.certificate.service.impl.CertificateApplicationCoreService;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.common.utils.SecurityUtils;
import com.wx.fbsir.common.utils.poi.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证申请Controller
 * 
 * @author FBSir
 */
@RestController
@RequestMapping("/business/certificateApplication")
public class CertificateApplicationController extends BaseController
{
    @Autowired
    private ICertificateApplicationService certificateApplicationService;

    @Autowired
    private CertificateApplicationCoreService certificateApplicationCoreService;

    /**
     * 查询认证申请列表
     */
    @GetMapping("/list")
    public TableDataInfo list(CertificateApplication certificateApplication)
    {
        startPage();
        List<CertificateApplication> list = certificateApplicationService.selectCertificateApplicationList(certificateApplication);
        return getDataTable(list);
    }

    /**
     * 导出认证申请列表
     */
    @Log(title = "认证申请", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, CertificateApplication certificateApplication)
    {
        List<CertificateApplication> list = certificateApplicationService.selectCertificateApplicationList(certificateApplication);
        ExcelUtil<CertificateApplication> util = new ExcelUtil<CertificateApplication>(CertificateApplication.class);
        util.exportExcel(response, list, "认证申请数据");
    }

    /**
     * 获取当前用户的认证申请列表
     */
    @GetMapping("/my")
    public TableDataInfo getMyApplications(CertificateApplication certificateApplication)
    {
        // 设置当前用户ID，确保只查询当前用户的申请
        certificateApplication.setUserId(SecurityUtils.getUserId());
        startPage();
        List<CertificateApplication> list = certificateApplicationService.selectCertificateApplicationList(certificateApplication);
        return getDataTable(list);
    }

    /**
     * 获取认证申请详细信息
     */
    @GetMapping(value = "/{applicationId}")
    public AjaxResult getInfo(@PathVariable("applicationId") Long applicationId)
    {
        return AjaxResult.success(certificateApplicationService.selectCertificateApplicationById(applicationId));
    }

    /**
     * 新增认证申请
     */
    @Log(title = "认证申请", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CertificateApplication certificateApplication)
    {
        return toAjax(certificateApplicationService.insertCertificateApplication(certificateApplication));
    }

    /**
     * 提交认证申请（带积分扣减）
     */
    @Log(title = "提交认证申请", businessType = BusinessType.INSERT)
    @PostMapping("/submit")
    public AjaxResult submitApplication(@RequestBody CertificateApplication certificateApplication)
    {
        Long userId = SecurityUtils.getUserId();
        return AjaxResult.success(certificateApplicationCoreService.submitApplication(certificateApplication));
    }

    /**
     * 修改认证申请
     */
    @Log(title = "认证申请", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CertificateApplication certificateApplication)
    {
        return toAjax(certificateApplicationService.updateCertificateApplication(certificateApplication));
    }

    /**
     * 删除认证申请
     */
    @Log(title = "认证申请", businessType = BusinessType.DELETE)
    @DeleteMapping("/{applicationIds}")
    public AjaxResult remove(@PathVariable Long[] applicationIds)
    {
        return toAjax(certificateApplicationService.deleteCertificateApplicationByIds(applicationIds));
    }

    /**
     * 领取证书
     */
    @Log(title = "领取证书", businessType = BusinessType.UPDATE)
    @PostMapping("/receive/{applicationId}")
    public AjaxResult receiveCertificate(@PathVariable Long applicationId)
    {
        Long userId = SecurityUtils.getUserId();
        boolean result = certificateApplicationService.receiveCertificate(applicationId, userId);
        return result ? AjaxResult.success("领取成功") : AjaxResult.error("领取失败");
    }

    /**
     * 查询证书发放列表（基于申请表）
     */
    @GetMapping("/issuance/list")
    public TableDataInfo listIssuedCertificates(CertificateApplication certificateApplication)
    {
        startPage();
        List<CertificateApplicationManagementVO> list = certificateApplicationService.selectCertificateIssuanceList(certificateApplication);
        return getDataTable(list);
    }

    /**
     * 查询证书发放详情（基于申请表）
     */
    @GetMapping(value = "/issuance/{applicationId}")
    public AjaxResult getIssuanceInfo(@PathVariable("applicationId") Long applicationId)
    {
        return AjaxResult.success(certificateApplicationService.selectCertificateIssuanceById(applicationId));
    }
}