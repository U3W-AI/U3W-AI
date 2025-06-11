package com.cube.wechat.selfapp.wecom.util;

import cn.felord.api.WorkWeChatApi;
import cn.felord.domain.WeComResponse;
import cn.felord.domain.webhook.WebhookBody;
import cn.felord.domain.webhook.WebhookMarkdownBody;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.cube.wechat.selfapp.app.util.HunYuanApiUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年10月23日 09:31
 */
public class WebHookTem {

    public static void pushXdzxPro(String botKey){
        System.out.println("机器人："+botKey);
        //参数
        Map<String, Object> map = new HashMap<>();
        map.put("msgtype", "template_card");

        Map templateCard = new HashMap<>();
        templateCard.put("card_type", "news_notice");

        //小logo
        Map source = new HashMap();
        source.put("desc", "许都之行");
        source.put("desc_color", 0);

        //一级标题
        Map mainTitle = new HashMap();
        mainTitle.put("title", "许都之行胖东来研究专题");
        mainTitle.put("desc", "");
        Map cardImage = new HashMap();
        cardImage.put("aspect_ratio", 2.25);



        Map pagePath = new HashMap();
        pagePath.put("type", 2);
        pagePath.put("appid", "wx9812ae7b613f2e49");
        pagePath.put("pagepath", "/pages/index");
        pagePath.put("title", "点击跳转小程序");

        List<Map> jumpList = new ArrayList<>();
        jumpList.add(pagePath);

        Map cardAction = new HashMap();
        cardAction.put("type", 2);
        cardAction.put("appid", "wx9812ae7b613f2e49");
        cardAction.put("pagepath", "/pages/index");


        templateCard.put("source", source);
        templateCard.put("jump_list", jumpList);
        templateCard.put("main_title", mainTitle);
        templateCard.put("card_image", cardImage);
        templateCard.put("card_action", cardAction);


        map.put("template_card", templateCard);
        String jsonString = JSON.toJSONString(map);
        String result = HttpRequest.post(botKey)
                .header("Content-Type", "application/json")
                .body(jsonString)
                .execute().body();
        System.out.println(result);
    }


    public static void pushCyPro(String title,String browseNum,String resId,String botKey){

        //参数
        Map<String, Object> map = new HashMap<>();
        map.put("msgtype", "template_card");

        Map templateCard = new HashMap<>();
        templateCard.put("card_type", "text_notice");

        //小logo
        Map source = new HashMap();
        source.put("icon_url", "https://u3w.com/qkxupfile/logo1.jpg");
        source.put("desc", "策元");
        source.put("desc_color", 0);

        //一级标题
        Map mainTitle = new HashMap();
        mainTitle.put("title", title);
        mainTitle.put("desc", browseNum + "人已阅读");


        List<Map> horizontalContentList = new ArrayList<>();
        Map secTitle = new HashMap();
        secTitle.put("keyname", browseNum + "人在看");
        horizontalContentList.add(secTitle);
        //关键数据
        //        Map emphasisContent = new HashMap();
        //        emphasisContent.put("title","");

        Map pagePath = new HashMap();
        pagePath.put("type", 2);
        pagePath.put("appid", "wxeec4e69e7f93d7b3");
        pagePath.put("pagepath", "/pages/user/detail/index?id=" + resId);
        pagePath.put("title", "点击跳转小程序");

        List<Map> jumpList = new ArrayList<>();
        jumpList.add(pagePath);

        Map cardAction = new HashMap();
        cardAction.put("type", 2);
        cardAction.put("appid", "wxeec4e69e7f93d7b3");
        cardAction.put("pagepath", "/pages/user/detail/index?id=" + resId);


        templateCard.put("source", source);
//                            templateCard.put("main_title",mainTitle);
        templateCard.put("sub_title_text", title);
        templateCard.put("jump_list", jumpList);
        templateCard.put("card_action", cardAction);
        templateCard.put("horizontal_content_list", horizontalContentList);

        map.put("template_card", templateCard);
        String jsonString = JSON.toJSONString(map);
        String result = HttpRequest.post(botKey)
                .header("Content-Type", "application/json")
                .body(jsonString)
                .execute().body();
        System.out.println(result);
    }

    public static void pushAIAnal(String paramtext,String botKey) throws Exception {
        String answer = HunYuanApiUtil.callApi(paramtext);
        WebhookBody markdownBody = WebhookMarkdownBody.from(answer);
        WeComResponse weComResponse = WorkWeChatApi.webhookApi().send("c3cee8b5-417e-4e61-a3a4-b455576e19c1", markdownBody);
    }

}
