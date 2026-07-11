package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.dto.business.wecom.WecomCheckResponse;
import com.wx.fbsir.business.fbs.dto.business.wecom.WecomSyncReadResponse;

/**
 * 企微同步服务接口
 *
 * @author FBSir
 * @date 2026-04-13
 */
public interface WecomSyncService {

    /**
     * 读取指定 Sheet 数据并记录日志
     *
     * @param sheetName Sheet 名称（不传默认 "meta"）
     * @return 读取响应（含前 10 条预览）
     */
    WecomSyncReadResponse readSheet(String sheetName);

    /**
     * 检测 CLI 可用性（只检文件，不检认证态，不写日志）
     *
     * @return 检测响应
     */
    WecomCheckResponse check();
}
