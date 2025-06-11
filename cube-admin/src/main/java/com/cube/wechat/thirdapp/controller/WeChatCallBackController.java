package com.cube.wechat.thirdapp.controller;

import com.cube.wechat.thirdapp.service.WeChatThirdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * @author AspireLife 企业微信回调
 * @Created date 2024/2/23 16:49
 */

@RestController
@RequestMapping("/WeChatCallBack")
public class WeChatCallBackController {

    Logger logger = LoggerFactory.getLogger(WeChatCallBackController.class);

    @Autowired
    private WeChatThirdService weChatThirdService;


    @ResponseBody
    @GetMapping({"/instruct","/data","/registerx"})
    String instructGet(@RequestParam(value = "msg_signature") String sVerifyMsgSig,
                       @RequestParam(value = "timestamp") String sVerifyTimeStamp,
                       @RequestParam(value = "nonce") String sVerifyNonce,
                       @RequestParam(value = "echostr") String sVerifyEchoStr
    ){

        logger.info("get回调验证开始");
        logger.info(sVerifyMsgSig);
        logger.info(sVerifyTimeStamp);
        logger.info(sVerifyNonce);
        logger.info(sVerifyEchoStr);
        logger.info("get回调验证");

        String result = weChatThirdService.getVerify(sVerifyMsgSig,sVerifyTimeStamp, sVerifyNonce,sVerifyEchoStr);

        return  result;
    }

    @ResponseBody
    @GetMapping({"/loginInstruct"})
    String loginInstructGet(@RequestParam(value = "msg_signature") String sVerifyMsgSig,
                            @RequestParam(value = "timestamp") String sVerifyTimeStamp,
                            @RequestParam(value = "nonce") String sVerifyNonce,
                            @RequestParam(value = "echostr") String sVerifyEchoStr
    ){

        logger.info("登录get回调验证开始");
        logger.info(sVerifyMsgSig);
        logger.info(sVerifyTimeStamp);
        logger.info(sVerifyNonce);
        logger.info(sVerifyEchoStr);
        logger.info("登录get回调验证");

        String result = weChatThirdService.getLoginVerify(sVerifyMsgSig,sVerifyTimeStamp, sVerifyNonce,sVerifyEchoStr);

        return  result;
    }


    @ResponseBody
    @PostMapping({"/loginInstruct",})
    String loginInstructPost(
            @RequestParam(value = "msg_signature") String sVerifyMsgSig,
            @RequestParam(value = "timestamp") String sVerifyTimeStamp,
            @RequestParam(value = "nonce") String sVerifyNonce,
            @RequestBody String body
    ){

        logger.info("登录回调验证开始");

        logger.info("登录POST回调验证");

        String result = weChatThirdService.loginInstructCallback(sVerifyMsgSig,sVerifyTimeStamp,sVerifyNonce,body);

        return  result;
    }


    /**
     * 指令回调
     * @param sVerifyMsgSig
     * @param sVerifyTimeStamp
     * @param sVerifyNonce
     * @param body
     * @return
     */
    @ResponseBody
    @PostMapping("/instruct")
    String instructPost(@RequestParam(value = "msg_signature") String sVerifyMsgSig,
                        @RequestParam(value = "timestamp") String sVerifyTimeStamp,
                        @RequestParam(value = "nonce") String sVerifyNonce,
                        @RequestBody String body
    ){
        logger.info("指令post回调开始");
        logger.info(sVerifyMsgSig);
        logger.info(sVerifyTimeStamp);
        logger.info(sVerifyNonce);
        logger.info(body);
        logger.info("指令post回调");
        //处理回调
        String result = weChatThirdService.instructCallback(sVerifyMsgSig,sVerifyTimeStamp,sVerifyNonce,body);
        return result;
    }

    /**
     * 数据回调
     * @param sVerifyMsgSig
     * @param sVerifyTimeStamp
     * @param sVerifyNonce
     * @param body
     * @return
     */
    @ResponseBody
    @PostMapping("/data")
    String dataPost(@RequestParam(value = "msg_signature") String sVerifyMsgSig,
                    @RequestParam(value = "timestamp") String sVerifyTimeStamp,
                    @RequestParam(value = "nonce") String sVerifyNonce,
                    @RequestBody String body
    ){
        logger.info("数据post回调开始");
        logger.info(sVerifyMsgSig);
        logger.info(sVerifyTimeStamp);
        logger.info(sVerifyNonce);
        logger.info(body);
        logger.info("数据post回调");
        //处理回调
        String result = weChatThirdService.dataCallback(sVerifyMsgSig,sVerifyTimeStamp,sVerifyNonce,body);
        return result;
    }

    @ResponseBody
    @PostMapping("/registerx")
    String registerPost(@RequestParam(value = "msg_signature") String sVerifyMsgSig,
                        @RequestParam(value = "timestamp") String sVerifyTimeStamp,
                        @RequestParam(value = "nonce") String sVerifyNonce,
                        @RequestBody String body
    ){
        logger.info("通用开发参数post回调开始");
        logger.info(sVerifyMsgSig);
        logger.info(sVerifyTimeStamp);
        logger.info(sVerifyNonce);
        logger.info(body);
        logger.info("通用开发参数post回调");
        //处理回调
        weChatThirdService.registerCallback(sVerifyMsgSig,sVerifyTimeStamp,sVerifyNonce,body);
        return "success";
    }

    /**
     * 系统事件验证
     * @param sVerifyMsgSig
     * @param sVerifyTimeStamp
     * @param sVerifyNonce
     * @param sVerifyEchoStr
     * @return
     */
    @ResponseBody
    @GetMapping({"eventCallbacks"})
    String eventCallbacks(@RequestParam(value = "msg_signature") String sVerifyMsgSig,
                          @RequestParam(value = "timestamp") String sVerifyTimeStamp,
                          @RequestParam(value = "nonce") String sVerifyNonce,
                          @RequestParam(value = "echostr") String sVerifyEchoStr
    ){

        logger.info("get回调验证开始");
        logger.info(sVerifyMsgSig);
        logger.info(sVerifyTimeStamp);
        logger.info(sVerifyNonce);
        logger.info(sVerifyEchoStr);
        logger.info("get回调验证");

        String result = weChatThirdService.systemEventCallbacksVerify(sVerifyMsgSig,sVerifyTimeStamp, sVerifyNonce,sVerifyEchoStr);

        return  result;
    }


    /**
     * 系统事件回调
     * @param sVerifyMsgSig
     * @param sVerifyTimeStamp
     * @param sVerifyNonce
     * @param body
     * @return
     */

    @ResponseBody
    @PostMapping("/eventCallbacks")
    String jobPost(@RequestParam(value = "msg_signature") String sVerifyMsgSig,
                   @RequestParam(value = "timestamp") String sVerifyTimeStamp,
                   @RequestParam(value = "nonce") String sVerifyNonce,
                   @RequestBody String body
    ){
        logger.info(sVerifyMsgSig);
        logger.info(sVerifyTimeStamp);
        logger.info(sVerifyNonce);
        logger.info(body);
        logger.info("系统事件回调post回调");
        //处理回调
        weChatThirdService.eventCallbacks(sVerifyMsgSig,sVerifyTimeStamp,sVerifyNonce,body);
        return "success";
    }

}
