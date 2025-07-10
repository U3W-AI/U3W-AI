package com.cube.wechat.selfapp.app.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.cube.common.core.domain.entity.SysUser;
import com.cube.common.core.domain.model.LoginUser;
import com.cube.common.utils.DateUtils;
import com.cube.framework.web.service.TokenService;
import com.cube.point.controller.PointsSystem;
import com.cube.system.mapper.SysUserMapper;
import com.cube.wechat.selfapp.app.domain.WxLoginBody;
import com.cube.wechat.selfapp.app.service.AppLoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月03日 11:00
 */
@Service
public class AppLoginServiceImpl implements AppLoginService {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private PointsSystem pointsSystem;

    @Override
    public String wxLogin(String decryptResult, WxLoginBody wxLoginBody) {
        //字符串转json
        JSONObject jsonObject = JSONObject.parseObject(decryptResult);
        //        String unionid = jsonObject.getString("unionid");
        String openId = jsonObject.getString("openId");
        String unionId = jsonObject.getString("unionId");
        if(unionId == null || unionId == ""){
            unionId = openId;
        }
        String nickName = wxLoginBody.getNickName();
        //获取nickName
         nickName =nickName + getStringRandom(6);// 生成16位随机昵称
        //获取头像
        String avatarUrl = wxLoginBody.getAvatar();
//        String avatarUrl ="";
        //根据openid查询用户信息
        SysUser wxUser = userMapper.selectWxUserByOpenId(openId,unionId);
//
//        //如果查不到，则新增，查到了，则更新
        SysUser user = new SysUser();
        if (wxUser == null) {
            // 新增
            user.setUserName(unionId);// 生成16位随机用户名
            user.setNickName(nickName);
            user.setAvatar("chatfile/avatar/head.png");
            user.setOpenId(openId);
            user.setUnionId(unionId);
            user.setCreateTime(DateUtils.getNowDate());
            user.setCreateBy("优立方小程序用户");
            user.setPoints(200);
            //新增 用户
            userMapper.insertUser(user);
            SysUser newUser = userMapper.selectWxUserByOpenId(openId,unionId);
            userMapper.saveUserRole(newUser.getUserId());
            pointsSystem.registerAccount(newUser.getUserId());
            pointsSystem.setUserPoint(String.valueOf(newUser.getUserId()),"首次登录",null,"0x2edc4228a84d672affe8a594033cb84a029bcafc","f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
        }else {
            //更新
            user = wxUser;
            user.setUserName(unionId);// 生成16位随机用户名
            user.setUpdateTime(DateUtils.getNowDate());
            Integer isFirst = pointsSystem.checkPointIsOk("每日优立方登录",String.valueOf(wxUser.getUserId()),1);
            if(isFirst==0){
                pointsSystem.setUserPoint(String.valueOf(wxUser.getUserId()),"每日优立方登录",null,"0x2edc4228a84d672affe8a594033cb84a029bcafc","f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
            }

            userMapper.updateUser(user);
        }
//
        //组装token信息
        LoginUser loginUser = new LoginUser();
        loginUser.setOpenId(openId);
        //如果有的话设置
        loginUser.setUser(user);
        loginUser.setUserId(user.getUserId());

        // 生成token
        return tokenService.createToken(loginUser);
    }


    @Override
    public String qywxLogin(String qwId,String openId,String unionId,String corpId) {
        //字符串转json
        String nickName = "企微用户";
        //获取nickName
         nickName =nickName + getStringRandom(7);// 生成16位随机昵称
        //获取头像
        String avatarUrl ="";
        //根据openid查询用户信息
        SysUser wxUser = userMapper.selectWxUserByOpenId(openId,unionId);
//
//        //如果查不到，则新增，查到了，则更新
        SysUser user = new SysUser();
        if (wxUser == null) {
            // 新增
            user.setUserName(unionId);// 生成16位随机用户名
            user.setNickName(nickName);
            user.setAvatar("chatfile/avatar/head.png");
            user.setOpenId(openId);
            user.setQwId(qwId);
            user.setUnionId(unionId);
//            user.setCorpId(corpId);
            user.setCreateTime(DateUtils.getNowDate());
            user.setCreateBy("企业微信小程序用户");
            user.setPoints(200);
            //新增 用户
            userMapper.insertUser(user);
            SysUser newUser = userMapper.selectWxUserByOpenId(openId,unionId);
            userMapper.saveUserRole(newUser.getUserId());
            pointsSystem.registerAccount(newUser.getUserId());
            pointsSystem.setUserPoint(String.valueOf(newUser.getUserId()),"首次登录",null,"0x2edc4228a84d672affe8a594033cb84a029bcafc","f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
        }else {
            //更新
            user = wxUser;
            user.setQwId(qwId);
            user.setUnionId(unionId);
//            user.setCorpId(corpId);
            user.setUpdateTime(DateUtils.getNowDate());
            Integer isFirst = pointsSystem.checkPointIsOk("每日优立方登录",String.valueOf(wxUser.getUserId()),1);
            if(isFirst==0){
                pointsSystem.setUserPoint(String.valueOf(wxUser.getUserId()),"每日优立方登录",null,"0x2edc4228a84d672affe8a594033cb84a029bcafc","f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
            }
            userMapper.updateUser(user);
        }
//
        //组装token信息
        LoginUser loginUser = new LoginUser();
        loginUser.setOpenId(openId);
        //如果有的话设置
        loginUser.setUser(user);
        loginUser.setUserId(user.getUserId());

        // 生成token
        return tokenService.createToken(loginUser);
    }


    @Override
    public String officeLogin(String unionId,String openId) {
        String nickName = "优立方用户";
        //获取nickName
        nickName =nickName + getStringRandom(7);// 生成16位随机昵称
        //字符串转json
        SysUser wxUser = userMapper.selectWxUserByUnionId(unionId);
//        //如果查不到，则新增，查到了，则更新
        SysUser user = new SysUser();
        if (wxUser == null) {
            // 新增
            user.setUserName(unionId);// 生成16位随机用户名
            user.setNickName(nickName);
            user.setAvatar("chatfile/avatar/head.png");
            user.setOpenId(openId);
            user.setUnionId(unionId);
            user.setCreateTime(DateUtils.getNowDate());
            user.setCreateBy("优立方用户");
            user.setPoints(200);
            //新增 用户
            userMapper.insertUser(user);
            SysUser newUser = userMapper.selectWxUserByOpenId(openId,unionId);
            userMapper.saveUserRole(newUser.getUserId());
            pointsSystem.registerAccount(newUser.getUserId());
            pointsSystem.setUserPoint(String.valueOf(newUser.getUserId()),"首次登录",null,"0x2edc4228a84d672affe8a594033cb84a029bcafc","f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
        }else{
            //更新
            user = wxUser;
            user.setUpdateTime(DateUtils.getNowDate());
            Integer isFirst = pointsSystem.checkPointIsOk("每日优立方登录",String.valueOf(wxUser.getUserId()),1);
            if(isFirst==0){
                pointsSystem.setUserPoint(String.valueOf(wxUser.getUserId()),"每日优立方登录",null,"0x2edc4228a84d672affe8a594033cb84a029bcafc","f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
            }
            userMapper.updateUser(user);
        }
        //组装token信息
        LoginUser loginUser = new LoginUser();
        loginUser.setOpenId(wxUser.getOpenId());
        //如果有的话设置
        loginUser.setUser(user);
        loginUser.setUserId(user.getUserId());
        // 生成token
        return tokenService.createToken(loginUser);
    }

    public static String getStringRandom(int length) {
        StringBuilder val = new StringBuilder();
        Random random = new Random();

        // 参数length，表示生成几位随机数
        for (int i = 0; i < length; i++) {
            val.append(random.nextInt(10));
        }
        return val.toString();
    }


}
