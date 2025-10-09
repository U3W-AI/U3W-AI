package com.cube.wechat.selfapp.corpchat.api;

import cn.felord.AgentDetails;
import cn.felord.DefaultAgent;
import cn.felord.api.ContactBookManager;
import cn.felord.api.WorkWeChatApi;
import cn.felord.domain.contactbook.department.DeptInfo;
import cn.felord.domain.contactbook.user.SimpleUser;
import cn.hutool.http.HttpRequest;
import com.cube.common.constant.Constants;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cube.common.core.domain.AjaxResult;
import com.cube.common.core.domain.R;
import com.cube.common.core.domain.entity.SysUser;
import com.cube.common.core.domain.model.LoginBody;
import com.cube.common.core.domain.model.LoginUser;
import com.cube.framework.web.service.TokenService;
import com.cube.system.mapper.SysUserMapper;
import com.cube.web.controller.system.SysLoginController;
import com.cube.wechat.selfapp.corpchat.entity.UserInfo;
import com.cube.wechat.selfapp.corpchat.mapper.CorpUserMapper;
import com.cube.wechat.selfapp.corpchat.util.HttpClientUtil;
import com.cube.wechat.selfapp.corpchat.util.ResultBody;
import com.cube.wechat.selfapp.corpchat.util.WeChatApiUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年08月29日 13:25
 */

@RestController
@RequestMapping("/wecom/corp")
@Slf4j
public class CorpUserController {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private WeChatApiUtils weChatApiUtils;

    @Autowired
    private SysUserMapper userMapper;



    @PostMapping(value = "/wechatUserLogin")
    public AjaxResult wechatUserLogin(@RequestBody LoginBody loginBody){
        //企业微信-用户登录
        String auth_code = loginBody.getCode();
        R<Map> userInfoR=null;
        try {
            String accessToken = weChatApiUtils.getPcAccessToken();

            if (accessToken == null) {
                return AjaxResult.error("服务异常，请联系客服处理相关配置问题");
            }

            String getUserInfoUrl = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token="+accessToken+"&code="+auth_code;
            String userInfoResult = HttpUtil.get(getUserInfoUrl);

            Map userInfoMap = JSON.parseObject(userInfoResult, Map.class);
            String userid = MapUtils.getString(userInfoMap, "userid");

            System.out.println("企微用户登录: " + userid);

            if (userid == null || userid.isEmpty()) {
                return AjaxResult.error("请尝试退出程序，重新进入");
            }

            SysUser wxUser = userMapper.selectWxUserByUserId(userid);

            if (wxUser == null) {
                // 使用特定错误码1001表示需要显示客服二维码
                AjaxResult result = AjaxResult.error("抱歉，暂无您的用户信息。添加客服前，请先在浏览器后台个人中心填写手机号，再将手机号连同 " + userid + " 信息一起发给客服，方便为您添加信息，确保正常使用。");
                result.put("code", 1001);
                result.put("userid", userid);
                result.put("needShowQRCode", true);
                return result;
            }

            SysUser user = wxUser;

            //组装token信息
            LoginUser loginUser = new LoginUser();
            loginUser.setUser(user);
            loginUser.setUserId(user.getUserId());

            AjaxResult ajax = AjaxResult.success();
            // 生成令牌
            String token = tokenService.createToken(loginUser);
            ajax.put(Constants.TOKEN, token);

            return ajax;
        } catch (Exception e) {
            log.error("企业微信登录异常: " + e.getMessage());
            return AjaxResult.error("登录异常，请联系客服处理");
        }
    }



}
