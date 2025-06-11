package com.cube.wechat.selfapp.app.mapper;


import com.cube.wechat.selfapp.app.domain.Strategy;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface StrategyMapper {

    List<Map> getStrategyList(Strategy wcStrategy);
    /**
     * 查询攻略记录
     *
     * @param id 攻略记录主键
     * @return 攻略记录
     */
    Map selectWcStrategyById(String id);

    /**
     * 查询攻略记录列表
     *
     * @param wcStrategy 攻略记录
     * @return 攻略记录集合
     */
    List<Map> selectWcStrategyList(Strategy wcStrategy);

    /**
     * 新增攻略记录
     *
     * @param wcStrategy 攻略记录
     * @return 结果
     */
    public int insertWcStrategy(Strategy wcStrategy);

    /**
     * 修改攻略记录
     *
     * @param wcStrategy 攻略记录
     * @return 结果
     */
    public int updateWcStrategy(Strategy wcStrategy);

}

