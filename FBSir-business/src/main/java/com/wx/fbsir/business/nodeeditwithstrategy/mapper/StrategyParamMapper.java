package com.wx.fbsir.business.nodeeditwithstrategy.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.wx.fbsir.business.nodeeditwithstrategy.domain.StrategyParam;

@Mapper
public interface StrategyParamMapper {
    StrategyParam selectStrategyParamById(Long id);

    StrategyParam selectByStrategyName(String strategyName);

    List<StrategyParam> selectStrategyParamList(StrategyParam query);

    int insertStrategyParam(StrategyParam param);

    int updateStrategyParam(StrategyParam param);

    int deleteStrategyParamById(Long id);

    int deleteStrategyParamByIds(Long[] ids);
}

