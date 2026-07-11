package com.wx.fbsir.business.nodeeditwithstrategy.domain;

import java.math.BigDecimal;

import com.wx.fbsir.common.annotation.Excel;
import com.wx.fbsir.common.core.domain.BaseEntity;

/**
 * 策略参数映射表
 *
 * @author FBSir
 */
public class StrategyParam extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 策略名称 */
    @Excel(name = "策略名称")
    private String strategyName;

    /** 模型名称 */
    @Excel(name = "模型名称")
    private String modelName;

    /** 温度 */
    @Excel(name = "temperature")
    private BigDecimal temperature;

    /** top_p */
    @Excel(name = "topP")
    private BigDecimal topP;

    /** 最大回复 token */
    @Excel(name = "maxTokens")
    private Integer maxTokens;

    /** 提示词模板 */
    @Excel(name = "prompt")
    private String prompt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public BigDecimal getTopP() {
        return topP;
    }

    public void setTopP(BigDecimal topP) {
        this.topP = topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}

