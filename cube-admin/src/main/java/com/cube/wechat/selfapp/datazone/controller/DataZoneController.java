package com.cube.wechat.selfapp.datazone.controller;

import com.alibaba.fastjson.JSONObject;
import com.cube.point.util.ResultBody;
import com.cube.wechat.selfapp.wecom.util.WeChatApiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年08月13日 14:21
 */
@RestController
@RequestMapping("/datazone")
public class DataZoneController {

    @Value("${datazone.url}")
    private String dataZoneUrl;

    @Autowired
    private WeChatApiUtils weChatApiUtils;



    @PostMapping("/createRule")
    public ResultBody createRule(){
        JSONObject jsonObject = new JSONObject();

        JSONObject request = new JSONObject();
        request.put("name","出海专题");

        List<String> keyWord = new ArrayList<>();
        keyWord.add("帮问问东来哥");
        keyWord.add("请问东来哥");
        keyWord.add("帮问东来哥");
        keyWord.add("帮忙问问东来哥");

        JSONObject wordList = new JSONObject();
        wordList.put("word_list",keyWord);

        request.put("keyword",wordList);

        jsonObject.put("program_id", "progYLdqiLu-_ce_XX6yOD_Rbsf6vrvNCySq");
        jsonObject.put("ability_id", "invoke_create_rule");
        jsonObject.put("request_data", request.toJSONString());
        System.out.println(jsonObject.toString());
//        Map resultMap = RestUtils.post(dataZoneUrl+weChatApiUtils.getAccessToken(), jsonObject);
//
//        Map<String, Object> responseData = JSON.parseObject((String) resultMap.get("response_data"), Map.class);
//
//        System.out.println(responseData.toString());
        return ResultBody.success("新建规则成功");
    }

}
