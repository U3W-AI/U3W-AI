package com.wx.fbsir.business.nodeeditwithstrategy.domain;

import java.math.BigDecimal;

import com.wx.fbsir.common.core.domain.BaseEntity;

/**
 * 策略参数映射实体。
 *
 * <p>
 * 用于为不同的"策略"（如：成本优先、质量优先、最大回复Token等）
 * 预先配置统一的模型参数，便于在业务调用时按策略名称进行覆盖。
 * </p>
 */
public class StrategyParamMapping extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;
    /** 策略名称（唯一） */
    private String strategyName;
    /** 模型名称 */
    private String modelName;
    /** 温度 */
    private BigDecimal temperature;
    /** top_p */
    private BigDecimal topP;
    /** 最大回复Token */
    private Integer maxTokens;
    /** 提示词模板 */
    private String prompt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }

    public BigDecimal getTopP() { return topP; }
    public void setTopP(BigDecimal topP) { this.topP = topP; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
}

