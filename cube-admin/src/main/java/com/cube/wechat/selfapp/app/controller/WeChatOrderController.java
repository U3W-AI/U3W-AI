package com.cube.wechat.selfapp.app.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cube.wechat.selfapp.app.service.WeChatOrderService;
import com.cube.wechat.thirdapp.aes.AesException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.apache.commons.codec.binary.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;

import static com.cube.wechat.selfapp.wecom.util.AESUtil.recoverNetworkBytesOrder;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年12月10日 14:00
 */
@RestController
@RequestMapping("/mini")
public class WeChatOrderController {
    String encodingAesKey = "hISOqcp3NqfcJfvAhmMRWCDk2kq31qPcgwR34A8v2HB";
    static Charset CHARSET = Charset.forName("utf-8");

    @Autowired
    private WeChatOrderService weChatOrderService;
    /***
     * 微信服务器触发get请求用于检测签名
     * @return
     */
    @GetMapping("/WxCheckOrderSignature")
    public String handleWxCheckOrderSignature(HttpServletRequest request){


        //todo 严格来说这里需要做签名验证,我这里为了方便就不做了
        String echostr = request.getParameter("echostr");

        return echostr;

    }
    /**
     * 接收微信推送事件
     * @return
     */
    @PostMapping("/WxCheckOrderSignature")
    public String handleWxOrderEvent(@RequestBody Map map){
        try {
            JSONObject jsonObject = JSON.parseObject(decrypt(map.get("Encrypt")+""));
//            JSONObject jsonObject = new JSONObject();
//            jsonObject.put("CreateTime",1735021738);
//            jsonObject.put("Event","channels_ec_order_deliver");
//            jsonObject.put("ToUserName","gh_d4f2d3a85d2f");
//            jsonObject.put("FromUserName","oj42r4vpOkMG7PKCNuHXTkN8c9xg");
//            jsonObject.put("MsgType","oj42r4vpOkMG7PKCNuHXTkN8c9xg");
//
//            JSONObject orderInfo = new JSONObject();
//            orderInfo.put("finish_delivery",1);
//            orderInfo.put("order_id","3724911935874800128");
//            jsonObject.put("order_info",orderInfo);

            System.out.println(jsonObject.toString());
            weChatOrderService.handleOrder(jsonObject);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "success";

    }



    /**
     * 对密文进行解密.
     *
     * @param text 需要解密的密文
     * @return 解密得到的明文
     * @throws AesException aes解密失败
     */
    String decrypt(String text) throws AesException {
        byte[] original;
        byte[]  aesKey = Base64.decodeBase64(encodingAesKey + "=");
        try {
            // 设置解密模式为AES的CBC模式
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec key_spec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
            cipher.init(Cipher.DECRYPT_MODE, key_spec, iv);

            // 使用BASE64对密文进行解码
            byte[] encrypted = Base64.decodeBase64(text);

            // 解密
            original = cipher.doFinal(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
            throw new AesException(AesException.DecryptAESError);
        }

        String xmlContent, from_appid;
        try {
            // 去除补位字符
            byte[] bytes = PKCS7EncoderP.decode(original);

            // 分离16位随机字符串,网络字节序和AppId
            byte[] networkOrder = Arrays.copyOfRange(bytes, 16, 20);

            int xmlLength = recoverNetworkBytesOrder(networkOrder);

            xmlContent = new String(Arrays.copyOfRange(bytes, 20, 20 + xmlLength), CHARSET);
            from_appid = new String(Arrays.copyOfRange(bytes, 20 + xmlLength, bytes.length),
                    CHARSET);
        } catch (Exception e) {
            e.printStackTrace();
            throw new AesException(AesException.IllegalBuffer);
        }

        return xmlContent;

    }
}
