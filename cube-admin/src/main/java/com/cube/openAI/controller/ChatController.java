package com.cube.openAI.controller;

import com.cube.openAI.constants.OpenAIExceptionConstants;
import com.cube.openAI.pojos.ChatCompletionRequest;
import com.cube.openAI.pojos.ChatCompletionResponse;
import com.cube.openAI.pojos.ChatCompletionStreamResponse;
import com.cube.openAI.pojos.Message;
import com.cube.openAI.config.ModelRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import javax.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/9/9 17:16
 */
@RestController
@RequestMapping("/v1") // 基础路径与OpenAI一致
public class ChatController {

    @Autowired
    private ModelRegistry modelRegistry;

    // 核心接口：兼容OpenAI的聊天接口
    @PostMapping(value = "/chat/completions",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Object createCompletion(
            @Valid @RequestBody ChatCompletionRequest request) {

        // 1. 验证模型是否存在
        var model = modelRegistry.getModel(request.getModel());
        if (model == null) {
            throw new IllegalArgumentException(OpenAIExceptionConstants.MODEL_NOT_FOUND + ":" + request.getModel());
        }
        // 1. 验证是否需要流式输出
        if (request.isStream()) {
            return model.generateByStream(
                    request.getMessages(),
                    request.getTemperature(),
                    request.getMaxTokens()
            );
        }

        // 2. 调用模型生成结果
        String responseText = model.generate(
                request.getMessages(),
                request.getTemperature(),
                request.getMaxTokens()
        );
        // 3. 构建符合OpenAI规范的响应
        long timestamp = System.currentTimeMillis() / 1000; // 秒级时间戳
        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .id("chatcmpl-" + UUID.randomUUID().toString().substring(0, 10))
                .created(timestamp)
                .model(request.getModel())
                .choices(List.of(
                        ChatCompletionResponse.Choice.builder()
                                .index(0)
                                .message(new Message("assistant", responseText))
                                .finishReason("stop")
                                .build()
                ))
                .usage(ChatCompletionResponse.Usage.builder()
                        .promptTokens(calculatePromptTokens(request.getMessages()))
                        .completionTokens(responseText.length())
                        .totalTokens(calculatePromptTokens(request.getMessages()) + responseText.length())
                        .build())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                .body(response);
    }

    // 简单计算输入令牌数（实际需根据模型tokenizer实现）
    private int calculatePromptTokens(List<Message> messages) {
        return messages.stream().mapToInt(m -> m.getContent().length()).sum();
    }
}