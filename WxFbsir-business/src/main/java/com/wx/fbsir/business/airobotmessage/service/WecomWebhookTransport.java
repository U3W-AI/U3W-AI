package com.wx.fbsir.business.airobotmessage.service;

import java.io.IOException;

public interface WecomWebhookTransport {
    TransportResult send(String webhookUrl, String jsonPayload) throws IOException, InterruptedException;

    record TransportResult(int httpStatus, String responseBody) {}
}
