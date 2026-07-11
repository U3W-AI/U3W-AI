package com.wx.fbsir.business.nodeeditwithstrategy.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.wx.fbsir.business.nodeeditwithstrategy.domain.StrategyParamMapping;

/**
 * 策略参数映射Mapper接口。
 *
 * <p>
 * 负责 {@link StrategyParamMapping} 与数据库表 {@code strategy_param_mapping}
 * 之间的持久化映射操作。
 * </p>
 */
@Mapper
public interface StrategyParamMappingMapper {

    StrategyParamMapping selectById(Long id);

    StrategyParamMapping selectByStrategyName(String strategyName);

    List<StrategyParamMapping> selectList(StrategyParamMapping filter);

    int insert(StrategyParamMapping entity);

    int update(StrategyParamMapping entity);

    int deleteByIds(Long[] ids);
}

