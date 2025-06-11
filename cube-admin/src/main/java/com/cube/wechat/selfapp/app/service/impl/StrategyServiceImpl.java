package com.cube.wechat.selfapp.app.service.impl;

import com.cube.wechat.selfapp.app.domain.Strategy;
import com.cube.wechat.selfapp.app.mapper.StrategyMapper;
import com.cube.wechat.selfapp.app.service.StrategyService;
import com.cube.wechat.selfapp.wecom.util.ResultBody;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年10月23日 10:28
 */
@Service
public class StrategyServiceImpl implements StrategyService {

    @Autowired
    private StrategyMapper strategyMapper;



    /**
     * 获取攻略列表接口(小程序使用，按照浏览记录排名)
     * */
    @Override
    public ResultBody getStrategyList(Strategy wcStrategy) {
        PageHelper.startPage(wcStrategy.getPage(),wcStrategy.getLimit());
        List<Map> list =  strategyMapper.getStrategyList(wcStrategy);
        PageInfo pageInfo = new PageInfo(list);
        return ResultBody.success(pageInfo);
    }

    /**
     * 查询攻略记录
     *
     * @param id 攻略记录主键
     * @return 攻略记录
     */
    @Override
    public ResultBody selectWcStrategyById(String id)
    {
        Map map =  strategyMapper.selectWcStrategyById(id);
        return ResultBody.success(map);
    }

    /**
     * 查询攻略记录列表
     *
     * @param wcStrategy 攻略记录
     * @return 攻略记录
     */
    @Override
    public ResultBody selectWcStrategyList(Strategy wcStrategy)
    {
        PageHelper.startPage(wcStrategy.getPage(),wcStrategy.getLimit());
        List<Map> list =  strategyMapper.selectWcStrategyList(wcStrategy);
        PageInfo pageInfo = new PageInfo(list);
        return ResultBody.success(pageInfo);
    }

    /**
     * 新增攻略记录
     *
     * @param wcStrategy 攻略记录
     * @return 结果
     */
    @Transactional
    @Override
    public ResultBody insertWcStrategy(Strategy wcStrategy)
    {
        strategyMapper.insertWcStrategy(wcStrategy);
        return ResultBody.success("上传成功");
    }

    /**
     * 修改攻略记录
     *
     * @param wcStrategy 攻略记录
     * @return 结果
     */
    @Transactional
    @Override
    public ResultBody updateWcStrategy(Strategy wcStrategy)
    {
        strategyMapper.updateWcStrategy(wcStrategy);
        return ResultBody.success("修改成功");
    }




}
