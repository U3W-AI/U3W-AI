package com.cube.wechat.selfapp.app.controller;

import com.alibaba.fastjson.JSONObject;
import com.cube.common.core.controller.BaseController;
import com.cube.wechat.selfapp.app.domain.AIParam;
import com.cube.wechat.selfapp.app.domain.Strategy;
import com.cube.wechat.selfapp.app.service.StrategyService;
import com.cube.wechat.selfapp.app.util.AssistantUtil;
import com.cube.wechat.selfapp.wecom.util.ResultBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author keke
 * @version JDK 1.8
 * @date 2024年11月12日
 */

@RestController
@RequestMapping("/mini")
public class StrategyController extends BaseController {


    @Autowired
    private StrategyService strategyService;

    // 获取攻略列表接口(小程序使用，按照浏览记录排名)
    @GetMapping("/getStrategyList")
    public ResultBody getStrategyList(Strategy wcStrategy){
        return strategyService.getStrategyList(wcStrategy);
    };

    /**
     * 查询攻略记录列表
     */
    @GetMapping("/strategy/list")
    public ResultBody list(Strategy wcStrategy)
    {
        return strategyService.selectWcStrategyList(wcStrategy);
    }

    /**
     * 获取攻略记录详细信息
     */
    @GetMapping(value = "/getStrategyDetail")
    public ResultBody getInfo(String id)
    {
        return strategyService.selectWcStrategyById(id);
    }

    /**
     * 新增攻略记录
     */
    @PostMapping("/addStrategy")
    public ResultBody add(@RequestBody Strategy wcStrategy)
    {
        return strategyService.insertWcStrategy(wcStrategy);
    }

    /**
     * 修改攻略记录
     */
    @PutMapping("/editStrategy")
    public ResultBody edit(@RequestBody Strategy wcStrategy)
    {
        return strategyService.updateWcStrategy(wcStrategy);
    }

    /**
     * 修改攻略记录
     */
    @PutMapping("/test")
    public ResultBody edits(@RequestBody Strategy wcStrategy)
    {
        return strategyService.updateWcStrategy(wcStrategy);
    }

    @PostMapping("/genStrategy")
    public ResultBody genStrategy(@RequestBody AIParam aiParam) throws Exception {
        String res = AssistantUtil.callApi(aiParam.getUserPrompt(),"A6cOZVSv1EUa","pwzRNQCwhffWXLglQEjn3FHOTzW2DGE7");
        JSONObject jsonObject = JSONObject.parseObject(res);
        Map resMap = new HashMap();
        resMap.put("author","许都之行");
        resMap.put("strategyTitle",jsonObject.get("title"));
        resMap.put("strategyContent",jsonObject.get("content"));
        resMap.put("desc",jsonObject.get("desc"));
        resMap.put("tag",jsonObject.get("tagList"));
        return ResultBody.success(resMap);
    }

}

