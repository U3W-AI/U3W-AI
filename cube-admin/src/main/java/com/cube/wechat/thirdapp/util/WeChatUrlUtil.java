package com.cube.wechat.thirdapp.util;

/**
 * @author sjl 企业微信api 获取工具类
 * @Created date 2024/2/23 17:27
 */
public class WeChatUrlUtil {
    private static final String baseUrl = "https://qyapi.weixin.qq.com/cgi-bin/";

    //服务商相关
    private static final String serviceUrl = baseUrl + "service/";
    private static final String suiteTokenUrl = serviceUrl + "get_suite_token";
    /**
     * 获取第三方应用凭证（suite_access_token）
     */
    public static final String getSuiteToken = "https://qyapi.weixin.qq.com/cgi-bin/service/get_suite_token";
    /**
     * 获取预授权码。预授权码用于企业授权时的第三方服务商安全验证。
     */
    public static final String getPreAuthCode = " https://qyapi.weixin.qq.com/cgi-bin/service/get_pre_auth_code?suite_access_token=SUITE_ACCESS_TOKEN";
    public static final String sessionInfoUrl = serviceUrl + "set_session_info?suite_access_token=%s";
    /**
     * 使用临时授权码换取授权方的永久授权码，并换取授权信息、企业access_token，临时授权码一次有效。
     */
    public static final String getPermanentCode = "https://qyapi.weixin.qq.com/cgi-bin/service/get_permanent_code?suite_access_token=SUITE_ACCESS_TOKEN";

    public static final String permanentCodeUrl = serviceUrl + "get_permanent_code?suite_access_token=%s";

    public static final String providerTokenUlr = serviceUrl + "get_provider_token";
    public static final String registerCodeUrl = serviceUrl + "get_register_code?provider_access_token=%s";
    public static final String registerUrl = "https://open.work.weixin.qq.com/3rdservice/wework/register?register_code=%s";
    public static final String ssoAuthUrl = "https://open.work.weixin.qq.com/wwopen/sso/3rd_qrConnect?appid=%s&redirect_uri=%s&state=%s&usertype=%s";
    public static final String loginInfoUrl = serviceUrl + "get_login_info?access_token=%s";
    // H5应用
    //scope应用授权作用域。
    //snsapi_base：静默授权，可获取成员的基础信息（UserId与DeviceId）；
    //snsapi_userinfo：静默授权，可获取成员的详细信息，但不包含手机、邮箱等敏感信息；
    //snsapi_privateinfo：手动授权，可获取成员的详细信息，包含手机、邮箱等敏感信息（已不再支持获取手机号/邮箱）。
    //https://work.weixin.qq.com/api/doc/90001/90143/91120
    public static final String oauthUrl = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s#wechat_redirect";
    //https://work.weixin.qq.com/api/doc/90001/90143/91121
    public static final String oauthUserUrl = serviceUrl + "getuserinfo3rd?suite_access_token=%s&code=%s";
    //https://work.weixin.qq.com/api/doc/90001/90143/91122
    public static final String oauthUserDetailUrl = serviceUrl + "getuserdetail3rd?suite_access_token=%s";
    //https://work.weixin.qq.com/api/doc/90001/90144/90539
    public static final String jsapiTicketUrl = baseUrl + "get_jsapi_ticket?access_token=%s";
    //https://work.weixin.qq.com/api/doc/90001/90144/90539#%E8%8E%B7%E5%8F%96%E5%BA%94%E7%94%A8%E7%9A%84jsapi_ticket
    public static final String jsapiTicketAgentUrl = baseUrl + "ticket/get?access_token=%s&type=agent_config";
    //https://developer.work.weixin.qq.com/document/path/90605：获取企业凭证
    public static final String getCorpToken = serviceUrl + "get_corp_token?suite_access_token=%s";
    //获取授权范围内可见部门ID列表
    public static final String getDepartmentIdList=baseUrl+"department/simplelist?access_token=%s";
    //获取部门下人员列表
    public static final String getDepartmentChildUser=baseUrl+"user/simplelist?access_token=%s&department_id=%s";
    //获取人员详情
    public static final String getDepartmentChildUserInfo=baseUrl+"user/get?access_token=%s&userid=%s";
    //用户身份
    public static final String getuserinfo="https://qyapi.weixin.qq.com/cgi-bin/service/auth/getuserinfo3rd?suite_access_token=%s&code=%s";
    //用户敏感信息
    public static final String getuserdetail3rd="https://qyapi.weixin.qq.com/cgi-bin/service/auth/getuserdetail3rd?suite_access_token=%s";
    //获取指定的应用详情
    //https://developer.work.weixin.qq.com/document/path/90363
    public static final String getAgent=baseUrl+"/agent/get?access_token=%s&agentid=%s";

    //外部联系人  企业可通过此接口，根据外部联系人的userid（如何获取?），拉取客户详情。
    public static final String getExternalContact=baseUrl+"externalcontact/get?access_token=%s&external_userid=%s";
    //获取应用管理员
    public static final String getAgentAdminList=serviceUrl+"get_admin_list?suite_access_token=%s";
    //查询部门详情

    public static final String getDepartmentInfo=baseUrl+"department/get?access_token=%s&id=%s";
    //获取企业的jsapi_ticket

    public static final String getCorpJsApiTicket=baseUrl+"get_jsapi_ticket?access_token=%s";

    //获取应用的jsapi_ticket
    public static final String getAppJsApiTicket=baseUrl+"ticket/get?access_token=%s&type=agent_config";
    //获取应用共享信息
    public static final String getAppShareInfo="https://qyapi.weixin.qq.com/cgi-bin/corpgroup/corp/list_app_share_info?access_token=%s";
    //客户列表
    public static final String getExternalcontactList="https://qyapi.weixin.qq.com/cgi-bin/externalcontact/list?access_token=%s&userid=%s";

    //获取企业标签库
    public static final String getCorpTagList =baseUrl+"externalcontact/get_corp_tag_list?access_token=%s";

    //添加企业客户标签
    public static final String addCorpTag =baseUrl+"externalcontact/add_corp_tag?access_token=%s";

    //编辑企业客户标签
    public static final String editCorpTag =baseUrl+"externalcontact/edit_corp_tag?access_token=%s";

    //删除企业客户标签
    public static final String delCorpTag =baseUrl+"externalcontact/del_corp_tag?access_token=%s";

    //编辑客户企业标签
    public static final String markTag =baseUrl+"externalcontact/mark_tag?access_token=%s";
    //上传临时素材
    public static final String upload=serviceUrl+"media/upload?provider_access_token=%s&type=file";

    //通讯录id转译
    public static final String idTranslateUrl=serviceUrl+"contact/id_translate?provider_access_token=%s";
    //异步任务处理结果
    public static final String getAsynchronousTaskResults=serviceUrl+"batch/getresult?provider_access_token=%s&jobid=%s";
    //发送卡片消息
    public static final String sendTextCardMessage="https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=%s";
    //批量获取联系人
    public static final String getGetExternalContactBatch=baseUrl+"externalcontact/batch/get_by_user?access_token=%s";


    //获取待分配的离职成员列表
    public static final String getUnassignedList=baseUrl+"externalcontact/get_unassigned_list?access_token=%s";
    /**
     * 获取群聊
     */
    public static final  String getGroupChatList=baseUrl+"externalcontact/groupchat/list?access_token=%s";
    /**
     * 查询群聊详情
     */
    public static final String getGroupChatInfo=baseUrl+"externalcontact/groupchat/get?access_token=%s";

    /**
     * 获取企业token- 民意感知
     */
    public static final String getCorpToken_MYGZ="https://question-naires-7ggpce3v4c72c63f-1308445404.ap-shanghai.app.tcloudbase.com/reg?key=createToken";
    /**
     * 获取调查问卷数据-民意感知
     */
    public static final String getSurveyQuestionnaireData="https://question-naires-7ggpce3v4c72c63f-1308445404.ap-shanghai.app.tcloudbase.com/questionnaire_statistics?key=getExternalInfo&token=%s";
    /**
     * 回调调查问卷数据-民意感知
     */
    public static String callBackSurveyQuestionnaireData="https://question-naires-7ggpce3v4c72c63f-1308445404.ap-shanghai.app.tcloudbase.com/questionnaire_statistics?key=updateAnswerDB";
    /**
     * 企业微信位置服务
     */
    public static String getAddressInfo="https://apis.map.qq.com/ws/geocoder/v1/?location=%s&key=DLZBZ-AZFKU-RXSVJ-B2VLL-K4S2E-4ABGU";
}
