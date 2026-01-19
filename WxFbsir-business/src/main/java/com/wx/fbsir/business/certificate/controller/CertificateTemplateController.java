package com.wx.fbsir.business.certificate.controller;

import com.wx.fbsir.business.certificate.domain.CertificateTemplate;
import com.wx.fbsir.business.certificate.service.ICertificateTemplateService;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.common.utils.poi.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 证书模板Controller
 * 
 * @author wxfbsir
 */
@RestController
@RequestMapping("/business/certificateTemplate")
public class CertificateTemplateController extends BaseController
{
    @Autowired
    private ICertificateTemplateService certificateTemplateService;

    /**
     * 查询证书模板列表
     */
    @GetMapping("/list")
    public TableDataInfo list(CertificateTemplate certificateTemplate)
    {
        startPage();
        List<CertificateTemplate> list = certificateTemplateService.selectCertificateTemplateList(certificateTemplate);
        return getDataTable(list);
    }

    /**
     * 导出证书模板列表
     */
    @Log(title = "证书模板", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, CertificateTemplate certificateTemplate)
    {
        List<CertificateTemplate> list = certificateTemplateService.selectCertificateTemplateList(certificateTemplate);
        ExcelUtil<CertificateTemplate> util = new ExcelUtil<CertificateTemplate>(CertificateTemplate.class);
        util.exportExcel(response, list, "证书模板数据");
    }

    /**
     * 获取证书模板详细信息
     */
    @GetMapping(value = "/{templateId}")
    public AjaxResult getInfo(@PathVariable("templateId") Long templateId)
    {
        return AjaxResult.success(certificateTemplateService.selectCertificateTemplateById(templateId));
    }

    /**
     * 新增证书模板
     */
    @Log(title = "证书模板", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CertificateTemplate certificateTemplate)
    {
        return toAjax(certificateTemplateService.insertCertificateTemplate(certificateTemplate));
    }

    /**
     * 修改证书模板
     */
    @Log(title = "证书模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CertificateTemplate certificateTemplate)
    {
        return toAjax(certificateTemplateService.updateCertificateTemplate(certificateTemplate));
    }

    /**
     * 删除证书模板
     */
    @Log(title = "证书模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{templateIds}")
    public AjaxResult remove(@PathVariable Long[] templateIds)
    {
        return toAjax(certificateTemplateService.deleteCertificateTemplateByIds(templateIds));
    }
}