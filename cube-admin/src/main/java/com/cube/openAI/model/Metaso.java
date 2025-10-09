package com.cube.openAI.model;

import com.cube.openAI.pojos.Message;
import com.cube.openAI.utils.AIResultUtil;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/9/16 16:50
 */
@Slf4j
public class Metaso implements AIModel{
    @Override
    public String generate(List<Message> messages, Double temperature, Integer maxTokens) {
        try {
            return AIResultUtil.waitForResult(messages, "metaso", "mita", false);
        } catch (Exception e) {
            log.error(e.getMessage());
            return e.getMessage();
        }
    }

    @Override
    public Flux<String> generateByStream(List<Message> messages, Double temperature, Integer maxTokens) {
        try {
            return AIResultUtil.waitForResultByStream(messages, "metaso", "mita", "metaso");
        } catch (InterruptedException e) {
            log.error(e.getMessage());
            return Flux.just(e.getMessage());
        }
    }
}
