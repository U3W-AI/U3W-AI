package com.cube.openAI.pojos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionResponse {
    @JsonProperty("id") // 响应唯一标识（如"chatcmpl-xxx"）
    private String id;
    
    @JsonProperty("object") // 固定值"chat.completion"
    private String object = "chat.completion";
    
    @JsonProperty("created") // 时间戳（秒级）
    private long created;
    
    @JsonProperty("model") // 实际调用的模型
    private String model;
    
    @JsonProperty("choices") // 生成结果列表
    private List<Choice> choices;
    
    @JsonProperty("usage") // 令牌使用统计
    private Usage usage;

    // 内部类：生成结果
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        @JsonProperty("index")
        private int index;
        
        @JsonProperty("message")
        private Message message;
        
        @JsonProperty("finish_reason") // 结束原因："stop"、"length"等
        private String finishReason;
    }

    // 内部类：令牌统计
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;
        
        @JsonProperty("completion_tokens")
        private int completionTokens;
        
        @JsonProperty("total_tokens")
        private int totalTokens;
    }
}