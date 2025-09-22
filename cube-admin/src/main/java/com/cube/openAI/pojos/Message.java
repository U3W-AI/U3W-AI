package com.cube.openAI.pojos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @JsonProperty("role") // 角色："user"、"assistant"、"system"
    private String role;
    
    @JsonProperty("content") // 消息内容
    private String content;
}