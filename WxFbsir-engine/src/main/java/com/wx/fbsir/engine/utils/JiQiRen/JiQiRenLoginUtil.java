package com.wx.fbsir.engine.utils.JiQiRen;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.wx.fbsir.engine.capability.base.StreamTaskHelper;
import com.wx.fbsir.engine.controller.yuanqi.YuanQiLoginController;
import com.wx.fbsir.engine.utils.yuanqi.YuanQiLoginUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class JiQiRenLoginUtil {
    @Autowired
    private YuanQiLoginController yuanQiLoginController;
    /**
     * 企业微信机器人扫码登录
     *
     * @param page Playwright页面对象
     * @param log  日志对象
     * @param task 流式返回
     * @param requestId 请求id
     * @param userId 用户id
     *
     * @return page Playwright页面对象
     */
    public Page scanLogin(Page page, StreamTaskHelper.StreamTask task, Logger log,String userId,String requestId){

        // TODO: captureAndUpload方法为private，无法直接调用，需要使用其他方式获取二维码
        task.sendLog("正在等待登录...");
        log.info("[机器人扫码登录] 开始等待 - 用户: {}", userId);
        
        long startTime = System.currentTimeMillis();
        long maxWaitTime = 300000; // 5分钟超时
        long lastScreenshotTime = System.currentTimeMillis();
        int screenshotCount = 1;
        String lastQrCodeUrl = null;

        // 每2秒检测一次登录状态
        while (true) {
            long elapsedTime = System.currentTimeMillis() - startTime;

            // 检查超时
            if (elapsedTime > maxWaitTime) {
                Map<String, Object> timeoutData = new HashMap<>();
                timeoutData.put("success", false);
                timeoutData.put("timeout", true);
                timeoutData.put("qrCodeUrl", lastQrCodeUrl);
                task.sendSuccess("扫码登录超时", timeoutData);
                task.stop();
                log.warn("[机器人扫码登录] 超时 - 用户: {}, 请求: {}", userId, requestId);
                return null;
            }

            // TODO: 每30秒更新截图功能暂时禁用（captureAndUpload为private方法）
            if (System.currentTimeMillis() - lastScreenshotTime >= 30000) {
                task.sendLog("等待登录中...（已等待" + (elapsedTime / 1000) + "秒）");
                lastScreenshotTime = System.currentTimeMillis();
            }

            boolean isQuitElementExist = true;
            try {
                // 等待“退出”元素出现，超时30秒
                page.waitForSelector(":has-text('退出')", new Page.WaitForSelectorOptions()
                        .setTimeout(30000));
            } catch (TimeoutError e) {
                // 捕获超时异常 → 判定元素未出现
                isQuitElementExist = false;
            } catch (Exception e) {
                // 其他异常也判定为未出现
                isQuitElementExist = false;
            }
            if (isQuitElementExist) {
                // 元素存在则代表登录成功
                task.sendLog("登录成功！");
                log.info("[机器人扫码登录] 成功 - 用户: {}", userId);
                break;
            }

            // 等待2秒后再次检测
            page.waitForTimeout(2000);
        }
        return page;
    }



}
