package com.cube.wechat.selfapp.app.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cube.common.core.domain.entity.SysUser;
import com.cube.common.utils.DateUtils;
import com.cube.point.controller.PointsSystem;
import com.cube.system.mapper.SysUserMapper;
import com.cube.wechat.selfapp.app.mapper.WeChatOrderMapper;
import com.cube.wechat.selfapp.app.service.WeChatOrderService;
import com.cube.wechat.selfapp.app.util.RestUtils;
import com.cube.wechat.selfapp.app.util.SendSmsUtil;
import com.cube.wechat.selfapp.wecom.util.WeChatApiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年12月24日 14:33
 */
@Service
public class WeChatOrderServiceImpl  implements WeChatOrderService {

    @Autowired
    private WeChatOrderMapper weChatOrderMapper;

    @Autowired
    private AppLoginServiceImpl sysLoginService;

    @Autowired
    private WeChatApiUtils weChatApiUtils;


    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private PointsSystem pointsSystem;


    @Override
    public void handleOrder(JSONObject jsonObject) {
       if(jsonObject != null && jsonObject.get("order_info") != null){
           Map orderMap = (Map) jsonObject.get("order_info");

           if(orderMap != null && orderMap.get("finish_delivery") !=null && orderMap.get("finish_delivery").equals(1)){


               String orderId = orderMap.get("order_id")+"";

               String accessToken = weChatApiUtils.getOrderAccessToken();

               String getOrderUrl = "https://api.weixin.qq.com/channels/ec/order/get?access_token="+accessToken;
               JSONObject orderParam = new JSONObject();
               orderParam.put("order_id", orderId);
               orderParam.put("encode_sensitive_info", false);
               JSONObject orderObject = RestUtils.post(getOrderUrl, orderParam);
               System.out.println("订单数据："+orderObject.toString());
               if(orderObject != null && orderObject.get("errcode").equals(0)) {
                    Map orderInfo = (Map) orderObject.get("order");
                    if(orderInfo != null && orderInfo.get("status").equals(30)){
                        Map orderDetail = (Map) orderInfo.get("order_detail");
                        String json = JSON.toJSONString(orderDetail.get("product_infos"));
                        Map deliveryInfo = (Map) orderDetail.get("delivery_info");
                        Map addressInfo = (Map) deliveryInfo.get("address_info");
                        String phone = addressInfo.get("virtual_order_tel_number")+"";
                        Map resMap = new HashMap();
                        resMap.put("order_id",orderId);
                        resMap.put("openid",orderInfo.get("openid"));
                        resMap.put("unionid",orderInfo.get("unionid"));
                        resMap.put("status",orderInfo.get("status"));
                        resMap.put("create_time",orderInfo.get("create_time"));
                        resMap.put("update_time",orderInfo.get("update_time"));
                        resMap.put("product_infos",json);
                        resMap.put("phone",phone);

                        String unionId = orderInfo.get("unionid")+"";
                        String openId = orderInfo.get("openid")+"";
                        //自动注册账号
                        SysUser wxUser = userMapper.selectWxUserByOpenId(openId,unionId);

                        SysUser user = new SysUser();
                        if (wxUser == null) {
                            // 新增
                            user.setUserName(unionId);// 生成16位随机用户名
                            user.setNickName("福帮手用户");
                            user.setAvatar("");
                            user.setOpenId(openId);
                            user.setUnionId(unionId);
                            user.setCreateTime(DateUtils.getNowDate());
                            user.setCreateBy("福帮手用户");
                            user.setPoints(0);
                            //新增 用户
                            userMapper.insertUser(user);
                            SysUser newUser = userMapper.selectWxUserByOpenId(openId,unionId);
                            userMapper.saveUserRole(newUser.getUserId());
                            pointsSystem.registerAccount(newUser.getUserId());
                            pointsSystem.setUserPoint(String.valueOf(newUser.getUserId()),"首次登录",null,"0x3f4413a0e863903147172b1e7672d7a23025e084","824af41abf2ca18335f5547ae293a4e250ed7e80a78f985fd01d551e0a0d3552");
                        }else{
                            //更新
                            user = wxUser;
                            user.setUpdateTime(DateUtils.getNowDate());
                            userMapper.updateUser(user);
                        }



                        //核销订单
                        wxUser = userMapper.selectWxUserByOpenId(openId,unionId);
                        List<Map> list = (List<Map>) orderDetail.get("product_infos");
                        for (Map map : list) {
                            pointsSystem.setUserPoint(String.valueOf(wxUser.getUserId()),map.get("title")+"",null,"0x3f4413a0e863903147172b1e7672d7a23025e084","824af41abf2ca18335f5547ae293a4e250ed7e80a78f985fd01d551e0a0d3552");
                        }
                        //生成小程序专属链接
//                        String uniAccessToken = weChatApiUtils.getOfficeAccessToken("wx9812ae7b613f2e49","f9be6c7aa2bf0ea7d0d3edfe7abd7280");
//                        String url = "https://api.weixin.qq.com/wxa/generate_urllink?access_token="+uniAccessToken;
//                        JSONObject urlParam = new JSONObject();
//                        urlParam.put("path","pages/mine/index");
//                        urlParam.put("query","orderId="+orderId+"&unionid="+orderInfo.get("unionid"));
//                        urlParam.put("env_version","develop");
//                        JSONObject urlObject = RestUtils.post(url, urlParam);
//                        System.out.println("专属链接："+urlObject.toString());
//                        String urlLink = urlObject.get("url_link")+"";
//                        resMap.put("urlLink",urlLink);




                        weChatOrderMapper.saveOrderDetail(resMap);



                    }

               }

           }

       }

    }

    public static void main(String[] args) {
        //发送短信
        SendSmsUtil.SendSmsRequest request = new SendSmsUtil.SendSmsRequest();
        request.setPhone("16637172129");
        // 这个值，要看你的模板中是否预留了占位符，如果没有则不需要设置
        SendSmsUtil.sendSms(request);
    }
}
