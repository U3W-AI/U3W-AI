package com.cube.wechat.thirdapp.service;

import com.alibaba.fastjson.JSONObject;
import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.WeChatThirdCompany;
import com.cube.wechat.thirdapp.util.MarkTagParam;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 @author sjl
  * @Created date 2024/3/7 10:21
 */
public interface WeChatDataService {
    /**
     * 查询人员详情
     */
    public R<Map> queryPersonnelDetails(String userId, String accessToken);
    /**
     * 获取企业访问凭证
     */
    public R<String> queryCorpAccessToken(String corpId);
    /**
     * 获取服务商凭证
     */
    public R<String> queryServiceProviderSuiteToken();

    /**
     * 获取服务商登录凭证
     */
    public R<String> queryServiceProviderLoginSuiteToken();
    /**
     * 获取企业永久授权码
     */
    public R<Map> queryCorpPermanentCode(String corpAuthCode);
    /**
     * 获取授权部门集
     */
    public R<Map> queryCorpDepartmentIdList(String corpId);
    /**
     * 获取部门下人员
     */
    public R<Map> queryDepartmentChildUser(String corpAccessToken,String departmentId);
    /**
     * 获取用户身份
     */
    public R<Map> queryUserIdentity(String user_auth_code);
    /**
     * 扫码登录获取用户身份
     */
    public R<Map> queryScanCoeUserIdentity(String user_auth_code);
    /**
     * 查询用户敏感信息
     */
    public R<Map> queryUserSensitiveInformation(String userTicket);
    /**
     * 查询部门详情
     */
    public R<Map> queryDepartmentInfo(String corpId,String departmentId);
    /**
     * 获取企业js_api_ticket
     */
    public R<String> queryCorpJsApiTicket(String corpId);
    /***
     * 获取应用js_api_ticket
     */
    public R<String> queryAppJsapiTicket(String corpId);
    /**
     * 获取公司详情
     */
    public R<WeChatThirdCompany> queryCorpInfo(String corpId, String suiteId);
    /**
     *获取应用共享信息
     */
    public R<List<Map<String,Object>>>  queryAppShareInfo(String agentId,String corpId);

    /**
     *获取企业标签库
     */
    R<Map> getCorpTagList(String corpId);


    /**
     *查看是否删除
     */
    R<Map> checkCorpTagDeleted(JSONObject jsonObject,String corpId);

    /**
     *添加企业客户标签
     */
    R<Map> addCorpTag(JSONObject jsonObject, String corpId);

    /**
     * 添加企业客户标签-批量
     */
    R<Map> addCorpTagBatch(Map map,String  corpId);

    /**
     *编辑企业客户标签
     */
    R<Map> editCorpTag(JSONObject jsonObject, String corpId);

    /**
     *删除企业客户标签
     */
    R<Map> delCorpTag(JSONObject jsonObject, String corpId);

    /**
     * 更新所有企业访问凭证
     */
    R<Map> updateAllCorpAccessToken();
    /**
     * 查询客户列表
     */
    R<List<String>> queryExternalcontactList(String corpId,String userId);
    //批量查询客户详情
    R<List<Map<String,Object>>> queryBatchExternalcontactList(String corpId,String userId,String cursor);
    /**
     * 查询外部联系人详情
     */
    R<Map> queryExternalContactInfo(String corpId,String externalUserId,Integer next_cursor);
    /**
     * 编辑企业外部联系人标签
     */
    R updateCorpExternalContactTag(String corpId, MarkTagParam tagList);
    /**
     * 上传临时素材
     */
    R<Map> uploadTemporaryMaterials(File file) throws Exception;
    /**
     * 通讯录id转译
     */
    R<Map> addressBookIdTranslation(String corpId,String media_id);
    /**
     * 文件导出全流程
     */
    R<Map> fileExport(File file,String corpId) throws Exception;
    /**
     * 获取异步任务结果
     */
    R<String> getAsynchronousTaskResults(String jobId);
    /**
     * 发送卡片消息
     */
    R<Map> sendTextCardMessage(Map map);

    /**
     * 获取待分配的离职成员列表
     * @param corpId 企业id
     * @param next_cursor 分页游标
     * @return
     */
    R<Map> getUnassignedList(String corpId,String next_cursor);
    /**
     * 获取群聊
     */
    R<List<Map>> getGroupChatList(String corpId,List<String> userList);
    /**
     * 获取群聊详情
     */
    R<Map> getGroupChatInfo(String corpId,String chatId);
    /**
     * 获取企业token
     */
    R<String> getCorpToken_MYGZ(String corpId);
    /*
    获取调查问卷数据
     */
    R<List<Map<String,Object>>> getSurveyQuestionnaireData(String token);

    /**
     * 告知调查问卷处理结束
     */
    R callBackSurveyQuestionnaireData(String token,List<String> idList);
    /**
     * 查询企业微信位置信息
     */
    R<Map> getAddressInfo(String location);
    /**
     * 获取增强组件居民头像
     */
    public R<String> getExternalAvatar(String externalUserId,String corpId);
}
