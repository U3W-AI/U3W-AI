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
public class ChatCompletionStreamResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("object")
    private String object = "chat.completion.chunk"; // 流式响应固定值

    @JsonProperty("created")
    private long created;

    @JsonProperty("model")
    private String model;

    @JsonProperty("choices")
    private List<Choice> choices;

    // 内部类：流式响应的片段
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        @JsonProperty("index")
        private int index;

        @JsonProperty("delta") // 流式响应使用delta字段，而非message
        private Delta delta;

        @JsonProperty("finish_reason") // 结束原因：null表示未结束，"stop"表示结束
        private String finishReason;
    }

    // 内部类：增量内容
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delta {
        @JsonProperty("content") // 每段生成的文本内容
        private String content;
    }
}
