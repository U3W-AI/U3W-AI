package com.wx.fbsir.business.nodeeditwithstrategy.service;

import java.util.List;

import com.wx.fbsir.business.nodeeditwithstrategy.domain.StrategyParamMapping;

/**
 * 策略参数映射Service接口。
 *
 * <p>
 * 封装策略参数映射的领域服务能力，为上层控制器提供简洁的调用入口。
 * </p>
 */
public interface IStrategyParamMappingService {

    StrategyParamMapping selectById(Long id);

    StrategyParamMapping selectByStrategyName(String strategyName);

    List<StrategyParamMapping> selectList(StrategyParamMapping filter);

    int insert(StrategyParamMapping entity);

    int update(StrategyParamMapping entity);

    int deleteByIds(Long[] ids);
}

