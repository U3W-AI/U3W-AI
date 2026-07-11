package com.wx.fbsir.business.smartbot.dto;

import lombok.Builder;
import lombok.Value;

/** Normalized protocol metadata; message bodies and credentials are deliberately excluded. */
@Value
@Builder
public class SmartBotInboundEnvelope {
    String msgId;
    String aibotId;
    String opaqueSenderId;
    String chatType;
    String chatId;
    String msgType;
    String eventType;
    String payloadHash;

    @Override
    public String toString() {
        return "SmartBotInboundEnvelope[msgId=***, aibotId=" + aibotId
            + ", opaqueSenderId=***, chatType=" + chatType
            + ", chatId=***, msgType=" + msgType
            + ", eventType=" + eventType + ", payloadHash=***]";
    }
}
