package com.cube.wechat.thirdapp.service.impl;

import com.cube.wechat.thirdapp.mapper.WeChatCallbackLogMapper;
import com.cube.wechat.thirdapp.entiy.WeChatCallbackLog;
import com.cube.wechat.thirdapp.service.WeChatCallbackLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author 张云龙 (ylzhang@wangsheng.com)
 */
@Service
public class WeChatCallbackLogServiceImpl implements WeChatCallbackLogService {

    @Autowired
    private WeChatCallbackLogMapper weChatCallbackLogMapper;

    @Override
    public void saveCallBackLog(WeChatCallbackLog weChatCallbackLog) {
        weChatCallbackLogMapper.insertSelective(weChatCallbackLog);
    }
}
