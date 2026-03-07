package com.wx.fbsir.business.airobotmessage.controller;

import com.wx.fbsir.business.airobotmessage.domain.WecomWebhook;
import com.wx.fbsir.business.airobotmessage.service.MessageService;
import com.wx.fbsir.common.annotation.Anonymous;
import com.wx.fbsir.common.core.domain.AjaxResult;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/business/message")
@CrossOrigin(origins = "*")
public class MessageController {
    @Resource
    private MessageService messageService;

    /**
     * 获取 Webhook 列表
     */
    @GetMapping("/list")
    @Anonymous
    public AjaxResult listWecomWebhook() {
        try {
            List<WecomWebhook> wecomWebhookList=messageService.selectWecomWebhookList();
            return AjaxResult.success(wecomWebhookList);
        } catch (Exception e) {
            return AjaxResult.error("获取Webhook列表失败," + e.getMessage());
        }
    }

    /**
     * 获取 Webhook地址
     *
     * @param id Webhook地址 ID
     * @return Webhook地址
     */
    @GetMapping("/get")
    @Anonymous
    public AjaxResult getWecomWebhook(@RequestParam Long id) {
        try {
            WecomWebhook wecomWebhook = messageService.selectWecomWebhookById(id);
            return AjaxResult.success(wecomWebhook);
        } catch (Exception e) {
            return AjaxResult.error("获取Webhook地址失败," + e.getMessage());
        }

    }

    /**
     * 插入Webhook地址
     *
     * @param wecomWebhook Webhook地址
     * @return 插入结果
     */
    @PostMapping("/insert")
    @Anonymous
    public AjaxResult insertWecomWebhook(@RequestBody WecomWebhook wecomWebhook) {
        try {
            int result = messageService.insertWecomWebhook(wecomWebhook);
            return result > 0 ? AjaxResult.success("插入成功") : AjaxResult.error("插入失败");
        } catch (Exception e) {
            return AjaxResult.error("插入Webhook地址失败" + e.getMessage());
        }
    }

    /**
     * 更新Webhook地址
     *
     * @param wecomWebhook Webhook地址
     * @return 更新结果
     */
    @PostMapping("/update")
    @Anonymous
    public AjaxResult updateWecomWebhook(@RequestBody WecomWebhook wecomWebhook) {
        try {
            boolean result = messageService.updateWecomWebhook(wecomWebhook);
            return result ? AjaxResult.success("更新成功") : AjaxResult.error("更新失败");
        } catch (Exception e) {
            return AjaxResult.error("更新Webhook地址失败" + e.getMessage());
        }
    }

    /**
     * 删除Webhook地址
     *
     * @param id Webhook地址 ID
     * @return 删除结果
     */
    @PostMapping("/delete")
    @Anonymous
    public AjaxResult deleteWecomWebhook(@RequestParam Long id) {
        try {
            int result = messageService.deleteWecomWebhookById(id);
            return result > 0 ? AjaxResult.success("删除成功") : AjaxResult.error("删除失败");
        } catch (Exception e) {
            return AjaxResult.error("删除Webhook地址失败" + e.getMessage());
        }
    }

    /**
     * 向微信群推送咨询消息
     *
     * @param userId         用户名
     * @param messageContent 消息内容
     */
    @PostMapping("/send")
    @Anonymous
    public void sendMessage(@RequestParam String userId, @RequestParam String messageContent,@RequestParam String webhookId) {
        try {
            messageService.sendTemplateMessage(userId, messageContent, Long.valueOf(webhookId));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 向微信群推送修改提示词消息
     *
     * @param userId 用户名
     */
    @PostMapping("/updateprompt")
    @Anonymous
    public void sendPromptMessage(@RequestParam String userId) {
        try {
            messageService.sendTemplatePromptMessage(userId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
