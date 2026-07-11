package com.wx.fbsir.business.officialaccount.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;

import com.wx.fbsir.business.dailyassistant.domain.DailyArticle;
import com.wx.fbsir.business.dailyassistant.service.IDailyArticleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.business.officialaccount.domain.WcOfficePublishRecord;
import com.wx.fbsir.business.officialaccount.service.IWcOfficePublishRecordService;
import com.wx.fbsir.common.utils.SecurityUtils;
import com.wx.fbsir.common.utils.poi.ExcelUtil;
import com.wx.fbsir.common.core.page.TableDataInfo;

/**
 * 公众号文章发布记录Controller
 * 
 * @author FBSir
 * @date 2025-12-09
 */
@RestController
@RequestMapping("/business/publish-record")
public class WcOfficePublishRecordController extends BaseController
{
    @Autowired
    private IWcOfficePublishRecordService wcOfficePublishRecordService;

    @Autowired
    private IDailyArticleService dailyArticleService;

    /**
     * 查询公众号文章发布记录列表（需要查询权限）
     * 
     * 说明：通过菜单权限控制按钮显示，后端通过userId过滤确保数据安全
     */
    @PreAuthorize("@ss.hasPermi('business:publish:list')")
    @GetMapping("/list")
    public TableDataInfo list(WcOfficePublishRecord wcOfficePublishRecord)
    {
        // 强制设置为当前用户ID，确保只能查询自己的记录
        wcOfficePublishRecord.setUserId(SecurityUtils.getUserId());
        startPage();
        List<WcOfficePublishRecord> list = wcOfficePublishRecordService.selectWcOfficePublishRecordList(wcOfficePublishRecord);
        return getDataTable(list);
    }

    /**
     * 导出公众号文章发布记录列表（需要查询权限）
     */
    @PreAuthorize("@ss.hasPermi('business:publish:list')")
    @Log(title = "公众号文章发布记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WcOfficePublishRecord wcOfficePublishRecord)
    {
        // 强制设置为当前用户ID，确保只能导出自己的记录
        wcOfficePublishRecord.setUserId(SecurityUtils.getUserId());
        List<WcOfficePublishRecord> list = wcOfficePublishRecordService.selectWcOfficePublishRecordList(wcOfficePublishRecord);
        ExcelUtil<WcOfficePublishRecord> util = new ExcelUtil<WcOfficePublishRecord>(WcOfficePublishRecord.class);
        util.exportExcel(response, list, "公众号文章发布记录数据");
    }

    /**
     * 获取公众号文章发布记录详细信息（包含关联的文章内容，需要查询权限）
     * 
     * 说明：通过菜单权限控制按钮显示，后端验证记录归属防止越权
     */
    @PreAuthorize("@ss.hasPermi('business:publish:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        WcOfficePublishRecord record = wcOfficePublishRecordService.selectWcOfficePublishRecordById(id);
        
        // 验证记录是否属于当前用户
        if (record != null && !record.getUserId().equals(SecurityUtils.getUserId())) {
            return error("无权访问该记录");
        }
        
        // 获取关联的文章内容
        if (record != null && record.getArticleId() != null) {
            DailyArticle article = dailyArticleService.selectDailyArticleById(record.getArticleId());
            AjaxResult ajax = AjaxResult.success(record);
            ajax.put("article", article);
            return ajax;
        }
        
        return success(record);
    }

    /**
     * 删除公众号文章发布记录（需要删除权限）
     * 
     * 说明：通过菜单权限控制按钮显示，后端验证记录归属防止越权删除
     */
    @PreAuthorize("@ss.hasPermi('business:publish:remove')")
    @Log(title = "公众号文章发布记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        // 验证所有记录都属于当前用户
        Long currentUserId = SecurityUtils.getUserId();
        for (Long id : ids) {
            WcOfficePublishRecord record = wcOfficePublishRecordService.selectWcOfficePublishRecordById(id);
            if (record != null && !record.getUserId().equals(currentUserId)) {
                return error("无权删除该记录");
            }
        }
        
        return toAjax(wcOfficePublishRecordService.deleteWcOfficePublishRecordByIds(ids));
    }
}
