package com.cube.wechat.selfapp.app.job;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cube.wechat.selfapp.app.mapper.MessageMapper;
import com.cube.wechat.selfapp.app.util.HunYuanApiUtil;
import com.cube.wechat.selfapp.app.util.RestUtils;
import com.cube.wechat.selfapp.wecom.util.RedisUtil;
import com.cube.wechat.selfapp.wecom.util.WeChatApiUtils;
import com.tencent.wework.RSAEncrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月18日 15:13
 */
@Component
@RestController
public class SyncDataZoneMsg {


    @Autowired
    private WeChatApiUtils weChatApiUtils;

    @Autowired
    private  RedisUtil redisUtil;

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

    // 拉取智能专区消息
    //@Scheduled(cron = "0/10 * * * * ?")
    public void getDataList() throws Exception {

        String url = "https://qyapi.weixin.qq.com/cgi-bin/chatdata/sync_call_program?access_token="+weChatApiUtils.getAccessToken();
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
        Map resultMapZc = RestUtils.post(url, jsonObjectZc);
        Map<String, Object> responseDataZc = JSON.parseObject((String) resultMapZc.get("response_data"), Map.class);
        Integer has_more = (Integer) responseDataZc.get("has_more");
        nextCursorZc = responseDataZc.get("next_cursor");
        if(nextCursorZc !=null){
            redisUtil.set("cy_zc_next_cursor",nextCursorZc);
        }
        List<Map> msgListZc = (List<Map>) responseDataZc.get("msg_list");
        System.out.println("已拉取到消息："+msgListZc.size()+"条");
        System.out.println("是否还有："+has_more);
        System.out.println("下次拉取凭证："+nextCursorZc);
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
                     int num = messageMapper.updateMsg(sendTime,formId,msgId,encrypt_key);
                     if(num>0){
                         System.out.println("更新成功："+msgId);
                     }
                }else{
                    System.out.println("内部员工");
                }
            }



        }
    }


    @GetMapping("/mini/initUserTagByMsg")
    public void initUserTagByMsg() throws Exception {

        String url = "https://qyapi.weixin.qq.com/cgi-bin/chatdata/sync_call_program?access_token="+weChatApiUtils.getAccessToken();

        List<Map> msgList = messageMapper.getMsgData();
//        List<Map> groupIds = messageMapper.getGroupIds();
//        JSONObject jsonObject = new JSONObject();
//        jsonObject.put("program_id", "progYLdqiLu-_ce_XX6yOD_Rbsf6vrvNCySq");
//        jsonObject.put("ability_id", "invoke_create_customer_tag_task");
//        JSONObject request = new JSONObject();
//        request.put("tag_group_list",groupIds);
//        List<Map> jobList = new ArrayList<>();
        for (Map map : msgList) {
            String text = map.get("title")+"";
            String paramtext = "你是一个标签匹配助手，我将提供给你一段文本消息、和一组标签。你分析出最匹配这段话的标签，然后只需要回复标签，不要乱回复，如果没有匹配的标签就回复：暂无匹配。/n文本："+text+"；标签组：科技传媒、出海专题、大消费、健康医疗、金融地产、能源矿产、工业制造、交通物流、公共服务、农林牧渔";
            String tag = HunYuanApiUtil.callApi(paramtext);
            messageMapper.updateMsgTag(map.get("msgid")+"",tag);
            //给人打标签 先判断这个标签有没有打过，已经打过就不打了
            Integer num = messageMapper.getUserTag(map.get("msgfrom")+"",tag);
            if(num == 0){
                messageMapper.saveUserTag(map.get("msgfrom")+"",tag);
            }


//            Map msgMap = new HashMap();
//            msgMap.put("msgid",map.get("zcMsgid"));
//            Map secretKeyMap = new HashMap();
//            secretKeyMap.put("secret_key",map.get("secretKey"));
//            msgMap.put("encrypt_info",secretKeyMap);
//
//            List<Map> msgs = new ArrayList<>();
//            msgs.add(msgMap);
//            request.put("msg_list",msgs);
//            jsonObject.put("request_data", request.toJSONString());
//
//            Map resultMap = RestUtils.post(url, jsonObject);
//            Map<String, Object> responseData = JSON.parseObject((String) resultMap.get("response_data"), Map.class);
//            String jobId = (String) responseData.get("jobid");
//            System.out.println("消息文本："+map.get("text"));
//            System.out.println(jobId);
//            Map jobMap = new HashMap();
//            jobMap.put("jobId",jobId);
//            jobMap.put("text",map.get("text"));
//            jobMap.put("msgId",map.get("msgid"));
//            jobMap.put("msgfrom",map.get("msgfrom"));
//            jobList.add(jobMap);
        }

//        for (Map map : jobList) {
//            JSONObject jobJson = new JSONObject();
//            jobJson.put("program_id", "progYLdqiLu-_ce_XX6yOD_Rbsf6vrvNCySq");
//            jobJson.put("ability_id", "invoke_get_customer_tag_result");
//            JSONObject jobReq = new JSONObject();
//            jobReq.put("jobid",map.get("jobId"));
//            jobJson.put("request_data", jobReq.toJSONString());
//
//            Map tabMap = RestUtils.post(url, jobJson);
////            Map<String, Object> tagResData = JSON.parseObject((String) tabMap.get("response_data"), Map.class);
//            System.out.println("消息文本："+map.get("text"));
//            System.out.println("模型返回："+tabMap.toString());
//        }

    }

    public static void main(String[] args) {

        String url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=";
        //参数
        Map<String,Object> map = new HashMap<>();
        map.put("msgtype","template_card");

        Map templateCard = new HashMap<>();
        templateCard.put("card_type","text_notice");

        //小logo
        Map source = new HashMap();
        source.put("icon_url","");
        source.put("desc","策元");
        source.put("desc_color",0);

        //一级标题
        Map mainTitle  = new HashMap();
        mainTitle.put("title","2024年中国生成式AI行业最佳应用实践");
        mainTitle.put("desc","45334人已阅读");


        //关键数据
//        Map emphasisContent = new HashMap();
//        emphasisContent.put("title","");

        Map pagePath = new HashMap();
        pagePath.put("type",2);
        pagePath.put("appid","wxeec4e69e7f93d7b3");
        pagePath.put("pagepath","/pages/user/detail/index?id="+"2118c579-719d-11ef-b4c0-525400862564");
        pagePath.put("title","点击跳转小程序");

        List<Map> jumpList = new ArrayList<>();


        jumpList.add(pagePath);

        List<Map> horizontalContentList = new ArrayList<>();
        Map secTitle = new HashMap();
        secTitle.put("keyname","50433人在看");
        horizontalContentList.add(secTitle);

        Map cardAction = new HashMap();
        cardAction.put("type",2);
        cardAction.put("appid","wxeec4e69e7f93d7b3");
        cardAction.put("pagepath","/pages/user/detail/index?id="+"2118c579-719d-11ef-b4c0-525400862564");


        templateCard.put("source",source);
//        templateCard.put("main_title",mainTitle);
        templateCard.put("sub_title_text","【深度研报】2024中国企业AI大模型落地应用现状调研报告丨全文32页");
        templateCard.put("jump_list",jumpList);
        templateCard.put("horizontal_content_list",horizontalContentList);
        templateCard.put("card_action",cardAction);

        map.put("template_card",templateCard);


        String jsonString = JSON.toJSONString(map);
        String result = HttpRequest.post(url+"cc4d7c79-2c1e-4e49-9263-5b837012fa52")
                .header("Content-Type", "application/json")
                .body(jsonString)
                .execute().body();
        System.out.println(result);
    }
}
