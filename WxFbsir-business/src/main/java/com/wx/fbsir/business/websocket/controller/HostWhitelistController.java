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
import com.wx.fbsir.business.websocket.domain.WsHostWhitelist;
import com.wx.fbsir.business.websocket.mapper.WsHostWhitelistMapper;
import com.wx.fbsir.common.core.page.TableDataInfo;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 主机ID白名单Controller
 * 
 * @author wxfbsir
 * @date 2025-12-23
 */
@RestController
@RequestMapping("/business/host/whitelist")
public class HostWhitelistController extends BaseController
{
    @Autowired
    private WsHostWhitelistMapper wsHostWhitelistMapper;
    
    @Autowired
    private RestTemplate restTemplate;

    @PreAuthorize("@ss.hasPermi('business:host:whitelist:query')")
    @GetMapping("/list")
    public TableDataInfo list(WsHostWhitelist wsHostWhitelist)
    {
        startPage();
        List<WsHostWhitelist> list = wsHostWhitelistMapper.selectList(wsHostWhitelist);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('business:host:whitelist:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(wsHostWhitelistMapper.selectById(id));
    }

    @PreAuthorize("@ss.hasPermi('business:host:whitelist:add')")
    @Log(title = "主机ID白名单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WsHostWhitelist wsHostWhitelist)
    {
        // 检查是否已存在相同的主机ID
        WsHostWhitelist existing = wsHostWhitelistMapper.selectByHostId(wsHostWhitelist.getHostId());
        if (existing != null) {
            return error("主机ID '" + wsHostWhitelist.getHostId() + "' 已存在，请使用其他主机ID");
        }
        
        wsHostWhitelist.setCreateBy(getUsername());
        return toAjax(wsHostWhitelistMapper.insert(wsHostWhitelist));
    }

    @PreAuthorize("@ss.hasPermi('business:host:whitelist:edit')")
    @Log(title = "主机ID白名单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WsHostWhitelist wsHostWhitelist)
    {
        wsHostWhitelist.setUpdateBy(getUsername());
        return toAjax(wsHostWhitelistMapper.update(wsHostWhitelist));
    }

    @PreAuthorize("@ss.hasPermi('business:host:whitelist:remove')")
    @Log(title = "主机ID白名单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        if (ids == null || ids.length == 0) {
            return error("请选择要删除的数据");
        }
        
        int count = wsHostWhitelistMapper.deleteByIds(ids);
        return count > 0 ? success("删除成功，共删除 " + count + " 条记录") : error("删除失败，未找到相关记录");
    }

    /**
     * 手动触发指定主机的健康检查
     *
     * @param id 主机ID
     * @return 健康检查结果
     */
    @PreAuthorize("@ss.hasPermi('business:host:whitelist:edit')")
    @Log(title = "主机ID白名单", businessType = BusinessType.OTHER)
    @GetMapping("/health-check/{id}")
    public AjaxResult manualHealthCheck(@PathVariable("id") Long id)
    {
        try {
            WsHostWhitelist host = wsHostWhitelistMapper.selectById(id);
            if (host == null) {
                return error("未找到指定主机");
            }

            if (!"openclaw".equals(host.getHostType())) {
                return error("只有OpenClaw类型的主机支持健康检查");
            }

            String healthCheckUrl = host.getHealthCheckUrl();
            if (healthCheckUrl == null || healthCheckUrl.isEmpty()) {
                return error("该主机未配置健康检查URL");
            }

            // 设置5秒超时
            restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                    super.prepareConnection(connection, httpMethod);
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                }
            });

            // 发送健康检查请求
            ResponseEntity<String> response = restTemplate.exchange(
                    healthCheckUrl,
                    HttpMethod.GET,
                    null,
                    String.class
            );

            // 更新主机状态
            String newStatus = (response.getStatusCode().is2xxSuccessful()) ? "online" : "offline";
            host.setOnlineStatus(newStatus);
            wsHostWhitelistMapper.update(host);

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("hostId", host.getHostId());
            resultMap.put("hostName", host.getHostName());
            resultMap.put("healthCheckUrl", healthCheckUrl);
            resultMap.put("responseStatus", response.getStatusCodeValue());
            resultMap.put("onlineStatus", newStatus);
            return AjaxResult.success("健康检查完成", resultMap);
        } catch (Exception e) {
            return error("健康检查失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有主机的ID和在线状态
     *
     * @return 主机状态列表
     */
    @PreAuthorize("@ss.hasPermi('business:host:whitelist:query')")
    @GetMapping("/status")
    public AjaxResult getAllHostStatus()
    {
        try {
            // 查询所有未删除的主机
            WsHostWhitelist query = new WsHostWhitelist();
            query.setDelFlag(0);
            List<WsHostWhitelist> hosts = wsHostWhitelistMapper.selectList(query);

            // 转换为状态列表
            List<Map<String, Object>> statusList = hosts.stream().map(host -> {
                Map<String, Object> statusMap = new HashMap<>();
                statusMap.put("hostId", host.getHostId());
                statusMap.put("hostName", host.getHostName());
                statusMap.put("hostType", host.getHostType());
                statusMap.put("onlineStatus", host.getOnlineStatus());
                statusMap.put("status", host.getStatus());
                return statusMap;
            }).collect(Collectors.toList());

            return success(statusList);
        } catch (Exception e) {
            return error("获取主机状态失败: " + e.getMessage());
        }
    }
}
