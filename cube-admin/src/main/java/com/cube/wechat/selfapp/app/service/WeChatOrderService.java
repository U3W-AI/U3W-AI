package com.cube.wechat.selfapp.app.service;

import com.alibaba.fastjson.JSONObject;

public interface WeChatOrderService {

    public void handleOrder(JSONObject jsonObject);
}
