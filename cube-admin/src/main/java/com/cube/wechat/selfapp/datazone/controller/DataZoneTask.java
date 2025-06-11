package com.cube.wechat.selfapp.datazone.controller;

import cn.felord.api.WorkWeChatApi;
import cn.felord.domain.WeComResponse;
import cn.felord.domain.webhook.WebhookBody;
import cn.felord.domain.webhook.WebhookMarkdownBody;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cube.wechat.selfapp.app.mapper.MessageMapper;
import com.cube.wechat.selfapp.app.util.RestUtils;
import com.cube.wechat.selfapp.wecom.util.RedisUtil;
import com.cube.wechat.selfapp.wecom.util.WeChatApiUtils;
import com.tencent.wework.RSAEncrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年10月09日 08:44
 */
@Component
@RestController
@RequestMapping("/datazone")
public class DataZoneTask {

    @Value("${datazone.url}")
    private String dataZoneUrl;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private WeChatApiUtils weChatApiUtils;

    @Autowired
    private MessageMapper messageMapper;

    private String priKey="-----BEGIN RSA PRIVATE KEY-----\n" +
            "MIIEogIBAAKCAQEAh7d+6W5LEvds20hnE4beq1HYH8wAM/qz8KDiSfPdBVx6Jpe0\n" +
            "83Z7ieYY3q6ItSCaCFCL+8M6rdsAZvSVpMuwdlXgZu9Tbm1vvxJsvCEuuZp0WaOz\n" +
            "gNm2nuhJZ7o4y9goD23xnWJufXEBCMySsor7F8TyynmmnSvtokptpklsHqsix3ud\n" +
            "cUnxmzAH+aogzgbnhD745AoIbjarBd3wBI5hEQilmtwUU3Fr8kVqUJ8M6st/leFt\n" +
            "qvYs97jYNj7WKg9apj9TcGo4s/Gyi4eMgvQ8ZkgSCUj6fIZ9qU4746+tIgCRw5OQ\n" +
            "nb20ta5p6E48vt6eBEGYPNzI69v6gGzud99GZQIDAQABAoIBAAL7Q0C+EUymnl/X\n" +
            "4JnTd+9UEjcqnGOH8a2K30XII3YjcLSJ1yoVE4Q1R50WwP6Xq4KcwGKEyLR6j/Dz\n" +
            "FRmEdwk2fEJOpirSIScVsMlWQkhGDiHNAJvHTKWDjV9HvkkuI70pCWqPd8VuNtta\n" +
            "YSumdXsxcrMDhqdDyIns8Ck7yjIHQFmtesU7ctvqddm6sAJmETfi0cFxa7dR4Ot9\n" +
            "2uHIjJ9kx3zcVy/lqb9SRpZSCgon0AdcM4hxZW6tXiOGbRtDpcGiDXxHwLVW4bo5\n" +
            "t2jkKHOug+7EdBBcs2Eusf3ZGbzYVVI7z6bdIkEoeJGHPNfQ7LUj7r0bof48C6ty\n" +
            "C9nnPYkCgYEAiFiBYNS42oxzERT0HCb8HHmQfr5VjelkeTlisxnFngIrQepXfhwI\n" +
            "qZTrYGCJNVdgkRBF018gtIY/a5VPmn359gps56rTxwCRORydKzs28lkY/nX4mPSY\n" +
            "1v7ePjjFfIRpgvmNeRIxBclpBsDENz6K8JwjTOU7nQNUGV1FhdQBKP0CgYEA/tGx\n" +
            "CIU8IDHBDRX1Dak06bjWov8iM+LSECCJN+nzrMw0Ob8KM5RL4lQZ/9/h26F9qGPf\n" +
            "RyUg7Y/IIysUjKy0hy/hsQN3mKs54MP2Zwo0oHNXPapzCgmXufqSRurGs6uUczHX\n" +
            "K71fw/xd+TmfBppfaJVNH7o/UHi6CdtqCMQI44kCgYA2RbyiNaqrW/LFnuiYeDAs\n" +
            "iXsp6EuX5IpY8q3GCwEtp0FeyJAxI6mTDzMuNt8G+5P1yltxCtGy6ik+gr2gCntA\n" +
            "I+A7yzTnZuNnr2skdTqm9y5Kw9zDzcE0+1itvd1mdjKlrv5QbhxTaFvFE2BHeT7H\n" +
            "De/DQRAcrOGCAy2UWtJnZQKBgFsQm0DdRJCI12ISz8GzD7rbGLGllhaO391tkzxN\n" +
            "Oo0taRieAkpOnBPlVGlSHEg+XUbZckjdpvffI3oWAkEH03hgjzqQb6Q6xPNjdOJ8\n" +
            "DjStI6dhC72xkeyf9LitXJeHIQVN8YSrJ9dFkFvp0MAuWRxqBuboy4m5q1qsdCdv\n" +
            "z3FpAoGAfrtN7Zu0gJu/H5vw24yRILgn+msViB4wkY50z8fqP+Nm5PfiQXokpj7P\n" +
            "EuPAZCU2SqEqhUsGMoOMoo71fj4OHKe5cAbslHGuETNoRUHzC1LO18dAiL0JDoMM\n" +
            "H69btYCloO1sYwlyPkWB8aNB86mAqzCxCSKh5fTP26evE/LKvEY=\n" +
            "-----END RSA PRIVATE KEY-----";



//    @Scheduled(cron = "0/10 * * * * ?")
    @GetMapping("/pullHitMsgList")
    public WeComResponse pullHitMsgList() throws Exception {

        System.out.println("=========开始拉取命中关键词消息=========");
        JSONObject jsonObject = new JSONObject();

        Object nextCursor = redisUtil.get("qkx_zc_next_cursor");
        JSONObject request = new JSONObject();
        if(nextCursor != null ){
            request.put("cursor",nextCursor);
        }
        request.put("need_detail",1);

        jsonObject.put("program_id", "progYLdqiLu-_ce_XX6yOD_Rbsf6vrvNCySq");
        jsonObject.put("ability_id", "invoke_get_hit_msg_list");
        jsonObject.put("request_data", request.toJSONString());

        Map resultMap = RestUtils.post(dataZoneUrl+weChatApiUtils.getAccessToken(), jsonObject);

        Map<String, Object> responseData = JSON.parseObject((String) resultMap.get("response_data"), Map.class);

        nextCursor = responseData.get("next_cursor");
        if(nextCursor !=null){
            redisUtil.set("qkx_zc_next_cursor",nextCursor);
        }

        List<Map> msgList = (List<Map>) responseData.get("msg_list");
        System.out.println("拉取结果："+msgList.size());

        for (Map map : msgList) {
            String msgId = (String) map.get("msgid");

            Map msgDetail = (Map) map.get("msg_detail");

            Integer msgType = Integer.parseInt(msgDetail.get("msgtype")+"");
            if(msgDetail.get("chatid")!=null&&msgDetail.get("chatid").equals("wrypNhRQAAgM_LxM1ObMMG9qSpEIKRkA")&&msgType == 1 ){
                //以下注释内容为扩展能力，通过会话摘要模型提取具体关键词，可根据需要使用
                Map serviceEncryptInfo = (Map) msgDetail.get("service_encrypt_info");
                String encryptedSecretKey = (String) serviceEncryptInfo.get("encrypted_secret_key");
                String  encryptKey = RSAEncrypt.decryptRSA(encryptedSecretKey,priKey);


                JSONObject summaryJsonObject = new JSONObject();
                summaryJsonObject.put("program_id", "progYLdqiLu-_ce_XX6yOD_Rbsf6vrvNCySq");
                summaryJsonObject.put("ability_id", "invoke_create_ww_model_task");

                JSONObject summaryReq = new JSONObject();

                Map msgMap = new HashMap();
                msgMap.put("msgid",msgId);
                Map secretKeyMap = new HashMap();
                secretKeyMap.put("secret_key",encryptKey);
                msgMap.put("encrypt_info",secretKeyMap);

                List<Map> msgs = new ArrayList<>();
                msgs.add(msgMap);

                summaryReq.put("msg_list",msgs);
                summaryReq.put("kb_id","kbZ8cI2YXZb5DQy55Dk1uLxJBo2YWcaVSt");
                summaryJsonObject.put("request_data", summaryReq.toJSONString());

                Map summaryMap = RestUtils.post(dataZoneUrl+weChatApiUtils.getAccessToken(), summaryJsonObject);

                Map<String, Object> summaryRes = JSON.parseObject((String) summaryMap.get("response_data"), Map.class);
                String jobId = (String) summaryRes.get("jobid");

                JSONObject jobJson = new JSONObject();
                jobJson.put("program_id", "progYLdqiLu-_ce_XX6yOD_Rbsf6vrvNCySq");
                jobJson.put("ability_id", "invoke_get_ww_model_result");

                JSONObject jobReq = new JSONObject();
                jobReq.put("jobid",jobId);
                jobJson.put("request_data", jobReq.toJSONString());

                System.out.println("任务ID:"+jobId);
                Thread.sleep(10000);
                Map tabMap = RestUtils.post(dataZoneUrl+weChatApiUtils.getAccessToken(), jobJson);

                Map<String, Object> textData = JSON.parseObject((String) tabMap.get("response_data"), Map.class);
                  if(textData.get("response_data")!=null){
                      WebhookBody markdownBody = WebhookMarkdownBody.from(textData.get("response_data")+"\n\n（内容基于通用模型生成，仅供参考）");
                      WeComResponse weComResponse = WorkWeChatApi.webhookApi().send("6de448fe-fc27-4c43-b9a5-2a33440de235", markdownBody);
                      return weComResponse;
                  }

            }

        }

        JSONObject jsonObjectZc = new JSONObject();

        Object nextCursorZc = redisUtil.get("cy_zc_next_cursor");
        JSONObject requestZc = new JSONObject();
        requestZc.put("limit",5);
        if(nextCursorZc!=null){
            requestZc.put("cursor",nextCursorZc);
        }
        jsonObjectZc.put("program_id", "progYLdqiLu-_ce_XX6yOD_Rbsf6vrvNCySq");
        jsonObjectZc.put("ability_id", "invoke_sync_msg");
        jsonObjectZc.put("request_data", requestZc.toJSONString());
        Map resultMapZc = RestUtils.post(dataZoneUrl+weChatApiUtils.getAccessToken(), jsonObjectZc);
        Map<String, Object> responseDataZc = JSON.parseObject((String) resultMapZc.get("response_data"), Map.class);
        Integer has_more = (Integer) responseDataZc.get("has_more");
        nextCursorZc = responseDataZc.get("next_cursor");
        if(nextCursorZc !=null){
            redisUtil.set("cy_zc_next_cursor",nextCursorZc);
        }
        List<Map> msgListZc = (List<Map>) responseDataZc.get("msg_list");
        for (Map map : msgListZc) {
            // 循环去调用专区模型，打标签
            Integer sendTime = (Integer) map.get("send_time")-1;
            Integer msgtype = (Integer) map.get("msgtype");
            if(msgtype ==1){
                String msgId = (String) map.get("msgid");

                Map senderData = (Map) map.get("sender");
                Map serviceEncryptInfo = (Map) map.get("service_encrypt_info");
                String encryptedSecretKey = (String) serviceEncryptInfo.get("encrypted_secret_key");
                String  encrypt_key = RSAEncrypt.decryptRSA(encryptedSecretKey,priKey);

                String formId = (String) senderData.get("id");

                if(!formId.equals("woypNhRQAAkh5rzs0xABFQjCkgObFGiw") && !formId.equals("DuRuiNing")){
                    //内部员工群发消息不参与
                    //  String msgData = messageMapper.getMsgText(str,formId);
                    System.out.println("更新时间："+sendTime);
                    System.out.println("发送人："+formId);
                    int num = messageMapper.updateMsg(sendTime,formId,msgId,encrypt_key);
                }
            }

        }



        return null;
    }

}
