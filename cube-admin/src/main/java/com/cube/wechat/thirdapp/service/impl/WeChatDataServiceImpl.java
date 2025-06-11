package com.cube.wechat.thirdapp.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cube.common.constant.Constants;
import com.cube.common.core.domain.R;
import com.cube.common.utils.StringUtils;
import com.cube.wechat.thirdapp.constant.BasicConstant;
import com.cube.wechat.thirdapp.entiy.WeChatThirdCompany;
import com.cube.wechat.thirdapp.service.WeChatDataService;
import com.cube.wechat.thirdapp.service.WeChatThirdCompanyService;
import com.cube.wechat.thirdapp.util.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author sjl
 * @Created date 2024/3/7 10:24
 */
@Service
@Slf4j
public class WeChatDataServiceImpl implements WeChatDataService {
    @Autowired
    private RedisService redisService;
    @Autowired
    private WeChatThirdCompanyService weChatThirdCompanyService;

    @Autowired
    private BasicConstant constant;

    @Value("${serviceProviders.REDIRECT_URL}")
    private String REDIRECT_URL;


    @Override
    public R<Map> queryPersonnelDetails(String userId, String accessToken) {
        String userInfoUrl = String.format(WeChatUrlUtil.getDepartmentChildUserInfo, accessToken, userId);
        String resultUserInfoStr = HttpUtil.get(userInfoUrl);
        Map userInfoMap = JSON.parseObject(resultUserInfoStr, Map.class);
        if (userInfoMap.containsKey("errcode") && MapUtils.getInteger(userInfoMap, "errcode") != 0) {
            return R.fail("查询人员详情失败，失败原因:" + MapUtils.getString(userInfoMap, "errmsg"));
        } else {
            return R.ok(userInfoMap, "查询成功");
        }
    }

    @Override
    public R<String> queryCorpAccessToken(String corpId) {
        //todo 查询公司详情
        WeChatThirdCompany WeChatThirdCompany = new WeChatThirdCompany();
        WeChatThirdCompany.setCorpId(corpId);
        WeChatThirdCompany.setSuiteId(constant.getSuiteID());
        Object cacheObject = redisService.getCacheObject(constant.getCorpAccessToken() + "_" + constant.getSuiteID() + "_" + corpId);
        if (ObjectUtil.isNotEmpty(cacheObject)) {
            log.info("从redis获取企业token->" + cacheObject.toString());
            return R.ok(cacheObject.toString(), "获取成功");
        }
        //缓存中没有数据，重新获取
        R<WeChatThirdCompany> thirdCompanyR = weChatThirdCompanyService.selectCompanyInfo(WeChatThirdCompany);
        System.err.println("查询出的公司信息:" + JSON.toJSONString(thirdCompanyR));
        if (thirdCompanyR != null && thirdCompanyR.getData() != null) {
            WeChatThirdCompany company = thirdCompanyR.getData();
            String permanentCode = company.getPermanentCode();
            JSONObject postJson = new JSONObject();
            postJson.put("auth_corpid", corpId);
            postJson.put("permanent_code", permanentCode);
            //获取服务商凭证
            R<String> suiteTokenResult = queryServiceProviderSuiteToken();
            if (suiteTokenResult.getCode() != 200) {
                return suiteTokenResult;
            }
            String suiteToken = suiteTokenResult.getData();
            //公司信息放入缓存
            redisService.setCacheObject(constant.getSuiteID() + "_" + corpId, JSON.toJSONString(company));
            //获取企业访问凭证
            String sessionInfoUrl = String.format(WeChatUrlUtil.getCorpToken, suiteToken);
            Map resultMap = RestUtilsTwo.post(sessionInfoUrl, postJson);
            if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
                log.error("获取企业访问凭证失败");
                return R.fail("获取企业访问凭证失败,失败原因:" + resultMap.get("errmsg"));
            } else {
                String accessToken = MapUtils.getString(resultMap, "access_token");
                Long expiresIn = MapUtils.getLong(resultMap, "expires_in");
                //可能存在请求延迟，影响到token有效期，有效期减去50秒
                redisService.setCacheObject(constant.getCorpAccessToken() + "_" + constant.getSuiteID() + "_" + corpId, accessToken, expiresIn - 50, TimeUnit.SECONDS);
                return R.ok(accessToken);
            }
        } else {
            return R.fail("未获取到当前企业有效授权，请重新授权");
        }
    }

    @Override
    public R<String> queryServiceProviderSuiteToken() {
        String suiteTicket = "";
        Object cacheObject = redisService.getCacheObject(constant.getSuitTicket());
        if (ObjectUtil.isEmpty(cacheObject)) {
            log.error("suit_ticket为空");
            R.fail("suit_ticket为空，请刷新");
        } else {
            suiteTicket = cacheObject.toString();
        }
        if (suiteTicket == "") {
            R.fail("suit_ticket为空，请刷新");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        System.out.println(constant.getSuiteAccessToken());
        Object suiteAccessTokenObject = "";
        if (ObjectUtil.isEmpty(suiteAccessTokenObject)) {
            JSONObject postJson = new JSONObject();
            postJson.put("suite_id", constant.getSuiteID());
            postJson.put("suite_secret", constant.getSuiteSecret());
            postJson.put("suite_ticket", suiteTicket);
            System.err.println(JSON.toJSONString(postJson));
            Map responseMap = RestUtilsTwo.post(WeChatUrlUtil.getSuiteToken, postJson);
            //获取错误日志
            if (responseMap.containsKey("errcode") && (Integer) responseMap.get("errcode") != 0) {
                System.err.println("获取第三方服务商token失败:----------->" + JSON.toJSONString(responseMap));
                return R.fail("获取第三方服务商token失败，失败原因:" + responseMap.get("errmsg"));
            }
            log.info("第三方服务商token接口返回:" + JSON.toJSONString(responseMap));
            String result = (String) responseMap.get("suite_access_token");
            Long expiresIn = MapUtils.getLong(responseMap, "expires_in");
            redisService.setCacheObject(constant.getSuiteAccessToken(), result, expiresIn, TimeUnit.SECONDS);
            return R.ok(result);
        } else {
            log.info("从redis中获取返回");
            return R.ok(suiteAccessTokenObject.toString());
        }
    }

    @Override
    public R<String> queryServiceProviderLoginSuiteToken() {
        String suiteTicket = "";
        Object cacheObject = redisService.getCacheObject(constant.getLoginSuitTicket());
        if (ObjectUtil.isEmpty(cacheObject)) {
            log.error("login_suit_ticket为空");
            R.fail("login_suit_ticket为空，请刷新");
        } else {
            suiteTicket = cacheObject.toString();
        }
        if (suiteTicket == "") {
            R.fail("login_suit_ticket为空，请刷新");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Object suiteAccessTokenObject = redisService.getCacheObject(constant.getLoginSuiteAccessToken());
        if (ObjectUtil.isEmpty(suiteAccessTokenObject)) {
            JSONObject postJson = new JSONObject();
            postJson.put("suite_id", constant.getLoginSuiteId());
            postJson.put("suite_secret", constant.getLoginSuiteSecret());
            postJson.put("suite_ticket", suiteTicket);
            System.err.println(JSON.toJSONString(postJson));
            Map responseMap = RestUtilsTwo.post(WeChatUrlUtil.getSuiteToken, postJson);
            //获取错误日志
            if (responseMap.containsKey("errcode") && (Integer) responseMap.get("errcode") != 0) {
                System.err.println("获取第三方服务商登录-token失败:----------->" + JSON.toJSONString(responseMap));
                return R.fail("获取第三方服务商登录-token失败，失败原因:" + responseMap.get("errmsg"));
            }
            log.info("第三方服务商登录-token接口返回:" + JSON.toJSONString(responseMap));
            String result = (String) responseMap.get("suite_access_token");
            Long expiresIn = MapUtils.getLong(responseMap, "expires_in");
            redisService.setCacheObject(constant.getLoginSuiteAccessToken(), result, expiresIn, TimeUnit.SECONDS);
            return R.ok(result);
        } else {
            log.info("从redis中获取返回");
            return R.ok(suiteAccessTokenObject.toString());
        }
    }

    @Override
    public R<Map> queryCorpPermanentCode(String corpAuthCode) {
        //通过auth code获取公司信息及永久授权码
        JSONObject postJson = new JSONObject();
        postJson.put("auth_code", corpAuthCode);
        //从缓存获取公司永久授权码
        Map response = new HashMap();
        Object cacheObject = redisService.getCacheObject(corpAuthCode);
        if (ObjectUtil.isNotEmpty(cacheObject)) {
            response = JSON.parseObject(cacheObject.toString(), Map.class);
        } else {
            String url = String.format(WeChatUrlUtil.permanentCodeUrl, queryServiceProviderSuiteToken().getData());
            log.info("通过auth code获取公司信息及永久授权码调用url======>" + url);
            postJson.put("auth_code", corpAuthCode);
            response = RestUtilsTwo.post(url, postJson);
        }
        //获取错误日志
        if (response.containsKey("errcode") && (Integer) response.get("errcode") != 0) {
            log.error("获取公司信息及永久授权码失败:" + response.toString());
            return R.fail("获取公司信息及永久授权码失败，失败原因:" + response.get("errmsg"));
        } else {
            redisService.setCacheObject(corpAuthCode, JSON.toJSONString(response), 60L, TimeUnit.SECONDS);
        }
        return R.ok(response, "获取公司信息及永久授权码成功");
    }

    @Override
    public R<Map> queryCorpDepartmentIdList(String corpId) {
        R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
        if (corpAccessTokenResult.getCode() != R.SUCCESS) {
            return R.fail("获取企业访问凭证失败:" + corpAccessTokenResult.getMsg());
        }
        String url = String.format(WeChatUrlUtil.getDepartmentIdList, corpAccessTokenResult.getData());
        String result = HttpUtil.get(url);
        Map resultMap = JSON.parseObject(result, Map.class);
        if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
            log.error("===================获取企业部门数据失败=================");
            log.error(JSON.toJSONString(resultMap));
            return R.fail("获取企业部门数据失败，失败原因：" + resultMap.get("errmsg"));
        } else {
            return R.ok(resultMap);
        }
    }

    @Override
    public R<Map> queryDepartmentChildUser(String corpAccessToken, String departmentId) {
        String userUrl = String.format(WeChatUrlUtil.getDepartmentChildUser, corpAccessToken, departmentId);
        String resonseUserStr = HttpUtil.get(userUrl);
        Map responseUserMap = JSON.parseObject(resonseUserStr, Map.class);
        if (responseUserMap.containsKey("errcode") && (Integer) responseUserMap.get("errcode") != 0) {
            log.error("===================获取部门人员数据失败=================");
            log.error(JSON.toJSONString(responseUserMap));
            return R.fail("获取部门下人员数据失败:" + responseUserMap.get("errmsg"));
        } else {
            return R.ok(responseUserMap);
        }
    }

    @Override
    public R<Map> queryUserIdentity(String user_auth_code) {
        R<String> corpAccessTokenR = queryServiceProviderSuiteToken();
        if (corpAccessTokenR.getCode() == R.SUCCESS) {
            String getUserInfoUrl = String.format(WeChatUrlUtil.getuserinfo, corpAccessTokenR.getData(), user_auth_code);
            String userInfoResult = HttpUtil.get(getUserInfoUrl);
            Map userInfoMap = JSON.parseObject(userInfoResult, Map.class);
            if (userInfoMap.containsKey("errcode") && MapUtils.getInteger(userInfoMap, "errcode") != 0) {
                log.error(JSON.toJSONString(userInfoMap));
                return R.fail("查询用户身份失败，" + userInfoMap.get("errmsg"));
            } else {
                return R.ok(userInfoMap);
            }
        } else {
            return R.fail("查询用户身份失败，未获取到服务商访问凭证");
        }
    }

    @Override
    public R<Map> queryScanCoeUserIdentity(String user_auth_code) {
        R<String> corpAccessTokenR = queryServiceProviderLoginSuiteToken();
        if (corpAccessTokenR.getCode() == R.SUCCESS) {
            String getUserInfoUrl = String.format(WeChatUrlUtil.getuserinfo, corpAccessTokenR.getData(), user_auth_code);
            String userInfoResult = HttpUtil.get(getUserInfoUrl);
            Map userInfoMap = JSON.parseObject(userInfoResult, Map.class);
            if (userInfoMap.containsKey("errcode") && MapUtils.getInteger(userInfoMap, "errcode") != 0) {
                log.error(JSON.toJSONString(userInfoMap));
                return R.fail("查询用户身份失败，" + userInfoMap.get("errmsg"));
            } else {
                return R.ok(userInfoMap);
            }
        } else {
            return R.fail("查询用户身份失败，未获取到服务商访问凭证");
        }
    }

    @Override
    public R<Map> queryUserSensitiveInformation(String userTicket) {
        //更新用户头像
        R<String> corpAccessToken = queryServiceProviderSuiteToken();
        if (corpAccessToken.getCode() == R.SUCCESS) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("user_ticket", userTicket);
            String userDetailUrl = String.format(WeChatUrlUtil.getuserdetail3rd, corpAccessToken.getData());
            JSONObject userDetailResult = RestUtilsTwo.post(userDetailUrl, jsonObject);
            if (userDetailResult.containsKey("errcode") && (Integer) userDetailResult.get("errcode") != 0) {
                log.error("获取用户敏感信息失败");
                return R.fail("获取用户敏感信息失败，" + userDetailResult.get("errmsg"));
            } else {
                return R.ok(userDetailResult);
            }
        } else {
            return R.fail(corpAccessToken.getMsg());
        }

    }

    @Override
    public R<Map> queryDepartmentInfo(String corpId, String departmentId) {
        R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
        if (corpAccessTokenResult.getCode() != R.SUCCESS) {
            return R.fail("获取企业访问凭证失败:" + corpAccessTokenResult.getMsg());
        }
        String url = String.format(WeChatUrlUtil.getDepartmentInfo, corpAccessTokenResult.getData(), departmentId);
        String departmentResult = HttpUtil.get(url);
        Map departmentResultMap = JSON.parseObject(departmentResult, Map.class);
        return R.ok(departmentResultMap);
    }

    @Override
    public R<String> queryCorpJsApiTicket(String corpId) {
        String jsApiTicket;
        Object jsApiObject = redisService.getCacheObject("CorpJSAPI_" + constant.getSuiteID() + "_" + corpId);
        if (ObjectUtil.isNotEmpty(jsApiObject)) {
            jsApiTicket = jsApiObject.toString();
            log.info("从缓存获取jsApiTicket:" + jsApiTicket);
        } else {
            R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
            if (corpAccessTokenResult.getCode() != R.SUCCESS) {
                return R.fail("获取企业访问凭证失败:" + corpAccessTokenResult.getMsg());
            }
            String url = String.format(WeChatUrlUtil.getCorpJsApiTicket, corpAccessTokenResult.getData());
            String jsApiTicketResult = HttpUtil.get(url);
            Map jsApiTicketResultMap = JSON.parseObject(jsApiTicketResult, Map.class);
            if (jsApiTicketResultMap.containsKey("errcode") && (Integer) jsApiTicketResultMap.get("errcode") != 0) {
                log.error("获取企业jsApiTicket失败");
                return R.fail("获取企业jsApiTicket失败，" + jsApiTicketResultMap.get("errmsg"));
            }
            String ticket = MapUtils.getString(jsApiTicketResultMap, "ticket");
            jsApiTicket = ticket;
            long expiresIn = MapUtils.getLong(jsApiTicketResultMap, "expires_in");
            redisService.setCacheObject("CorpJSAPI_" + constant.getSuiteID() + "_" + corpId, ticket, expiresIn - 200, TimeUnit.SECONDS);

        }
        return R.ok(jsApiTicket);
    }

    @Override
    public R<String> queryAppJsapiTicket(String corpId) {
        String jsApiTicket;
        Object jsApiObject = redisService.getCacheObject("AppJSAPI_" + constant.getSuiteID() + "_" + corpId);
        if (ObjectUtil.isNotEmpty(jsApiObject)) {
            jsApiTicket = jsApiObject.toString();
            log.info("从缓存中获取应用 jsApiTicket:" + jsApiTicket);
        } else {
            R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
            if (corpAccessTokenResult.getCode() != R.SUCCESS) {
                return R.fail("获取企业访问凭证失败:" + corpAccessTokenResult.getMsg());
            }
            String url = String.format(WeChatUrlUtil.getAppJsApiTicket, corpAccessTokenResult.getData());
            String jsApiTicketResult = HttpUtil.get(url);
            Map jsApiTicketResultMap = JSON.parseObject(jsApiTicketResult, Map.class);
            if (jsApiTicketResultMap.containsKey("errcode") && (Integer) jsApiTicketResultMap.get("errcode") != 0) {
                log.error("获取应用jsApiTicket失败");
                return R.fail("获取应用jsApiTicket失败，" + jsApiTicketResultMap.get("errmsg"));
            }
            String ticket = MapUtils.getString(jsApiTicketResultMap, "ticket");
            jsApiTicket = ticket;
            long expiresIn = MapUtils.getLong(jsApiTicketResultMap, "expires_in");
            redisService.setCacheObject("AppJSAPI_" + constant.getSuiteID() + "_" + corpId, ticket, expiresIn - 200, TimeUnit.SECONDS);
        }
        return R.ok(jsApiTicket);
    }

    @Override
    public R<WeChatThirdCompany> queryCorpInfo(String corpId, String suiteId) {
        WeChatThirdCompany WeChatThirdCompany = new WeChatThirdCompany();
        WeChatThirdCompany.setCorpId(corpId);
        WeChatThirdCompany.setSuiteId(suiteId);
        //公司信息放入缓存
        Object cacheObject = redisService.getCacheObject(constant.getSuiteID() + "_" + corpId);
        if (ObjectUtil.isNotEmpty(cacheObject)) {
            WeChatThirdCompany qy = JSON.parseObject(cacheObject.toString(), WeChatThirdCompany.class);
            return R.ok(qy);
        } else {
            R<WeChatThirdCompany> thirdCompanyR = weChatThirdCompanyService.selectCompanyInfo(WeChatThirdCompany);
            System.err.println("查询出的公司信息:" + JSON.toJSONString(thirdCompanyR));
            //公司信息放入缓存
            redisService.setCacheObject(constant.getSuiteID() + "_" + corpId, JSON.toJSONString(thirdCompanyR.getData()));
            return R.ok(thirdCompanyR.getData());
        }

    }

    @Override
    public R<List<Map<String, Object>>> queryAppShareInfo(String agentId, String corpId) {
        R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
        if (corpAccessTokenResult.getCode() != R.SUCCESS) {
            return R.fail("获取企业访问凭证失败:" + corpAccessTokenResult.getMsg());
        }
        String appShareInfoUrl = String.format(WeChatUrlUtil.getAppShareInfo, corpAccessTokenResult.getData());
        JSONObject postJson = new JSONObject();
        postJson.put("agentid", agentId);
        postJson.put("business_type", 1);
        //postJson.put("corpid", corpId);
        postJson.put("limit", 0);
        log.info("获取企业上下游URL:-------------->" + appShareInfoUrl);
        log.info("获取企业上下游参数:-------------->" + postJson);
        Map<String, Object> masterCorp = new HashMap<>();
        List<Map<String, Object>> resultList = new ArrayList<>();
        new ArrayList<>();
        masterCorp.put("corpid", corpId);
        masterCorp.put("agentId", agentId);
        masterCorp.put("parentId", "0");
        masterCorp.put("suiteId", constant.getSuiteID());
        resultList.add(masterCorp);
        JSONObject shareInfoResult = RestUtilsTwo.post(appShareInfoUrl, postJson);
        if (shareInfoResult.containsKey("errcode") && (Integer) shareInfoResult.get("errcode") != 0) {
            log.error("获取企业上下游数据失败");
            return R.fail("获取企业上下游数据失败，" + shareInfoResult.get("errmsg"));
        } else {
            log.info("下游企业集==================================================");
            log.info(JSON.toJSONString(shareInfoResult));
            List<Map> corpList = (List<Map>) shareInfoResult.get("corp_list");
            if (corpList != null && corpList.size() > 0) {
                for (Map map : corpList) {
                    map.put("parentId", corpId);
                    map.put("suiteId", constant.getSuiteID());
                    map.put("agentId", MapUtils.getString(map, "agentid"));
                    resultList.add(map);
                }
            }
            return R.ok(resultList);
        }
    }


    @Override
    public R<Map> getCorpTagList(String corpId) {
        R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
        if (corpAccessTokenResult.getCode() != R.SUCCESS) {
            return R.fail("获取企业访问凭证失败:" + corpAccessTokenResult.getMsg());
        }
        String url = String.format(WeChatUrlUtil.getCorpTagList, corpAccessTokenResult.getData());
//        String url = String.format(WeChatUrlUtil.getCorpTagList, s);

        HttpResponse httpResponse = HttpRequest.post(url).execute();

        Map labelResultMap = JSON.parseObject(httpResponse.body(), Map.class);
        if (labelResultMap.containsKey("errcode") && (Integer) labelResultMap.get("errcode") != 0) {
            log.error("获取企业标签库数据失败，失败原因:" + JSON.toJSONString(labelResultMap));
            return R.fail("获取企业标签库数据失败，" + labelResultMap.get("errmsg"));
        }
        return R.ok(labelResultMap);
    }


    @Override
    public R<Map> checkCorpTagDeleted(JSONObject jsonObject, String corpId) {
        R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
        if (corpAccessTokenResult.getCode() != R.SUCCESS) {
            return R.fail("获取企业访问凭证失败:" + corpAccessTokenResult.getMsg());
        }
        String url = String.format(WeChatUrlUtil.getCorpTagList, corpAccessTokenResult.getData());
//        String url = String.format(WeChatUrlUtil.getCorpTagList,"ECRilurH7lezkZOUoM7uxJafcU12lOp3FVfhdca5ONAJKIlyWGAo_KYkjRfK4YPpNaQHusB6CLUfljLKGuF-tJvzWDi_tEDwOA_kRc4FMif23f0GnYFzTpfZMeeWa3_uFtA1tmBFkV97gqFcREL_zv5oJQ7gzfeGlob5cdY0JZLh6-UBgnata9-LMx1GYzgBGpZ7obxej2Wi2TMDshWlzQ" );


        Map resultMap = RestUtilsTwo.post(url, jsonObject);
        if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
            log.error("查看失败：#{}", resultMap.get("errmsg"));
            System.err.println(" 查询企微是否删除失败,失败原因:" + resultMap.get("errmsg"));
            return R.fail("查询企微是否删除失败,失败原因:" + resultMap.get("errmsg"));
        }

        return R.ok(resultMap);
    }

    @Override
    public R<Map> addCorpTag(JSONObject jsonObject, String corpId) {
//        String s ="ECRilurH7lezkZOUoM7uxJafcU12lOp3FVfhdca5ONAJKIlyWGAo_KYkjRfK4YPpNaQHusB6CLUfljLKGuF-tJvzWDi_tEDwOA_kRc4FMif23f0GnYFzTpfZMeeWa3_uFtA1tmBFkV97gqFcREL_zv5oJQ7gzfeGlob5cdY0JZLh6-UBgnata9-LMx1GYzgBGpZ7obxej2Wi2TMDshWlzQ";

        R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
        if (corpAccessTokenResult.getCode() != R.SUCCESS) {
            return R.fail("获取企业访问凭证失败:" + corpAccessTokenResult.getMsg());
        }
        String url = String.format(WeChatUrlUtil.addCorpTag, corpAccessTokenResult.getData());

        Map resultMap = RestUtilsTwo.post(url, jsonObject);
        if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
            log.error("添加企业客户标签失败：#{}", resultMap.get("errmsg"));
            System.err.println("添加企业客户标签失败,失败原因:" + resultMap.get("errmsg"));
            return R.fail("添加企业客户标签失败,失败原因:" + resultMap.get("errmsg"));
        }

        return R.ok(resultMap);
    }

    @Override
    public R<Map> addCorpTagBatch(Map map, String corpId) {

        R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
        if (corpAccessTokenResult.getCode() != R.SUCCESS) {
            return R.fail("获取企业访问凭证失败:" + corpAccessTokenResult.getMsg());
        } else {
            String url = String.format(WeChatUrlUtil.addCorpTag, corpAccessTokenResult.getData());
            String groupName = MapUtils.getString(map, "group_name");
            String group_id = MapUtils.getString(map, "group_id");
            JSONObject jsonObject = new JSONObject();
            if (StringUtils.isNotEmpty(groupName)) {
                return R.fail("未获取到标签组名称");
            }
            jsonObject.put("group_name", groupName);
            jsonObject.put("group_id", group_id);
            List<Map> tagList = (List<Map>) map.get("tagList");
            if (tagList != null && tagList.size() > 0) {
                jsonObject.put("tag", tagList);
            }
            Map resultMap = RestUtilsTwo.post(url, jsonObject);
            if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
                log.error("添加企业标签失败：#{}", resultMap.get("errmsg"));
                return R.fail("添加企业标签失败");
            } else {
                Map tagGroup = (Map) resultMap.get("tag_group");
                return R.ok(tagGroup);
            }
        }
    }

    @Override
    public R<Map> editCorpTag(JSONObject jsonObject, String corpId) {

        R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
        if (corpAccessTokenResult.getCode() != R.SUCCESS) {
            return R.fail("获取企业访问凭证失败:" + corpAccessTokenResult.getMsg());
        }
        String url = String.format(WeChatUrlUtil.editCorpTag, corpAccessTokenResult.getData());

        Map resultMap = RestUtilsTwo.post(url, jsonObject);
        if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
            log.error("编辑企业客户标签失败：#{}", resultMap.get("errmsg"));
            System.err.println("编辑企业客户标签失败,失败原因:" + resultMap.get("errmsg"));
            String errmsg = (String) resultMap.get("errmsg");
            if (errmsg.contains("no priviledge to access")) {
                return R.fail("该标签由企微创建，不允许修改!若要修改请至企微后台进行操作");
            }
            return R.fail("编辑企业客户标签失败,失败原因:" + resultMap.get("errmsg"));
        }

        return R.ok(resultMap);
    }


    @Override
    public R<Map> delCorpTag(JSONObject jsonObject, String corpId) {

        R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
        if (corpAccessTokenResult.getCode() != R.SUCCESS) {
            return R.fail("获取企业访问凭证失败:" + corpAccessTokenResult.getMsg());
        }
        String url = String.format(WeChatUrlUtil.delCorpTag, corpAccessTokenResult.getData());
//        String s = "ECRilurH7lezkZOUoM7uxJafcU12lOp3FVfhdca5ONAJKIlyWGAo_KYkjRfK4YPpNaQHusB6CLUfljLKGuF-tJvzWDi_tEDwOA_kRc4FMif23f0GnYFzTpfZMeeWa3_uFtA1tmBFkV97gqFcREL_zv5oJQ7gzfeGlob5cdY0JZLh6-UBgnata9-LMx1GYzgBGpZ7obxej2Wi2TMDshWlzQ";
//        String url = String.format(WeChatUrlUtil.delCorpTag, s);
        Map resultMap = RestUtilsTwo.post(url, jsonObject);
        if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
            log.error("编辑企业客户标签失败：#{}", resultMap.get("errmsg"));
            System.err.println("删除企业客户标签失败,失败原因:" + resultMap.get("errmsg"));
            String errmsg = (String) resultMap.get("errmsg");
            if (errmsg.contains("no priviledge to access")) {
                return R.fail("该标签由企微创建，不允许删除!若要删除请至企微后台进行操作");
            }
            return R.fail("删除企业客户标签失败,失败原因:" + resultMap.get("errmsg"));
        }

        return R.ok(resultMap);
    }

    @Override
    public R<Map> updateAllCorpAccessToken() {
        //todo 查询公司详情
        WeChatThirdCompany WeChatThirdCompany = new WeChatThirdCompany();
        WeChatThirdCompany.setSuiteId(constant.getSuiteID());
        //缓存中没有数据，重新获取
        R<List<WeChatThirdCompany>> selectAllCompany = weChatThirdCompanyService.selectAllCompany(WeChatThirdCompany);
        System.err.println("查询出的公司信息:" + JSON.toJSONString(selectAllCompany));
        if (selectAllCompany != null && selectAllCompany.getData() != null) {
            List<WeChatThirdCompany> WeChatThirdCompanyList = selectAllCompany.getData();
            for (WeChatThirdCompany thirdCompany : WeChatThirdCompanyList) {
                String corpId = thirdCompany.getCorpId();
                String permanentCode = thirdCompany.getPermanentCode();
                JSONObject postJson = new JSONObject();
                postJson.put("auth_corpid", corpId);
                postJson.put("permanent_code", permanentCode);
                //获取服务商凭证
                R<String> suiteTokenResult = queryServiceProviderSuiteToken();
                if (suiteTokenResult.getCode() != 200) {
                    continue;
                }
                String suiteToken = suiteTokenResult.getData();
                //公司信息放入缓存
                redisService.setCacheObject(constant.getSuiteID() + "_" + corpId, JSON.toJSONString(thirdCompany));
                //获取企业访问凭证
                String sessionInfoUrl = String.format(WeChatUrlUtil.getCorpToken, suiteToken);
                Map resultMap = RestUtilsTwo.post(sessionInfoUrl, postJson);
                if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
                    log.error("获取企业访问凭证失败");
                    continue;
                } else {
                    String accessToken = MapUtils.getString(resultMap, "access_token");
                    Long expiresIn = MapUtils.getLong(resultMap, "expires_in");
                    //可能存在请求延迟，影响到token有效期，有效期减去50秒
                    redisService.setCacheObject(constant.getCorpAccessToken() + "_" + constant.getSuiteID() + "_" + corpId, accessToken, expiresIn - 50, TimeUnit.SECONDS);
                }
            }
        }
        return R.ok();
    }

    @Override
    public R<List<String>> queryExternalcontactList(String corpId, String userId) {
        R<String> corpAccessToken = queryCorpAccessToken(corpId);
        if (corpAccessToken.getCode() == R.SUCCESS) {
            String data = corpAccessToken.getData();
            String url = String.format(WeChatUrlUtil.getExternalcontactList, data, userId);
            String externalContactUrlResult = HttpUtil.get(url);
            log.info("获取" + userId + "的外部联系人结果", externalContactUrlResult);
            Map externalContactResultMap = JSON.parseObject(externalContactUrlResult, Map.class);
            if (externalContactResultMap.containsKey("errcode") && MapUtils.getInteger(externalContactResultMap, "errcode") != 0) {
                log.error("============================获取" + userId + "的外部联系人列表失败===================================");
                log.error(JSON.toJSONString(externalContactResultMap));
                return R.fail(null);
            } else {
                List<String> externalUseridList = (List<String>) externalContactResultMap.get("external_userid");
                return R.ok(externalUseridList);
            }
        } else {
            return R.fail();
        }

    }

    @Override
    public R<List<Map<String, Object>>> queryBatchExternalcontactList(String corpId, String userId, String cursor) {
        List<Map<String, Object>> resultDataList = new ArrayList<>();
        recursiveAcquisition(resultDataList, corpId, userId, "");

        if (resultDataList != null && resultDataList.size() > 0) {
            return R.ok(resultDataList);
        } else {
            return R.fail("未获取到当前人员的外部联系人数据");
        }
    }

    /**
     * 递归获取数据
     *
     * @param dataList
     * @param corpId
     * @param userId
     * @param nextCursor
     */
    public void recursiveAcquisition(List<Map<String, Object>> dataList, String corpId, String userId, String nextCursor) {
        R<String> corpAccessToken = queryCorpAccessToken(corpId);
        int code = corpAccessToken.getCode();
        if (code == R.SUCCESS) {
            String data = corpAccessToken.getData();
            String url = String.format(WeChatUrlUtil.getGetExternalContactBatch, data);
            JSONObject jsonObject = new JSONObject();
            List<String> userIdList = new ArrayList<>();
            userIdList.add(userId);
            jsonObject.put("userid_list", userIdList);
            jsonObject.put("cursor", nextCursor);
            jsonObject.put("limit", 100);
            Map resultMap = RestUtilsTwo.post(url, jsonObject);
            if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
                log.error("批量查询用户详情失败：" + JSON.toJSONString(resultMap));
            } else {
                List<Map> externalContactList = (List<Map>) resultMap.get("external_contact_list");
                if (externalContactList != null && externalContactList.size() > 0) {
                    for (Map map : externalContactList) {
                        Map<String, Object> dataMap = new HashMap<>();
                        Map followInfo = (Map) map.get("follow_info");
                        Map external_contact = (Map) map.get("external_contact");
                        if (external_contact != null) {
                            dataMap.put("external_userid", MapUtils.getString(external_contact, "external_userid"));
                            dataMap.put("name", MapUtils.getString(external_contact, "name"));
                            dataMap.put("type", MapUtils.getString(external_contact, "type"));
                            dataMap.put("gender", MapUtils.getString(external_contact, "gender"));
                            dataMap.put("unionid", MapUtils.getString(external_contact, "unionid"));
                            dataMap.put("corp_name", MapUtils.getString(external_contact, "corp_name"));
                            dataMap.put("corp_full_name", MapUtils.getString(external_contact, "corp_full_name"));
                            dataMap.put("position", MapUtils.getString(external_contact, "position"));
                        }
                        if (followInfo != null) {
                            dataMap.put("userid", MapUtils.getString(followInfo, "userid"));
                            dataMap.put("remark", MapUtils.getString(followInfo, "remark"));
                            dataMap.put("description", MapUtils.getString(followInfo, "description"));
                            dataMap.put("createtime", MapUtils.getString(followInfo, "createtime"));
                            dataMap.put("tagIdList", MapUtils.getObject(followInfo, "tag_id", List.class));
                            dataMap.put("add_way", MapUtils.getString(followInfo, "add_way"));
                            dataMap.put("oper_userid", MapUtils.getString(followInfo, "oper_userid"));
                            dataMap.put("remark_corp_name", MapUtils.getString(followInfo, "remark_corp_name"));
                        }
                        dataList.add(dataMap);
                    }
                }

                nextCursor = MapUtils.getString(resultMap, "next_cursor");
                if (StringUtils.isNotEmpty(nextCursor)) {
                    recursiveAcquisition(dataList, corpId, userId, nextCursor);
                }

            }
        }
    }


    @Override
    public R<Map> queryExternalContactInfo(String corpId, String externalUserId, Integer next_cursor) {
        R<String> corpAccessToken = queryCorpAccessToken(corpId);
        if (corpAccessToken.getCode() == R.SUCCESS) {
            //外部联系人详情
            String corpAccessTokenData = corpAccessToken.getData();
            String externalContactInfoUrl = String.format(WeChatUrlUtil.getExternalContact, corpAccessTokenData,
                    externalUserId);
            if (next_cursor != null) {
                externalContactInfoUrl = externalContactInfoUrl + "&&cursor=" + next_cursor;
            }
            String externalContactInfoResult = HttpUtil.get(externalContactInfoUrl);
            Map externalContactInfoResultMap = JSON.parseObject(externalContactInfoResult, Map.class);
            if (externalContactInfoResultMap.containsKey("errcode") && MapUtils.getInteger(externalContactInfoResultMap, "errcode") != 0) {
                log.error("============================获取外部联系人" + externalUserId + "的详情失败===================================");
                log.error(JSON.toJSONString(externalContactInfoResultMap));
                return R.fail(externalContactInfoResultMap);
            } else {
                return R.ok(externalContactInfoResultMap);
            }
        } else {
            return R.fail();
        }
    }

    @Override
    public R updateCorpExternalContactTag(String corpId, MarkTagParam markTagParam) {
        R<String> corpAccessTokenR = queryCorpAccessToken(corpId);
        if (corpAccessTokenR.getCode() == R.SUCCESS) {
            String accessTokenRData = corpAccessTokenR.getData();
            String markTagUrl = String.format(WeChatUrlUtil.markTag, accessTokenRData);
            if (markTagParam != null) {
                JSONObject paramObject = (JSONObject) JSON.toJSON(markTagParam);
                Map resultMap = RestUtilsTwo.post(markTagUrl, paramObject);
                if (resultMap.containsKey("errcode") && MapUtils.getInteger(resultMap, "errcode") != 0) {
                    log.error("============================修改外部联系人标签失败,失败原因===================================");
                    log.error(MapUtils.getString(resultMap, "errmsg"));
                    Integer integer = MapUtils.getInteger(resultMap, "errcode");
                    //如果是人员不存在导致的更新企业微信标签失败，算作成功
                    if (integer != null && integer == 60111) {
                        return R.ok();
                    } else {
                        return R.fail("修改外部联系人标签失败：" + MapUtils.getString(resultMap, "errmsg"));
                    }
                } else {
                    return R.ok();
                }
            } else {
                return R.ok();
            }
        } else {
            return R.fail("修改客户标签失败，企业访问凭证获取失败");
        }

    }

    @Override
    public R<Map> uploadTemporaryMaterials(File file) throws Exception {
        R<String> providerSuiteToken = queryServiceProviderSuiteToken();
        if (providerSuiteToken.getCode() == R.SUCCESS) {
            String formatUrl = String.format(WeChatUrlUtil.upload, providerSuiteToken.getData());
            OkHttpClient client = new OkHttpClient();
            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("media", file.getName(),
                            RequestBody.create(okhttp3.MediaType.parse("application/octet-stream"), file))
                    .addFormDataPart("filename", file.getName())
                    .addFormDataPart("filelength", String.valueOf(file.length()))
                    .build();
            Request request = new Request.Builder()
                    .url(formatUrl)
                    .addHeader("Content-Type", "multipart/form-data; boundary=-------------------------acebdf13572468")
                    .post(body)
                    .build();
            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();
            Map resultMap = JSON.parseObject(responseBody, Map.class);
            if (resultMap.containsKey("errcode") && MapUtils.getInteger(resultMap, "errcode") != 0) {
                log.error("============================上传临时素材失败===================================");
                log.error(MapUtils.getString(resultMap, "errmsg"));
                return R.fail("上传企业微信临时素材失败");
            } else {
                return R.ok(resultMap);
            }
        } else {
            return R.fail("获取服务商访问凭证失败");
        }

    }

    @Override
    public R<Map> addressBookIdTranslation(String corpId, String media_id) {
        R<String> providerSuiteToken = queryServiceProviderSuiteToken();
        if (providerSuiteToken.getCode() == R.SUCCESS) {
            String providerSuiteTokenData = providerSuiteToken.getData();
            String url = String.format(WeChatUrlUtil.idTranslateUrl, providerSuiteTokenData);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("auth_corpid", corpId);
            List<String> idList = new ArrayList<>();
            idList.add(media_id);
            jsonObject.put("media_id_list", idList);
            Map resultMap = RestUtilsTwo.post(url, jsonObject);
            if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
                log.error("id转译失败：" + JSON.toJSONString(resultMap));
                return R.fail("通讯录ID转译失败,原因：" + resultMap.get("errmsg"));
            } else {
                return R.ok(resultMap);
            }
        } else {
            return R.fail("通讯录ID转译失败,服务商凭证获取失败");
        }
    }

    @Override
    public R<Map> fileExport(File file, String corpId) throws Exception {
        R<Map> fileUploadR = uploadTemporaryMaterials(file);
        Map<String, Object> resultDataMap = new HashMap<>();
        if (fileUploadR.getCode() == R.SUCCESS) {
            resultDataMap.putAll(fileUploadR.getData());
            //企业微信文件id
            String media_id = MapUtils.getString(fileUploadR.getData(), "media_id");
            //通讯录 ID转义
            R<Map> addressBookIdTranslationR = addressBookIdTranslation(corpId, media_id);
            if (addressBookIdTranslationR != null && addressBookIdTranslationR.getCode() != R.SUCCESS) {
                return R.fail(addressBookIdTranslationR.getMsg());
            } else {
                resultDataMap.putAll(addressBookIdTranslationR.getData());
                return R.ok(resultDataMap);
            }
        } else {
            return R.fail("上传服务商素材失败：" + fileUploadR.getMsg());
        }
    }

    @Override
    public R<String> getAsynchronousTaskResults(String jobId) {
        R<String> providerSuiteToken = queryServiceProviderSuiteToken();
        if (providerSuiteToken.getCode() == R.SUCCESS) {
            String providerSuiteTokenData = providerSuiteToken.getData();
            String url = String.format(WeChatUrlUtil.getAsynchronousTaskResults, providerSuiteTokenData, jobId);
            Map resultMap = RestUtilsTwo.get(url);
            if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
                log.error("任务处理结果查询失败：" + JSON.toJSONString(resultMap));
                return R.fail("任务处理结果查询失败,原因：" + resultMap.get("errmsg"));
            } else {
                Map resultDataMap = (Map) resultMap.get("result");
                if (resultDataMap != null) {
                    Map contact_id_translate = (Map) resultDataMap.get("contact_id_translate");
                    if (contact_id_translate != null) {
                        String dataUrl = MapUtils.getString(contact_id_translate, "url");
                        return R.ok(dataUrl);
                    }
                }
                return R.fail("任务处理结果查询失败，未获取到文件url");
            }
        } else {
            return R.fail("任务处理结果查询失败，未获取到服务商访问凭证");
        }
    }

    /**
     * 发送卡片消息
     *
     * @param map
     * @return
     */
    @Override
    public R<Map> sendTextCardMessage(Map map) {
        String corpId = MapUtils.getString(map, "corpId");
        if (StringUtils.isNotEmpty(corpId)) {
            R<String> corpAccessToken = queryCorpAccessToken(corpId);
            if (corpAccessToken.getCode() == R.SUCCESS) {
                String corpAccessTokenData = corpAccessToken.getData();
                String touser = MapUtils.getString(map, "touser");
                if (StringUtils.isEmpty(touser)) {
                    return R.fail("卡片消息发送失败，未获取到发送人");
                }
                R<WeChatThirdCompany> WeChatThirdCompanyR = queryCorpInfo(corpId, constant.getSuiteID());
                if (WeChatThirdCompanyR.getCode() != R.SUCCESS) {
                    return R.fail("卡片消息发送失败，未获取到企业详情");
                }
                if (StringUtils.isEmpty(touser)) {
                    return R.fail("卡片消息发送失败，未获取到发送人");
                }
                String title = MapUtils.getString(map, "title");
                if (StringUtils.isEmpty(title)) {
                    return R.fail("卡片消息发送失败，未获取消息标题");
                }
                String description = MapUtils.getString(map, "description");
                if (StringUtils.isEmpty(description)) {
                    return R.fail("卡片消息发送失败，未获取消息内容描述");
                }
                String url = MapUtils.getString(map, "url");
                if (StringUtils.isEmpty(url)) {
                    url = REDIRECT_URL;
                }
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("touser", touser);
                jsonObject.put("msgtype", "textcard");
                jsonObject.put("agentid", WeChatThirdCompanyR.getData().getAgentId());
                JSONObject textcard = new JSONObject();
                textcard.put("title", title);
                textcard.put("description", "<div class=\"normal\">" + description + "</div>");
                textcard.put("url", url);
                String btntxt = MapUtils.getString(map, "btntxt");
                if (StringUtils.isEmpty(btntxt)) {
                    btntxt = "详情";
                }
                textcard.put("btntxt", btntxt);
                jsonObject.put("textcard", textcard);
                jsonObject.put("enable_id_trans", 0);
                jsonObject.put("enable_duplicate_check", 0);
                jsonObject.put("duplicate_check_interval", 18000);

                String formatUrl = String.format(WeChatUrlUtil.sendTextCardMessage, corpAccessTokenData);
                Map resultMap = RestUtilsTwo.post(formatUrl, jsonObject);
                if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
                    log.error("卡片消息发送失败:" + JSON.toJSONString(resultMap));
                    return R.fail("卡片消息发送失败");
                } else {
                    return R.ok();
                }
            } else {
                return R.fail("卡片消息发送失败,未获取到企业访问凭证");
            }
        } else {
            return R.fail("卡片消息发送失败，未获取到企业ID");
        }
    }

    @Override
    public R<Map> getUnassignedList(String corpId, String next_cursor) {

        R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
        if (corpAccessTokenResult.getCode() != R.SUCCESS) {
            return R.fail("获取企业访问凭证失败:" + corpAccessTokenResult.getMsg());
        }
        String url = String.format(WeChatUrlUtil.getUnassignedList, corpAccessTokenResult.getData());


        JSONObject jsonObject = new JSONObject();
        jsonObject.put("cursor", next_cursor);
        //每次返回的最大记录数，默认为1000，最大值为1000
        //jsonObject.put("page_size",1);

        Map resultMap = RestUtilsTwo.post(url, jsonObject);
        if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
            log.error("获取待分配的离职成员列表失败：#{}", resultMap.get("errmsg"));
            System.err.println("获取待分配的离职成员列表失败,失败原因:" + resultMap.get("errmsg"));
            return R.fail("获取待分配的离职成员列表失败,失败原因:" + resultMap.get("errmsg"));
        }
        return R.ok(resultMap);
    }


    /**
     * 查询用户群聊
     * @param corpId
     * @param userList
     * @return
     */
    @Override
    public R<List<Map>> getGroupChatList(String corpId, List<String> userList) {
        List<Map> dataList = new ArrayList<>();
        getGroupChatList(corpId, userList, "", dataList);
        if (dataList != null && dataList.size() > 0) {
            return R.ok(dataList);
        } else {
            return R.fail(null);
        }
    }

    @Override
    public R<Map> getGroupChatInfo(String corpId, String chatId) {
        if (StringUtils.isNotEmpty(corpId) && StringUtils.isNotEmpty(corpId)) {
            R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
            if (corpAccessTokenResult.getCode() == R.SUCCESS) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("chat_id", chatId);
                jsonObject.put("need_name", 1);
                String formatUrl = String.format(WeChatUrlUtil.getGroupChatInfo, corpAccessTokenResult.getData());
                Map resultMap = RestUtilsTwo.post(formatUrl, jsonObject);
                if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
                    log.error("获取群聊详情失败：" + resultMap);
                    return R.fail("获取群聊详情失败");
                } else {
                    Map groupChat = (Map) resultMap.get("group_chat");
                    return R.ok(groupChat);
                }
            } else {
                log.error("获取群聊详情失败：" + corpAccessTokenResult.getMsg());
                return R.fail("获取群聊详情失败");
            }
        } else {
            return R.fail("未获取到企业ID");
        }
    }

    @Override
    public R<String> getCorpToken_MYGZ(String corpId) {
        try {
            Map<String, Object> paramMap = new HashMap<>();
            Map<String, Object> dataMa = new HashMap<>();
            dataMa.put("corpId", corpId);
            paramMap.put("data", dataMa);
            String resutl = HttpUtil.post(WeChatUrlUtil.getCorpToken_MYGZ, JSON.toJSONString(paramMap));
            if (StringUtils.isEmpty(resutl)) {
                return R.fail();
            } else {
                Map resultMap = JSON.parseObject(resutl, Map.class);
                String encryptedSecret = MapUtils.getString(resultMap, "encrypted_secret");
                //编码
                return R.ok(encryptedSecret);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail();
        }

    }

    @Override
    public R<List<Map<String, Object>>> getSurveyQuestionnaireData(String token) {
        try {
            String encodedString = URLEncoder.encode(token, Constants.UTF8);
            String result = HttpUtil.get(String.format(WeChatUrlUtil.getSurveyQuestionnaireData, encodedString));
            if (StringUtils.isEmpty(result)) {
                return R.fail();
            } else {
                Map resultMap = JSON.parseObject(result, Map.class);
                if (resultMap.containsKey("code") && (Integer) resultMap.get("code") != 200) {
                    return R.fail();
                } else {
                    List<Map<String, Object>> resultDataList = (List) resultMap.get("data");
                    return R.ok(resultDataList);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail();
        }

    }

    @Override
    public R callBackSurveyQuestionnaireData(String token, List<String> idList) {
        String url = String.format(WeChatUrlUtil.callBackSurveyQuestionnaireData);
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("token", token);
        paramMap.put("data", idList);
        HttpUtil.post(url, JSON.toJSONString(paramMap));
        return R.ok();
    }

    @Override
    public R<Map> getAddressInfo(String location) {
        String url = String.format(WeChatUrlUtil.getAddressInfo, location);
        Map<String, Object> resultDataMap = new HashMap<>();
        resultDataMap.put("addressCode", "未知");
        resultDataMap.put("address", "未知");
        resultDataMap.put("more", "未知");
        try {
            String result = HttpUtil.get(url);
            if (StringUtils.isEmpty(result)) {
                return R.fail();
            } else {
                Map resultMap = JSON.parseObject(result, Map.class);
                Map addressMap = (Map) MapUtils.getObject(resultMap, "result");
                if (addressMap != null) {
                    if (addressMap.containsKey("address_component")) {
                        Map addressComponent = (Map) addressMap.get("address_component");
                        if (addressComponent != null) {
                            //道路
                            String street = MapUtils.getString(addressComponent, "street");
                            //门牌号
                            String street_number = MapUtils.getString(addressComponent, "street_number");
                            if (StringUtils.isNotEmpty(street) && StringUtils.isNotEmpty(street_number)) {
                                if (street_number.contains(street)) {
                                    //直接取门牌信息
                                    resultDataMap.put("more", street_number);
                                } else {
                                    //拼接道路和门牌号信息
                                    resultDataMap.put("more", street + street_number);
                                }
                            } else {
                                //直接取企业微信的推荐地址
                                Map formattedAddresses = (Map) addressMap.get("formatted_addresses");
                                if (formattedAddresses != null) {
                                    String recommend = MapUtils.getString(formattedAddresses, "recommend");
                                    if (StringUtils.isNotEmpty(recommend)) {
                                        resultDataMap.put("more", recommend);
                                    }
                                }
                            }
                        }
                    }
                    //省市区
                    if (addressMap.containsKey("ad_info")) {
                        Map adInfo = (Map) addressMap.get("ad_info");
                        if (adInfo != null) {
                            String address = "";
                            //省
                            String province = MapUtils.getString(adInfo, "province");
                            if (StringUtils.isNotEmpty(province)) {
                                address += province;
                            }
                            //市
                            String city = MapUtils.getString(adInfo, "city");
                            if (StringUtils.isNotEmpty(city)) {
                                address += city;
                            }
                            //区
                            String district = MapUtils.getString(adInfo, "district");
                            if (StringUtils.isNotEmpty(district)) {
                                address += district;
                            }
                            resultDataMap.put("address", address);
                            //城市代码
                            String adcode = MapUtils.getString(adInfo, "adcode");
                            if (StringUtils.isNotEmpty(adcode)) {
                                resultDataMap.put("addressCode", adcode);
                            }
                        }
                    }
                }
            }
            return R.ok(resultDataMap);
        } catch (Exception e) {
            e.printStackTrace();
            return R.ok(resultDataMap);
        }

    }

    @Override
    public R<String> getExternalAvatar(String externalUserId, String corpId) {
        //直接获取redis里的token  无需重复判断  有一个半小时
        //redis获取  三方公共token 不用拼接企业
        Object externalAccessToken = redisService.getCacheObject("third_external_accessToken");
        if (ObjectUtil.isEmpty(externalAccessToken)) {
            String accessToken = FriendAvatarUtil.getAccessToken();
            if (StringUtils.isEmpty(accessToken)) {
                log.error("获取第三方增强组件Token失败");
                return R.fail("获取第三方增强组件Token失败");
            } else {
                redisService.setCacheObject("third_external_accessToken", accessToken, 5400L, TimeUnit.SECONDS);
                String externalAvatar = FriendAvatarUtil.getExternalAvatar(accessToken, externalUserId);
                return R.ok(externalAvatar, "获取成功");
            }
        } else {
            String externalAvatar = FriendAvatarUtil.getExternalAvatar(externalAccessToken.toString(), externalUserId);
            return R.ok(externalAvatar, "获取成功");
        }
    }

    /*
    递归查询群聊
     */
    public void getGroupChatList(String corpId, List<String> userList, String next_cursor, List<Map> dataList) {
        R<String> corpAccessTokenResult = queryCorpAccessToken(corpId);
        if (corpAccessTokenResult.getCode() != R.SUCCESS) {
            log.error("获取用户群聊失败：" + corpAccessTokenResult.getMsg());
        } else {
            String formatUrl = String.format(WeChatUrlUtil.getGroupChatList, corpAccessTokenResult.getData());
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("status_filter", 0);
            Map<String, Object> ownerFileMap = new HashMap<>();
            ownerFileMap.put("userid_list", userList);
            jsonObject.put("owner_filter", ownerFileMap);
            jsonObject.put("cursor", next_cursor);
            jsonObject.put("limit", 1000);
            Map resultMap = RestUtilsTwo.post(formatUrl, jsonObject);
            if (resultMap.containsKey("errcode") && (Integer) resultMap.get("errcode") != 0) {
                log.error("获取用户群聊失败：" + JSON.toJSONString(resultMap));
            } else {
                List<Map> groupChatList = (List<Map>) resultMap.get("group_chat_list");
                if (groupChatList != null && groupChatList.size() > 0) {
                    dataList.addAll(groupChatList);
                } else {
                    return;
                }
                next_cursor = MapUtils.getString(resultMap, "next_cursor");
                if (StringUtils.isNotEmpty(next_cursor)) {
                    getGroupChatList(corpId, userList, next_cursor, dataList);
                }
            }
        }
    }
}
