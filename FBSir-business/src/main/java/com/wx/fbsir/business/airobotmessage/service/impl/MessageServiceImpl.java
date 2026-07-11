package com.wx.fbsir.business.airobotmessage.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.airobotmessage.domain.WecomWebhook;
import com.wx.fbsir.business.airobotmessage.domain.WebhookDelivery;
import com.wx.fbsir.business.airobotmessage.dto.WebhookDeliveryReceipt;
import com.wx.fbsir.business.airobotmessage.dto.WebhookEnterpriseOption;
import com.wx.fbsir.business.airobotmessage.dto.WebhookMetadataResponse;
import com.wx.fbsir.business.airobotmessage.dto.WebhookSendRequest;
import com.wx.fbsir.business.airobotmessage.dto.WebhookUpsertRequest;
import com.wx.fbsir.business.airobotmessage.mapper.MessageMapper;
import com.wx.fbsir.business.airobotmessage.mapper.WebhookDeliveryMapper;
import com.wx.fbsir.business.airobotmessage.service.MessageService;
import com.wx.fbsir.business.airobotmessage.service.WecomWebhookTransport;
import com.wx.fbsir.business.airobotmessage.service.WebhookScopeGuard;
import com.wx.fbsir.business.airobotmessage.service.WebhookSecretCodec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MessageServiceImpl implements MessageService {
    private final MessageMapper messageMapper;
    private final WebhookDeliveryMapper deliveryMapper;
    private final WebhookScopeGuard scopeGuard;
    private final WebhookSecretCodec secretCodec;
    private final WecomWebhookTransport transport;
    private final ObjectMapper objectMapper;

    @Value("${webhook.action-hosts:}")
    private String allowedActionHosts;

    public MessageServiceImpl(MessageMapper messageMapper,
                              WebhookDeliveryMapper deliveryMapper,
                              WebhookScopeGuard scopeGuard,
                              WebhookSecretCodec secretCodec,
                              WecomWebhookTransport transport,
                              ObjectMapper objectMapper) {
        this.messageMapper = messageMapper;
        this.deliveryMapper = deliveryMapper;
        this.scopeGuard = scopeGuard;
        this.secretCodec = secretCodec;
        this.transport = transport;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<WebhookEnterpriseOption> enterprises(Long userId) {
        return scopeGuard.listAdminEnterprises(userId);
    }

    @Override
    public List<WebhookMetadataResponse> list(Long enterpriseId, Long userId) {
        scopeGuard.requireActiveMember(enterpriseId, userId);
        return messageMapper.selectWecomWebhookList(enterpriseId).stream().map(this::metadata).toList();
    }

    @Override
    public WebhookMetadataResponse get(Long id, Long enterpriseId, Long userId) {
        scopeGuard.requireActiveMember(enterpriseId, userId);
        return metadata(requireWebhook(id, enterpriseId));
    }

    @Override
    public WebhookMetadataResponse create(WebhookUpsertRequest request, Long userId) {
        scopeGuard.requireActiveMember(request.enterpriseId(), userId);
        if (!StringUtils.hasText(request.webhookUrl())) {
            throw new IllegalArgumentException("新增Webhook必须提供地址");
        }
        WecomWebhook webhook = fromRequest(request);
        webhook.setWebhookSecretRef(secretCodec.encode(request.webhookUrl()));
        webhook.setWebhookUrl(WebhookSecretCodec.STORED_SENTINEL);
        webhook.setVersion(1);
        messageMapper.insertWecomWebhook(webhook);
        return metadata(requireWebhook(webhook.getId(), request.enterpriseId()));
    }

    @Override
    public WebhookMetadataResponse update(WebhookUpsertRequest request, Long userId) {
        scopeGuard.requireActiveMember(request.enterpriseId(), userId);
        if (request.id() == null || request.version() == null) {
            throw new IllegalArgumentException("更新Webhook必须提供id和version");
        }
        requireWebhook(request.id(), request.enterpriseId());
        WecomWebhook webhook = fromRequest(request);
        if (StringUtils.hasText(request.webhookUrl())
                && !WebhookSecretCodec.MASKED_URL.equals(request.webhookUrl())) {
            webhook.setWebhookSecretRef(secretCodec.encode(request.webhookUrl()));
            webhook.setWebhookUrl(WebhookSecretCodec.STORED_SENTINEL);
        }
        int updated = messageMapper.updateWecomWebhook(webhook);
        if (updated != 1) {
            throw new IllegalStateException("Webhook已被其他操作修改，请刷新后重试");
        }
        return metadata(requireWebhook(request.id(), request.enterpriseId()));
    }

    @Override
    public int delete(Long id, Long enterpriseId, Integer version, Long userId) {
        scopeGuard.requireActiveMember(enterpriseId, userId);
        if (id == null || version == null) throw new IllegalArgumentException("id和version不能为空");
        int deleted = messageMapper.deleteWecomWebhookById(id, enterpriseId, version);
        if (deleted != 1) throw new IllegalStateException("Webhook不存在或版本已变化");
        return deleted;
    }

    @Override
    public WebhookDeliveryReceipt send(WebhookSendRequest request, Long userId) {
        scopeGuard.requireActiveMember(request.enterpriseId(), userId);
        WecomWebhook webhook = requireWebhook(request.webhookId(), request.enterpriseId());
        if (!Boolean.TRUE.equals(webhook.getStatus())) {
            throw new IllegalStateException("Webhook已禁用");
        }

        String payload = buildPayload(request);
        String payloadHash = sha256(payload);
        WebhookDelivery existing = deliveryMapper.selectByIdempotencyKey(
                request.enterpriseId(), request.idempotencyKey());
        if (existing != null) return replay(existing, payloadHash, request.webhookId());

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setEnterpriseId(request.enterpriseId());
        delivery.setWebhookId(request.webhookId());
        delivery.setActorUserId(userId);
        delivery.setIdempotencyKey(request.idempotencyKey());
        delivery.setPayloadHash(payloadHash);
        delivery.setTraceId(UUID.randomUUID().toString());
        delivery.setStatus("PENDING");
        try {
            deliveryMapper.insert(delivery);
        } catch (DuplicateKeyException e) {
            WebhookDelivery concurrent = deliveryMapper.selectByIdempotencyKey(
                    request.enterpriseId(), request.idempotencyKey());
            if (concurrent == null) throw e;
            return replay(concurrent, payloadHash, request.webhookId());
        }

        try {
            String url = secretCodec.resolve(webhook.getWebhookSecretRef());
            WecomWebhookTransport.TransportResult result = transport.send(url, payload);
            delivery.setProviderHttpStatus(result.httpStatus());
            applyProviderResult(delivery, result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            delivery.setStatus("UNKNOWN");
            delivery.setProviderErrmsg("request interrupted");
        } catch (Exception e) {
            delivery.setStatus("UNKNOWN");
            delivery.setProviderErrmsg(sanitizeError(e.getMessage()));
        }
        if (deliveryMapper.updateResult(delivery) != 1) {
            throw new IllegalStateException("投递结果未能持久化，traceId=" + delivery.getTraceId());
        }
        return receipt(delivery);
    }

    private void applyProviderResult(WebhookDelivery delivery, WecomWebhookTransport.TransportResult result) {
        int errcode = -1;
        String errmsg = "invalid provider response";
        try {
            JsonNode body = objectMapper.readTree(result.responseBody());
            errcode = body.path("errcode").asInt(-1);
            errmsg = body.path("errmsg").asText(errmsg);
        } catch (Exception ignored) {
            // The response body is deliberately not logged because it may contain provider details.
        }
        delivery.setProviderErrcode(errcode);
        delivery.setProviderErrmsg(sanitizeError(errmsg));
        boolean httpAccepted = result.httpStatus() >= 200 && result.httpStatus() < 300;
        boolean accepted = httpAccepted && errcode == 0;
        delivery.setStatus(accepted ? "PROVIDER_ACCEPTED"
                : result.httpStatus() >= 500 ? "UNKNOWN" : "REJECTED");
    }

    private String buildPayload(WebhookSendRequest request) {
        try {
            validateActionUrl(request.actionUrl());
            if (!StringUtils.hasText(request.actionUrl())) {
                return objectMapper.writeValueAsString(Map.of(
                        "msgtype", "markdown",
                        "markdown", Map.of("content", "**" + request.title() + "**\n" + request.messageContent())));
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("card_type", "text_notice");
            card.put("main_title", Map.of("title", request.title()));
            card.put("sub_title_text", request.messageContent());
            card.put("card_action", Map.of(
                    "type", 1,
                    "url", request.actionUrl()));
            return objectMapper.writeValueAsString(Map.of(
                    "msgtype", "template_card",
                    "template_card", card));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("消息序列化失败", e);
        }
    }

    private WecomWebhook fromRequest(WebhookUpsertRequest request) {
        WecomWebhook webhook = new WecomWebhook();
        webhook.setId(request.id());
        webhook.setEnterpriseId(request.enterpriseId());
        webhook.setName(request.name().trim());
        webhook.setDescription(request.description());
        webhook.setStatus(request.status() == null || request.status());
        webhook.setVersion(request.version() == null ? 1 : request.version());
        return webhook;
    }

    private WecomWebhook requireWebhook(Long id, Long enterpriseId) {
        WecomWebhook webhook = messageMapper.selectWecomWebhookById(id, enterpriseId);
        if (webhook == null) throw new IllegalArgumentException("Webhook不存在");
        return webhook;
    }

    private WebhookMetadataResponse metadata(WecomWebhook webhook) {
        return new WebhookMetadataResponse(webhook.getId(), webhook.getEnterpriseId(), webhook.getName(),
                WebhookSecretCodec.MASKED_URL, webhook.getDescription(), webhook.getStatus(), webhook.getVersion(),
                webhook.getCreateTime(), webhook.getUpdateTime());
    }

    private WebhookDeliveryReceipt replay(WebhookDelivery existing, String payloadHash, Long webhookId) {
        if (!payloadHash.equals(existing.getPayloadHash())
                || !java.util.Objects.equals(existing.getWebhookId(), webhookId)) {
            throw new IllegalStateException("幂等键已用于不同消息或目标");
        }
        if ("PENDING".equals(existing.getStatus())
                && deliveryMapper.markStalePendingUnknown(existing.getEnterpriseId(), existing.getIdempotencyKey()) == 1) {
            existing = deliveryMapper.selectByIdempotencyKey(existing.getEnterpriseId(), existing.getIdempotencyKey());
        }
        return receipt(existing);
    }

    private void validateActionUrl(String actionUrl) {
        if (!StringUtils.hasText(actionUrl)) return;
        try {
            java.net.URI uri = java.net.URI.create(actionUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1) {
                throw new IllegalArgumentException("行动链接必须是安全的HTTPS地址");
            }
            boolean allowed = StringUtils.hasText(allowedActionHosts)
                    && java.util.Arrays.stream(allowedActionHosts.split(","))
                    .map(String::trim)
                    .anyMatch(host -> host.equalsIgnoreCase(uri.getHost()));
            if (!allowed) throw new IllegalArgumentException("行动链接域名未获授权");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("行动链接必须使用已授权的HTTPS域名");
        }
    }

    private WebhookDeliveryReceipt receipt(WebhookDelivery delivery) {
        return new WebhookDeliveryReceipt(delivery.getId(), delivery.getTraceId(), delivery.getStatus(),
                delivery.getProviderHttpStatus(), delivery.getProviderErrcode(), delivery.getProviderErrmsg());
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("消息摘要生成失败", e);
        }
    }

    private String sanitizeError(String value) {
        if (!StringUtils.hasText(value)) return "provider request failed";
        String sanitized = value.replaceAll("(?i)key=[A-Za-z0-9_-]+", "key=****")
                .replaceAll("[\\r\\n\\t]+", " ");
        return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
    }
}
