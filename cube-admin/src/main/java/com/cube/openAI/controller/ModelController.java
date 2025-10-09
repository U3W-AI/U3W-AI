package com.cube.openAI.controller;

import com.cube.openAI.config.ModelRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/9/9 17:16
 */
@RestController
@RequestMapping("/v1")
public class ModelController {

    @Autowired
    private ModelRegistry modelRegistry;

    // 返回所有可用模型（兼容OpenAI的/models接口）
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> listModels() {
        List<String> modelNames = modelRegistry.getAllModelNames();
        List<Map<String, Object>> models = modelNames.stream().map(name -> {
            Map<String, Object> model = new HashMap<>();
            model.put("id", name);
            model.put("object", "model");
            model.put("created", System.currentTimeMillis() / 1000);
            model.put("owned_by", "U3W");
            return model;
        }).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("data", models);
        response.put("object", "list");
        return ResponseEntity.ok(response);
    }
}