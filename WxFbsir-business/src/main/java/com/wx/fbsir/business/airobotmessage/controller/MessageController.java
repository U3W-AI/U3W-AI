package com.wx.fbsir.business.airobotmessage.controller;

import com.wx.fbsir.business.airobotmessage.service.MessageService;
import com.wx.fbsir.common.annotation.Anonymous;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/business/message")
@CrossOrigin(origins = "*")
public class MessageController {
    @Resource
    private MessageService messageService;

    /**
     * 向微信群推送咨询消息
     *
     * @param userId         用户名
     * @param messageContent 消息内容
     */
    @PostMapping("/send")
    @Anonymous
    public void sendMessage(@RequestParam String userId, @RequestParam String messageContent) {
        try {
            messageService.sendTemplateMessage(userId, messageContent);
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
