package com.wx.fbsir.business.nodeeditwithstrategy.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wx.fbsir.business.nodeeditwithstrategy.domain.StrategyParamMapping;
import com.wx.fbsir.business.nodeeditwithstrategy.mapper.StrategyParamMappingMapper;
import com.wx.fbsir.business.nodeeditwithstrategy.service.IStrategyParamMappingService;

/**
 * 策略参数映射Service实现。
 */
@Service
public class StrategyParamMappingServiceImpl implements IStrategyParamMappingService {

    @Autowired
    private StrategyParamMappingMapper mapper;

    @Override
    public StrategyParamMapping selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public StrategyParamMapping selectByStrategyName(String strategyName) {
        return mapper.selectByStrategyName(strategyName);
    }

    @Override
    public List<StrategyParamMapping> selectList(StrategyParamMapping filter) {
        return mapper.selectList(filter);
    }

    @Override
    public int insert(StrategyParamMapping entity) {
        return mapper.insert(entity);
    }

    @Override
    public int update(StrategyParamMapping entity) {
        return mapper.update(entity);
    }

    @Override
    public int deleteByIds(Long[] ids) {
        return mapper.deleteByIds(ids);
    }
}

