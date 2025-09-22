package com.cube.openAI.config;

import com.cube.openAI.model.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 管理所有模型，通过model名称路由
@Component
public class ModelRegistry {
    private final Map<String, AIModel> modelMap = new HashMap<>();

    // 初始化时注册所有模型
    public ModelRegistry() {
        modelMap.put("bai_du", new BaiDuAI());
        modelMap.put("deepseek", new DeepSeek());
        modelMap.put("dou_bao", new DouBao());
        modelMap.put("yuan_bao_T1", new YuanBaoT1());
        modelMap.put("yuan_bao_DS", new YuanBaoDS());
        modelMap.put("tong_yi", new TongYi());
        modelMap.put("zhi_hu_zhi_da", new ZhiHuZhiDa());
        modelMap.put("metaso", new Metaso());
    }

    // 根据model名称获取模型实例
    public AIModel getModel(String modelName) {
        return modelMap.get(modelName);
    }

    // 获取所有可用模型名称
    public List<String> getAllModelNames() {
        return modelMap.keySet().stream().toList();
    }
}