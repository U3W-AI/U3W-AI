package com.tencent.wework;

import cn.felord.WeComTokenCacheable;
import cn.felord.api.WorkWeChatApi;
import cn.felord.domain.WeComResponse;
import cn.felord.domain.webhook.WebhookBody;
import cn.felord.domain.webhook.WebhookMarkdownBody;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author YHX
 * @date 2024年07月18日 08:05
 */
public class Test {

    @Autowired
    WeComTokenCacheable weComTokenCacheable;
    @Autowired
    private WorkWeChatApi workWeChatApi;
    public static void main(String[] args) {
        String content = "我是webhook机器人，我叫小杨，我可以推送消息";
        WebhookBody markdownBody = WebhookMarkdownBody.from(content);
        WeComResponse weComResponse = WorkWeChatApi.webhookApi().send("cc4d7c79-2c1e-4e49-9263-5b837012fa52", markdownBody);
    }



}
