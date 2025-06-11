package com.cube.wechat.selfapp.app.service;

import com.cube.wechat.selfapp.app.domain.Strategy;
import com.cube.wechat.selfapp.wecom.util.ResultBody;

public interface StrategyService {

    /**
     * 获取攻略列表接口(小程序使用，按照浏览记录排名)
     * */
    ResultBody getStrategyList(Strategy wcStrategy);

    /**
     * 查询攻略记录
     *
     * @param id 攻略记录主键
     * @return 攻略记录
     */
    ResultBody selectWcStrategyById(String id);

    /**
     * 查询攻略记录列表
     *
     * @param wcStrategy 攻略记录
     * @return 攻略记录集合
     */
    ResultBody selectWcStrategyList(Strategy wcStrategy);

    /**
     * 新增攻略记录
     *
     * @param wcStrategy 攻略记录
     * @return 结果
     */
    ResultBody insertWcStrategy(Strategy wcStrategy);

    /**
     * 修改攻略记录
     *
     * @param wcStrategy 攻略记录
     * @return 结果
     */
    ResultBody updateWcStrategy(Strategy wcStrategy);


}
