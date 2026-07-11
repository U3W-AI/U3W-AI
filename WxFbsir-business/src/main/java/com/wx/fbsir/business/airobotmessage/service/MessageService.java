package com.wx.fbsir.business.airobotmessage.service;

import com.wx.fbsir.business.airobotmessage.dto.WebhookDeliveryReceipt;
import com.wx.fbsir.business.airobotmessage.dto.WebhookMetadataResponse;
import com.wx.fbsir.business.airobotmessage.dto.WebhookSendRequest;
import com.wx.fbsir.business.airobotmessage.dto.WebhookUpsertRequest;

import java.util.List;
import com.wx.fbsir.business.airobotmessage.dto.WebhookEnterpriseOption;

public interface MessageService {
    List<WebhookEnterpriseOption> enterprises(Long userId);
    List<WebhookMetadataResponse> list(Long enterpriseId, Long userId);
    WebhookMetadataResponse get(Long id, Long enterpriseId, Long userId);
    WebhookMetadataResponse create(WebhookUpsertRequest request, Long userId);
    WebhookMetadataResponse update(WebhookUpsertRequest request, Long userId);
    int delete(Long id, Long enterpriseId, Integer version, Long userId);
    WebhookDeliveryReceipt send(WebhookSendRequest request, Long userId);
}
