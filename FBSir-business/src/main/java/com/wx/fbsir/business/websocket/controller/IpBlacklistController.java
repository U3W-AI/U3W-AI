package com.wx.fbsir.business.websocket.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.business.websocket.domain.WsIpBlacklist;
import com.wx.fbsir.business.websocket.mapper.WsIpBlacklistMapper;
import com.wx.fbsir.common.core.page.TableDataInfo;

/**
 * IP黑名单Controller
 * 
 * @author FBSir
 * @date 2025-12-23
 */
@RestController
@RequestMapping("/business/host/blacklist")
public class IpBlacklistController extends BaseController
{
    @Autowired
    private WsIpBlacklistMapper wsIpBlacklistMapper;

    @PreAuthorize("@ss.hasPermi('business:host:blacklist:query')")
    @GetMapping("/list")
    public TableDataInfo list(WsIpBlacklist wsIpBlacklist)
    {
        startPage();
        List<WsIpBlacklist> list = wsIpBlacklistMapper.selectList(wsIpBlacklist);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('business:host:blacklist:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(wsIpBlacklistMapper.selectById(id));
    }

    @PreAuthorize("@ss.hasPermi('business:host:blacklist:add')")
    @Log(title = "IP黑名单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WsIpBlacklist wsIpBlacklist)
    {
        wsIpBlacklist.setCreateBy(getUsername());
        return toAjax(wsIpBlacklistMapper.insert(wsIpBlacklist));
    }

    @PreAuthorize("@ss.hasPermi('business:host:blacklist:edit')")
    @Log(title = "IP黑名单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WsIpBlacklist wsIpBlacklist)
    {
        wsIpBlacklist.setUpdateBy(getUsername());
        return toAjax(wsIpBlacklistMapper.update(wsIpBlacklist));
    }

    @PreAuthorize("@ss.hasPermi('business:host:blacklist:remove')")
    @Log(title = "IP黑名单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        if (ids == null || ids.length == 0) {
            return error("请选择要删除的数据");
        }
        
        int count = 0;
        for (Long id : ids) {
            count += wsIpBlacklistMapper.deleteById(id);
        }
        return count > 0 ? success("删除成功，共删除 " + count + " 条记录") : error("删除失败，未找到相关记录");
    }
}
