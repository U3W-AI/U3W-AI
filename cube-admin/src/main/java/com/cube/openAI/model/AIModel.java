package com.cube.openAI.model;

import com.cube.openAI.pojos.Message;
import reactor.core.publisher.Flux;

import java.util.List;

// 所有AI模型的统一调用标准
public interface AIModel {
    /**
     * 非流式输出
     */
    String generate(List<Message> messages, Double temperature, Integer maxTokens);

    /**
     * 流式输出
     */
    Flux<String> generateByStream(List<Message> messages, Double temperature, Integer maxTokens);

}