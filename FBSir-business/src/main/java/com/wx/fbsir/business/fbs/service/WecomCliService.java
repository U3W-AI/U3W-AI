package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.WecomCliResult;

/**
 * wecom-cli 命令执行服务接口
 *
 * @author FBSir
 * @date 2026-04-13
 */
public interface WecomCliService {

    /**
     * 执行 wecom-cli 命令
     *
     * @param category 类别（如 "doc"）
     * @param method   方法名（如 "smartsheet_get_records"）
     * @param params   参数 JSON 字符串
     * @return 执行结果
     */
    WecomCliResult execute(String category, String method, String params);

    /**
     * 检查 wecom-cli 是否可用（文件是否存在）
     *
     * @return true=文件存在且可执行
     */
    boolean isAvailable();

    /**
     * 获取当前配置的 wecom-cli 路径
     *
     * @return CLI 可执行文件绝对路径
     */
    String getCliPath();
}
