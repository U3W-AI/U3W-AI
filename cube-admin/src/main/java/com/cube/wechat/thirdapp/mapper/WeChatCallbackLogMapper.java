package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.WeChatCallbackLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 张云龙
 */

@Mapper
public interface WeChatCallbackLogMapper {

    int insertSelective(WeChatCallbackLog weChatCallbackLog);
}
