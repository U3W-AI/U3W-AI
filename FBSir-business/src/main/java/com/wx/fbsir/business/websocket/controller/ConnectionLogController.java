package com.wx.fbsir.business.websocket.controller;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.business.websocket.domain.WsConnectionLog;
import com.wx.fbsir.business.websocket.mapper.WsConnectionLogMapper;
import com.wx.fbsir.common.core.page.TableDataInfo;

/**
 * WebSocket连接记录Controller
 * 
 * @author FBSir
 * @date 2025-12-23
 */
@RestController
@RequestMapping("/business/host/connection")
public class ConnectionLogController extends BaseController
{
    @Autowired
    private WsConnectionLogMapper wsConnectionLogMapper;

    @PreAuthorize("@ss.hasPermi('business:host:connection:query')")
    @GetMapping("/list")
    public TableDataInfo list(WsConnectionLog wsConnectionLog)
    {
        startPage();
        List<WsConnectionLog> list = wsConnectionLogMapper.selectList(wsConnectionLog);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('business:host:connection:query')")
    @GetMapping(value = "/{sessionId}")
    public AjaxResult getInfo(@PathVariable("sessionId") String sessionId)
    {
        return success(wsConnectionLogMapper.selectBySessionId(sessionId));
    }

    @PreAuthorize("@ss.hasPermi('business:host:connection:remove')")
    @Log(title = "WebSocket连接记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{sessionIds}")
    public AjaxResult remove(@PathVariable String[] sessionIds)
    {
        if (sessionIds == null || sessionIds.length == 0) {
            return error("请选择要删除的数据");
        }
        
        int count = 0;
        for (String sessionId : sessionIds) {
            WsConnectionLog log = wsConnectionLogMapper.selectBySessionId(sessionId);
            if (log != null) {
                log.setDelFlag(1);
                count += wsConnectionLogMapper.update(log);
            }
        }
        return count > 0 ? success("删除成功，共删除 " + count + " 条记录") : error("删除失败，未找到相关记录");
    }
}
