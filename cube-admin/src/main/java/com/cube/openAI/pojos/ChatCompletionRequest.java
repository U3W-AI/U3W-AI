package com.cube.openAI.pojos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class ChatCompletionRequest {
    @NotBlank(message = "model参数不能为空")
    @JsonProperty("model") // 模型名称（如"model-a"）
    private String model;
    
    @NotEmpty(message = "messages参数不能为空")
    @JsonProperty("messages") // 对话历史
    private List<Message> messages;
    
    @JsonProperty("temperature") // 随机性（默认0.7）
    private Double temperature = 0.7;
    
    @JsonProperty("max_tokens") // 最大生成长度（默认1024）
    private Integer maxTokens = 1024;
    
    @JsonProperty("stream") // 是否流式响应（默认false）
    private boolean stream = false;
}