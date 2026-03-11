package com.wx.fbsir.business.airobotmessage.controller;

import com.wx.fbsir.business.airobotmessage.domain.WecomWebhook;
import com.wx.fbsir.business.airobotmessage.service.MessageService;
import com.wx.fbsir.common.annotation.Anonymous;
import com.wx.fbsir.common.core.domain.AjaxResult;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/business/message")
@CrossOrigin(origins = "*")
public class MessageController {
    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);
    
    @Resource
    private MessageService messageService;

    /**
     * 获取 Webhook 列表
     */
    @PreAuthorize("@ss.hasPermi('business:wecom:list')")
    @GetMapping("/list")
    public AjaxResult listWecomWebhook() {
        try {
            List<WecomWebhook> wecomWebhookList=messageService.selectWecomWebhookList();
            return AjaxResult.success(wecomWebhookList);
        } catch (Exception e) {
            logger.error("获取 Webhook列表失败", e);
            return AjaxResult.error("获取 Webhook列表失败," + e.getMessage());
        }
    }

    /**
     * 获取 Webhook地址
     *
     * @param id Webhook地址 ID
     * @return Webhook地址
     */
    @PreAuthorize("@ss.hasPermi('business:wecom:query')")
    @GetMapping("/get")
    public AjaxResult getWecomWebhook(@RequestParam Long id) {
        try {
            if (id == null) {
                return AjaxResult.error("Webhook ID不能为空");
            }
            WecomWebhook wecomWebhook = messageService.selectWecomWebhookById(id);
            if (wecomWebhook == null) {
                return AjaxResult.error("Webhook 不存在");
            }
            return AjaxResult.success(wecomWebhook);
        } catch (Exception e) {
            logger.error("获取 Webhook地址失败", e);
            return AjaxResult.error("获取Webhook地址失败," + e.getMessage());
        }

    }

    /**
     * 插入Webhook地址
     *
     * @param wecomWebhook Webhook地址
     * @return 插入结果
     */
    @PreAuthorize("@ss.hasPermi('business:wecom:add')")
    @PostMapping("/insert")
    public AjaxResult insertWecomWebhook(@RequestBody WecomWebhook wecomWebhook) {
        try {
            if (wecomWebhook == null) {
                return AjaxResult.error("Webhook 信息不能为空");
            }
            if (wecomWebhook.getName() == null || wecomWebhook.getName().trim().isEmpty()) {
                return AjaxResult.error("Webhook 名称不能为空");
            }
            if (wecomWebhook.getWebhookUrl() == null || wecomWebhook.getWebhookUrl().trim().isEmpty()) {
                return AjaxResult.error("Webhook 地址不能为空");
            }
            int result = messageService.insertWecomWebhook(wecomWebhook);
            return result > 0 ? AjaxResult.success("插入成功") : AjaxResult.error("插入失败");
        } catch (Exception e) {
            logger.error("插入 Webhook地址失败", e);
            return AjaxResult.error("插入 Webhook地址失败" + e.getMessage());
        }
    }

    /**
     * 更新Webhook地址
     *
     * @param wecomWebhook Webhook地址
     * @return 更新结果
     */
    @PreAuthorize("@ss.hasPermi('business:wecom:edit')")
    @PostMapping("/update")
    public AjaxResult updateWecomWebhook(@RequestBody WecomWebhook wecomWebhook) {
        try {
            if (wecomWebhook == null) {
                return AjaxResult.error("Webhook 信息不能为空");
            }
            if (wecomWebhook.getId() == null) {
                return AjaxResult.error("Webhook ID不能为空");
            }
            if (wecomWebhook.getName() == null || wecomWebhook.getName().trim().isEmpty()) {
                return AjaxResult.error("Webhook 名称不能为空");
            }
            if (wecomWebhook.getWebhookUrl() == null || wecomWebhook.getWebhookUrl().trim().isEmpty()) {
                return AjaxResult.error("Webhook 地址不能为空");
            }
            boolean result = messageService.updateWecomWebhook(wecomWebhook);
            return result ? AjaxResult.success("更新成功") : AjaxResult.error("更新失败");
        } catch (Exception e) {
            logger.error("更新 Webhook地址失败", e);
            return AjaxResult.error("更新 Webhook地址失败" + e.getMessage());
        }
    }

    /**
     * 删除Webhook地址
     *
     * @param id Webhook地址 ID
     * @return 删除结果
     */
    @PreAuthorize("@ss.hasPermi('business:wecom:remove')")
    @PostMapping("/delete")
    public AjaxResult deleteWecomWebhook(@RequestParam Long id) {
        try {
            if (id == null) {
                return AjaxResult.error("Webhook ID不能为空");
            }
            int result = messageService.deleteWecomWebhookById(id);
            return result > 0 ? AjaxResult.success("删除成功") : AjaxResult.error("删除失败");
        } catch (Exception e) {
            logger.error("删除 Webhook地址失败", e);
            return AjaxResult.error("删除 Webhook地址失败" + e.getMessage());
        }
    }

    /**
     * 向微信群推送咨询消息
     *
     * @param userId         用户名
     * @param messageContent 消息内容
     * @param webhookId      Webhook ID
     */
    @PostMapping("/send")
    @Anonymous
    public void sendMessage(@RequestParam String userId, @RequestParam String messageContent, @RequestParam String webhookId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (messageContent == null || messageContent.trim().isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (webhookId == null || webhookId.trim().isEmpty()) {
            throw new IllegalArgumentException("Webhook ID不能为空");
        }
        
        Long webhookIdLong;
        try {
            webhookIdLong = Long.valueOf(webhookId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Webhook ID格式错误");
        }
        
        try {
            messageService.sendTemplateMessage(userId, messageContent, webhookIdLong);
        } catch (Exception e) {
            logger.error("发送消息失败", e);
            throw new RuntimeException("发送消息失败: " + e.getMessage());
        }
    }

    /**
     * 向微信群推送修改提示词消息
     *
     * @param userId    用户名
     * @param webhookId Webhook ID
     */
    @PostMapping("/updateprompt")
    @Anonymous
    public void sendPromptMessage(@RequestParam String userId, @RequestParam String webhookId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (webhookId == null || webhookId.trim().isEmpty()) {
            throw new IllegalArgumentException("Webhook ID不能为空");
        }
        
        Long webhookIdLong;
        try {
            webhookIdLong = Long.valueOf(webhookId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Webhook ID格式错误");
        }
        
        try {
            messageService.sendTemplatePromptMessage(userId, webhookIdLong);
        } catch (Exception e) {
            logger.error("发送提示词更新通知失败", e);
            throw new RuntimeException("发送提示词更新通知失败: " + e.getMessage());
        }
    }
}
