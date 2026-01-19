package com.wx.fbsir.business.certificate.controller;

import com.wx.fbsir.business.certificate.domain.ApplicationReview;
import com.wx.fbsir.business.certificate.domain.vo.ApplicationReviewVo;
import com.wx.fbsir.business.certificate.service.IApplicationReviewService;
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
 * 申请审核记录Controller
 * 
 * @author wxfbsir
 */
@RestController
@RequestMapping("/business/applicationReview")
public class ApplicationReviewController extends BaseController
{
    @Autowired
    private IApplicationReviewService applicationReviewService;

    /**
     * 查询申请审核记录列表
     */
    @GetMapping("/list")
    public TableDataInfo list(ApplicationReview applicationReview)
    {
        startPage();
        List<ApplicationReview> list = applicationReviewService.selectApplicationReviewList(applicationReview);
        return getDataTable(list);
    }
    
    /**
     * 查询申请审核列表（带详细信息）
     */
    @GetMapping("/listWithDetails")
    public TableDataInfo listWithDetails(ApplicationReview applicationReview)
    {
        startPage();
        List<ApplicationReviewVo> list = applicationReviewService.selectApplicationReviewListWithDetails(applicationReview);
        return getDataTable(list);
    }

    /**
     * 导出申请审核记录列表
     */
    @Log(title = "申请审核记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ApplicationReview applicationReview)
    {
        List<ApplicationReview> list = applicationReviewService.selectApplicationReviewList(applicationReview);
        ExcelUtil<ApplicationReview> util = new ExcelUtil<ApplicationReview>(ApplicationReview.class);
        util.exportExcel(response, list, "申请审核记录数据");
    }

    /**
     * 获取申请审核记录详细信息（通过审核ID）
     */
    @GetMapping(value = "/info/{reviewId}")
    public AjaxResult getInfo(@PathVariable("reviewId") Long reviewId)
    {
        // 使用关联查询获取完整的审核记录信息
        ApplicationReview applicationReview = new ApplicationReview();
        applicationReview.setReviewId(reviewId);
        List<ApplicationReviewVo> list = applicationReviewService.selectApplicationReviewListWithDetails(applicationReview);
        if (list != null && !list.isEmpty()) {
            return AjaxResult.success(list.get(0));
        }
        return AjaxResult.success();
    }
    
    /**
     * 获取申请详情（通过申请ID）- 路径参数版本
     */
    @GetMapping(value = "/getApplication/{applicationId}")
    public AjaxResult getApplicationInfo(@PathVariable("applicationId") Long applicationId)
    {
        if (applicationId == null) {
            return AjaxResult.error("申请ID不能为空");
        }
        // 使用关联查询获取完整的申请信息
        ApplicationReview applicationReview = new ApplicationReview();
        applicationReview.setApplicationId(applicationId);
        List<ApplicationReviewVo> list = applicationReviewService.selectApplicationReviewListWithDetails(applicationReview);
        if (list != null && !list.isEmpty()) {
            return AjaxResult.success(list.get(0));
        }
        return AjaxResult.error("未找到申请信息");
    }
    
    /**
     * 获取申请详情（通过申请ID）- 查询参数版本
     */
    @GetMapping(value = "/getApplication")
    public AjaxResult getApplicationInfoByParam(Long applicationId)
    {
        if (applicationId == null) {
            return AjaxResult.error("申请ID不能为空");
        }
        // 使用关联查询获取完整的申请信息
        ApplicationReview applicationReview = new ApplicationReview();
        applicationReview.setApplicationId(applicationId);
        List<ApplicationReviewVo> list = applicationReviewService.selectApplicationReviewListWithDetails(applicationReview);
        if (list != null && !list.isEmpty()) {
            return AjaxResult.success(list.get(0));
        }
        return AjaxResult.error("未找到申请信息");
    }

    /**
     * 新增申请审核记录
     */
    @Log(title = "申请审核记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ApplicationReview applicationReview)
    {
        return toAjax(applicationReviewService.insertApplicationReview(applicationReview));
    }

    /**
     * 修改申请审核记录
     */
    @Log(title = "申请审核记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ApplicationReview applicationReview)
    {
        return toAjax(applicationReviewService.updateApplicationReview(applicationReview));
    }
    
    /**
     * 审核认证申请
     */
    @Log(title = "审核认证申请", businessType = BusinessType.UPDATE)
    @PutMapping("/reviewApplication")
    public AjaxResult reviewApplication(@RequestBody ApplicationReview applicationReview)
    {
        int result = applicationReviewService.reviewApplication(applicationReview);
        return toAjax(result > 0 ? 1 : 0);
    }

    /**
     * 删除申请审核记录
     */
    @Log(title = "申请审核记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{reviewIds}")
    public AjaxResult remove(@PathVariable Long[] reviewIds)
    {
        return toAjax(applicationReviewService.deleteApplicationReviewByIds(reviewIds));
    }

}