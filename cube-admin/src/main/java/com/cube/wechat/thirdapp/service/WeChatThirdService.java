package com.cube.wechat.thirdapp.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cube.common.core.domain.R;
import com.cube.common.utils.StringUtils;
import com.cube.wechat.thirdapp.aes.AesException;
import com.cube.wechat.thirdapp.aes.WeChatBizMsgCrypt;
import com.cube.wechat.thirdapp.aes.XMLParse;
import com.cube.wechat.thirdapp.constant.BasicConstant;
import com.cube.wechat.thirdapp.entiy.*;
import com.cube.wechat.thirdapp.mapper.RmRoleMapper;
import com.cube.wechat.thirdapp.mapper.RmUserRoleMapper;
import com.cube.wechat.thirdapp.param.*;
import com.cube.wechat.thirdapp.util.RedisService;
import com.cube.wechat.thirdapp.util.RestUtilsTwo;
import com.cube.wechat.thirdapp.util.WeChatUrlUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.poi.ss.formula.functions.T;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author AspireLife 企业微信第三方应用业务类
 * @version JDK 1.8
 * @date 2024年08月06日 09:24
 */
@Service
public class WeChatThirdService {

    private final static Logger logger = LoggerFactory.getLogger(WeChatThirdService.class);


    @Autowired
    private RedisService redisService;

    @Autowired
    private BasicConstant constant;

    @Autowired
    private WeChatDataService weChatDataService;

    @Autowired
    private WeChatThirdCompanyService weChatThirdCompanyService;

    @Autowired
    private WeChatCallbackLogService weChatCallbackLogService;

    @Autowired
    private WeChatThirdUserService weChatThirdUserService;

    @Autowired
    private WeChatUserDepartmentService weChatUserDepartmentService;

    @Autowired
    private WeChatCorpUserService weChatCorpUserService;

    @Autowired
    private WeChatCorpRelationshipService weChatCorpRelationshipService;

    @Autowired
    private WeChatCorpDepartmentService weChatCorpDepartmentService;

    @Autowired
    private ILabelService iLabelService;

    @Autowired
    private ExternalContactService externalContactService;

    @Autowired
    private ExternalGroupService externalGroupService;

    @Autowired
    private RmRoleMapper rmRoleMapper;

    @Autowired
    private RmUserRoleMapper rmUserRoleMapper;

    /**
     * 指令回调url验证 get请求
     *
     * @param sVerifyMsgSig
     * @param sVerifyTimeStamp
     * @param sVerifyNonce
     * @param sVerifyEchoStr
     * @return
     */
    public String getVerify(String sVerifyMsgSig, String sVerifyTimeStamp, String sVerifyNonce, String sVerifyEchoStr) {

        String sToken = constant.getTOKEN();
        String sCorpID = constant.getCorpID();
        String sEncodingAESKey = constant.getEncodingAESKey();

        WeChatBizMsgCrypt wxcpt = null;
        try {
            wxcpt = new WeChatBizMsgCrypt(sToken, sEncodingAESKey, sCorpID);
        } catch (AesException E) {
            return "error";
        }
        String sEchoStr; //需要返回的明文
        try {
            sEchoStr = wxcpt.VerifyURL(sVerifyMsgSig, sVerifyTimeStamp, sVerifyNonce, sVerifyEchoStr);
        } catch (Exception e) {
            //验证URL失败，错误原因请查看异常
            e.printStackTrace();
            return "error";
        }
        return sEchoStr;
    }


    /**
     * 登录授权指令回调
     *
     * @param sVerifyMsgSig
     * @param sVerifyTimeStamp
     * @param sVerifyNonce
     * @param sVerifyEchoStr
     * @return
     */
    public String getLoginVerify(String sVerifyMsgSig, String sVerifyTimeStamp, String sVerifyNonce, String sVerifyEchoStr) {

        String sToken = constant.getLoginTOKEN();
        String sCorpID = constant.getCorpID();
        String sEncodingAESKey = constant.getLoginEncodingAESKey();

        WeChatBizMsgCrypt wxcpt = null;
        try {
            wxcpt = new WeChatBizMsgCrypt(sToken, sEncodingAESKey, sCorpID);
        } catch (AesException E) {
            return "error";
        }
        String sEchoStr; //需要返回的明文
        try {
            sEchoStr = wxcpt.VerifyURL(sVerifyMsgSig, sVerifyTimeStamp, sVerifyNonce, sVerifyEchoStr);
        } catch (Exception e) {
            //验证URL失败，错误原因请查看异常
            e.printStackTrace();
            return "error";
        }
        return sEchoStr;
    }



    public String loginInstructCallback(String sVerifyMsgSig, String sVerifyTimeStamp, String sVerifyNonce, String sData) {
        String sToken = constant.getLoginTOKEN();
        String sEncodingAESKey = constant.getLoginEncodingAESKey();
        String sSuiteid = constant.getLoginSuiteId();
        String result = "error";
        WeChatBizMsgCrypt wxcpt = null;
        try {
            wxcpt = new WeChatBizMsgCrypt(sToken, sEncodingAESKey, sSuiteid);
        } catch (AesException E) {
            return result;
        }
        try {
            String sMsg = wxcpt.DecryptMsg(sVerifyMsgSig, sVerifyTimeStamp, sVerifyNonce, sData);
            System.out.println("after encrypt sEncrytMsg: " + sMsg);
            // 加密成功
            // TODO: 解析出明文xml标签的内容进行处理
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(sMsg)));
            Element root = document.getDocumentElement();
            NodeList infoTypeNode = root.getElementsByTagName("InfoType");
            String infoType = infoTypeNode.item(0).getTextContent();
            //回调指令类型
            logger.info("infoType-------------------->" + infoType);
            switch (infoType) {
                //刷新 suite_ticket
                case "suite_ticket":
                    //缓存suite_ticket
                    NodeList nodelist = root.getElementsByTagName("SuiteTicket");
                    String SuiteTicket = nodelist.item(0).getTextContent();
                    System.out.println(SuiteTicket);
                    setLoginSuitTicket(root);
                    break;
                default:
                    logger.info(infoType);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 加密失败
            return result;
        }
        result = "success";
        return result;
    }


    /**
     * 指令回调接收 post请求处理
     *
     * @param sVerifyMsgSig
     * @param sVerifyTimeStamp
     * @param sVerifyNonce
     * @param sData
     * @return
     */
    public String instructCallback(String sVerifyMsgSig, String sVerifyTimeStamp, String sVerifyNonce, String sData) {
        String sToken = constant.getTOKEN();
        String sCorpID = constant.getCorpID();
        String sEncodingAESKey = constant.getEncodingAESKey();
        String sSuiteid = constant.getSuiteID();
        String result = "error";
        WeChatBizMsgCrypt wxcpt = null;
        try {
            wxcpt = new WeChatBizMsgCrypt(sToken, sEncodingAESKey, sSuiteid);
        } catch (AesException E) {
            return result;
        }
        try {
            String sMsg = wxcpt.DecryptMsg(sVerifyMsgSig, sVerifyTimeStamp, sVerifyNonce, sData);
            System.out.println("after encrypt sEncrytMsg: " + sMsg);
            // 加密成功
            // TODO: 解析出明文xml标签的内容进行处理
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(sMsg)));
            Element root = document.getDocumentElement();
            NodeList infoTypeNode = root.getElementsByTagName("InfoType");
            String infoType = infoTypeNode.item(0).getTextContent();

            //封装企微回调日志
            WeChatCallbackLog qywxCallbackLog = new WeChatCallbackLog();
            qywxCallbackLog.setType(infoType);
            qywxCallbackLog.setParam(sMsg);
            //'1：指令回调
            qywxCallbackLog.setInterfaceType(1);

            //回调指令类型
            logger.info("infoType-------------------->" + infoType);
            switch (infoType) {
                //刷新 suite_ticket
                case "suite_ticket":
                    //缓存suite_ticket
                    String suitTicket = setSuitTicket(root);
                    if (StringUtils.isNotEmpty(suitTicket)) {
                        //更新suite_ticketToken
                        weChatDataService.queryServiceProviderSuiteToken();
                        //异步更新所有企业的访问秘钥
                        CompletableFuture.runAsync(() -> weChatDataService.updateAllCorpAccessToken());
                    }
                    break;
                case "create_auth"://授权成功通知
                    //获取auth_code
                    NodeList authcodeNode = root.getElementsByTagName("AuthCode");
                    String authcode = authcodeNode.item(0).getTextContent();
                    logger.info("企业临时授权码auth code------------------->:" + authcode);
                    logger.info("instructCallback==============>" + authcode);
                    qywxCallbackLog.setTypeDescribe("授权成功通知");
                    CompletableFuture.runAsync(() -> createAuth(authcode, qywxCallbackLog));
                    break;
                //授权变更
                case "change_auth":
                    //获取corp_id
                    NodeList changeAuthCorpNode = root.getElementsByTagName("AuthCorpId");
                    String changeCorpId = changeAuthCorpNode.item(0).getTextContent();
                    //保存企微回调日志
                    qywxCallbackLog.setTypeDescribe("变更授权通知");
                    qywxCallbackLog.setCorpId(changeCorpId);
                    saveCallBackLog(qywxCallbackLog);
                    //异步执行事件处理
                    CompletableFuture.runAsync(() -> changeAuth(changeCorpId, ""));
                    break;
                case "cancel_auth":
                    //获取corp_id
                    NodeList authCorpNode = root.getElementsByTagName("AuthCorpId");
                    String corpId = authCorpNode.item(0).getTextContent();
                    //保存企微回调日志
                    qywxCallbackLog.setTypeDescribe("变更授权通知");
                    qywxCallbackLog.setCorpId(corpId);
                    saveCallBackLog(qywxCallbackLog);

                    CompletableFuture.runAsync(() -> cancelAuth(corpId));
                    break;
                //变更授权
                case "corp_arch_auth":
                    NodeList corpArchAuthNode = root.getElementsByTagName("AuthCorpId");
                    String corpArchCorId = corpArchAuthNode.item(0).getTextContent();
                    //保存企微回调日志
                    qywxCallbackLog.setTypeDescribe("授权组织架构权限通知");
                    qywxCallbackLog.setCorpId(corpArchCorId);
                    saveCallBackLog(qywxCallbackLog);
                    CompletableFuture.runAsync(() -> changeAuth(corpArchCorId, ""));
                    break;
                case "register_corp":
                    NodeList stateNode = root.getElementsByTagName("state");
                    String state = stateNode.item(0).getTextContent();
                    logger.info("state :" + state);
                    break;
                case "batch_job_result":
                    //通讯录id转译异步任务回调  https://open.work.weixin.qq.com/api/doc/90001/90143/91875
                    break;
                //企业客户标签创建事件
                case "change_external_tag":
                    //企业id
                    String change_external_tag_corpId = root.getElementsByTagName("AuthCorpId").item(0).getTextContent();
                    //应用id
                    String change_external_tag_suiteId = root.getElementsByTagName("SuiteId").item(0).getTextContent();
                    //创建标签时，此项为tag，创建标签组时，此项为tag_group
                    String tagType = root.getElementsByTagName("TagType").item(0).getTextContent();
                    //类型
                    String change_external_tag_ChangeType = root.getElementsByTagName("ChangeType").item(0).getTextContent();
                    String change_external_tag_id = root.getElementsByTagName("Id").item(0).getTextContent();

                    qywxCallbackLog.setCorpId(change_external_tag_corpId);
                    if (change_external_tag_ChangeType.equals("create")) {
                        qywxCallbackLog.setTypeDescribe("企业客户标签创建事件");
                    } else if (change_external_tag_ChangeType.equals("update")) {
                        qywxCallbackLog.setTypeDescribe("企业客户标签变更事件");
                    } else if (change_external_tag_ChangeType.equals("delete")) {
                        qywxCallbackLog.setTypeDescribe("企业客户标签删除事件");
                    } else if (change_external_tag_ChangeType.equals("shuffle")) {
                        qywxCallbackLog.setTypeDescribe("企业客户标签重排事件");
                    }

                    //异步处理客户标签
                    TagParam tagParam = new TagParam();
                    tagParam.setCorpId(change_external_tag_corpId);
                    tagParam.setSuiteId(change_external_tag_suiteId);
                    tagParam.setTagType(tagType);
                    tagParam.setChangeType(change_external_tag_ChangeType);
                    tagParam.setTagId(change_external_tag_id);

                    logger.info("tagParam:{}", tagParam);
                    CompletableFuture.runAsync(() -> changeExternalTag(tagParam));
                    saveCallBackLog(qywxCallbackLog);

                    break;

                //客户群变更事件
                case "change_external_chat":
                    ExternalChatParam externalChatParam = new ExternalChatParam();
                    //企业id
                    String change_external_chat_corpId = root.getElementsByTagName("AuthCorpId").item(0).getTextContent();
                    //应用id
                    String change_external_chat_suiteId = root.getElementsByTagName("SuiteId").item(0).getTextContent();
                    //类型
                    String change_external_chat_ChangeType = root.getElementsByTagName("ChangeType").item(0).getTextContent();
                    //变更详情
                    String change_external_chat_UpdateDetail = "";
                    NodeList updateDetailNodeList = root.getElementsByTagName("UpdateDetail");
                    if (updateDetailNodeList != null && updateDetailNodeList.item(0) != null) {
                        change_external_chat_UpdateDetail = updateDetailNodeList.item(0).getTextContent();
                        externalChatParam.setUpdateDetail(change_external_chat_UpdateDetail);
                    }
                    // 获取MemChangeList元素
                    NodeList memChangeLists = root.getElementsByTagName("MemChangeList");
                    List<String> memChangeIdList = new ArrayList<>();
                    if (memChangeLists.getLength() > 0) {
                        Element memChangeList = (Element) memChangeLists.item(0);
                        // 获取MemChangeList下的Item元素
                        NodeList items = memChangeList.getElementsByTagName("Item");
                        // 输出Item元素的值
                        for (int i = 0; i < items.getLength(); i++) {
                            String userId = items.item(i).getTextContent();
                            memChangeIdList.add(userId);
                        }
                    }

                    //退群方式
                    NodeList quitScene = root.getElementsByTagName("QuitScene");
                    if (quitScene != null && quitScene.getLength() > 0) {
                        String quitSceneStr = quitScene.item(0).getTextContent();
                        if (StringUtils.isNotEmpty(quitSceneStr)) {
                            externalChatParam.setQuitScene(Integer.parseInt(quitSceneStr));
                        }
                    }
                    externalChatParam.setMemChangeList(memChangeIdList);
                    //群聊id
                    String change_external_chat_ChatId = root.getElementsByTagName("ChatId").item(0).getTextContent();
                    externalChatParam.setCorpId(change_external_chat_corpId);
                    externalChatParam.setSuiteId(change_external_chat_suiteId);
                    externalChatParam.setChangeType(change_external_chat_ChangeType);

                    NodeList curMemVerNodeList = root.getElementsByTagName("CurMemVer");
                    if (curMemVerNodeList != null && curMemVerNodeList.getLength() > 0) {
                        Node versionItem = curMemVerNodeList.item(0);
                        if (versionItem != null) {
                            String curmever = versionItem.getTextContent();
                            externalChatParam.setCurMemVer(curmever);
                        }
                    }
                    externalChatParam.setChatId(change_external_chat_ChatId);
                    //保存企微回调日志
                    qywxCallbackLog.setCorpId(change_external_chat_corpId);
                    //群创建
                    if (change_external_chat_ChangeType.equals("create")) {
                        qywxCallbackLog.setTypeDescribe("客户群变更_创建群");
                    } else if (change_external_chat_ChangeType.equals("update")) {
                        if (StringUtils.isNotEmpty(change_external_chat_UpdateDetail)) {
                            if (change_external_chat_UpdateDetail.equals("add_member")) {
                                qywxCallbackLog.setTypeDescribe("客户群变更_成员入群");
                            } else if (change_external_chat_UpdateDetail.equals("del_member")) {
                                qywxCallbackLog.setTypeDescribe("客户群变更_成员退群");
                            } else if (change_external_chat_UpdateDetail.equals("change_owner")) {
                                qywxCallbackLog.setTypeDescribe("客户群变更_群主变更");
                            } else if (change_external_chat_UpdateDetail.equals("change_name")) {
                                qywxCallbackLog.setTypeDescribe("客户群变更_群名变更");
                            } else if (change_external_chat_UpdateDetail.equals("change_notice")) {
                                qywxCallbackLog.setTypeDescribe("客户群变更_群公告变更");
                            }

                        }
                    } else if (change_external_chat_ChangeType.equals("dismiss")) {
                        qywxCallbackLog.setTypeDescribe("客户群变更_群解散");
                    }
                    saveCallBackLog(qywxCallbackLog);
                    //异步处理事件任务
                    CompletableFuture.runAsync(() -> changeExternalChat(externalChatParam));
                    break;
                case "change_external_contact":
                    //外部联系人变更回调  https://open.work.weixin.qq.com/api/doc/90001/90143/91875
                    //获取外部联系人事件类型
                    //获取 外部联系人的userid，注意不是企业成员的账号
                    String contact_authCorpId = root.getElementsByTagName("AuthCorpId").item(0).getTextContent();

                    //保存企微回调日志
                    qywxCallbackLog.setTypeDescribe("外部联系人变更回调");
                    qywxCallbackLog.setCorpId(contact_authCorpId);
                    saveCallBackLog(qywxCallbackLog);

                    R<String> corpAccessTokenResult = weChatDataService.queryCorpAccessToken(contact_authCorpId);
                    if (corpAccessTokenResult.getCode() == R.SUCCESS) {
                        //新增外部联系人
                        //TODO 获取外部联系人信息
                        WeChatExternalContactParam weChatExternalContactParam = new WeChatExternalContactParam();
                        weChatExternalContactParam.setCorpId(contact_authCorpId);
                        weChatExternalContactParam.setSuiteId(root.getElementsByTagName("SuiteId").item(0).getTextContent());
                        weChatExternalContactParam.setAccessToken(corpAccessTokenResult.getData());
                        weChatExternalContactParam.setChangeType(root.getElementsByTagName("ChangeType").item(0).getTextContent());
                        weChatExternalContactParam.setUserId(root.getElementsByTagName("UserID").item(0).getTextContent());
                        weChatExternalContactParam.setExternalUserId(root.getElementsByTagName("ExternalUserID").item(0).getTextContent());
                        //异步执行事件处理
                        CompletableFuture.runAsync(() -> externalContactChangeNotice(weChatExternalContactParam));
                    }
                    break;
                //通讯录变更
                case "change_contact":
                    String suiteId = root.getElementsByTagName("SuiteId").item(0).getTextContent();
                    String authCorpId = root.getElementsByTagName("AuthCorpId").item(0).getTextContent();
                    String change_contact_type = root.getElementsByTagName("ChangeType").item(0).getTextContent();
                    //保存企微回调日志

                    //部门变更相关
                    if (change_contact_type.contains("party")) {
                        qywxCallbackLog.setTypeDescribe("部门变更");
                        //异步执行事件处理
                        DepartmentChangeParam departmentChangeParam = new DepartmentChangeParam();
                        departmentChangeParam.setSuiteId(suiteId);
                        departmentChangeParam.setCorpId(authCorpId);
                        departmentChangeParam.setChangeType(change_contact_type);
                        departmentChangeParam.setDepartmentId(root.getElementsByTagName("Id").item(0).getTextContent());
                        CompletableFuture.runAsync(() -> departmentChangeEventNotice(departmentChangeParam));
                    } else {
                        qywxCallbackLog.setTypeDescribe("成员变更");
                        //成员变更相关
                        MemberChangeParam memberChangeParam = new MemberChangeParam();
                        memberChangeParam.setCorpId(authCorpId);
                        memberChangeParam.setChangeType(change_contact_type);
                        memberChangeParam.setOpenUserId(root.getElementsByTagName("OpenUserID").item(0).getTextContent());
                        memberChangeParam.setSuiteId(suiteId);
                        memberChangeParam.setUserId(root.getElementsByTagName("UserID").item(0).getTextContent());
                        //异步执行事件处理
                        CompletableFuture.runAsync(() -> memberChangeNotice(memberChangeParam));
                    }
                    qywxCallbackLog.setCorpId(authCorpId);
                    saveCallBackLog(qywxCallbackLog);
                    break;
                //共享应用事件回调-企业互联共享应用事件回调
                case "share_agent_change":
                    //保存企微回调日志
                    qywxCallbackLog.setTypeDescribe("企业互联共享应用事件回调");
                    qywxCallbackLog.setCorpId(root.getElementsByTagName("CorpId").item(0).getTextContent());
                    saveCallBackLog(qywxCallbackLog);

                    //异步执行事件处理
                    ShareAgentChangeParam shareAgentChangeParam = new ShareAgentChangeParam();
                    shareAgentChangeParam.setCorpId(root.getElementsByTagName("CorpId").item(0).getTextContent());
                    shareAgentChangeParam.setSuiteId(root.getElementsByTagName("SuiteId").item(0).getTextContent());
                    shareAgentChangeParam.setAgentId(root.getElementsByTagName("AgentId").item(0).getTextContent());
                    CompletableFuture.runAsync(() -> shareAgentChangeNotice(shareAgentChangeParam));
                    break;
                //共享应用事件回调-上下游共享应用事件回调
                case "share_chain_change":

                    //保存企微回调日志
                    qywxCallbackLog.setTypeDescribe("上下游共享应用事件回调");
                    qywxCallbackLog.setCorpId(root.getElementsByTagName("CorpId").item(0).getTextContent());
                    saveCallBackLog(qywxCallbackLog);
                    //异步执行事件处理
                    ShareAgentChangeParam shareAgentChangeParam2 = new ShareAgentChangeParam();
                    shareAgentChangeParam2.setCorpId(root.getElementsByTagName("CorpId").item(0).getTextContent());
                    shareAgentChangeParam2.setSuiteId(root.getElementsByTagName("SuiteId").item(0).getTextContent());
                    shareAgentChangeParam2.setAgentId(root.getElementsByTagName("AgentId").item(0).getTextContent());
                    CompletableFuture.runAsync(() -> shareAgentChangeNotice(shareAgentChangeParam2));
                    break;
                default:
                    logger.info(infoType);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 加密失败
            return result;
        }
        result = "success";
        return result;
    }


    private void changeExternalTag(TagParam tagParam) {

        /**
         *  create  企业客户标签创建事件
         *  update   企业客户标签变更事件
         *  delete   企业客户标签删除事件
         *  shuffle  企业客户标签删除事件
         */
        String changeType = tagParam.getChangeType();

        if (changeType.equals("create")) {
            logger.info("=============================开始企业客户标签创建===================================");
            R<Map> externalGroup = iLabelService.createExternalTag(tagParam);
            if (externalGroup.getCode() == R.SUCCESS) {
                logger.info("=============================企业客户标签创建成功===================================");
            } else {
                logger.error("=============================企业客户标签创建失败===================================");
            }
        } else if (changeType.equals("update")) {
            logger.info("=============================开始企业客户标签变更===================================");
            R<Map> externalGroup = iLabelService.updateExternalTag(tagParam);
            if (externalGroup.getCode() == R.SUCCESS) {
                logger.info("=============================企业客户标签变更成功===================================");
            } else {
                logger.error("=============================企业客户标签变更失败===================================");
            }
        } else if (changeType.equals("delete")) {
            logger.info("=============================开始企业客户标签删除==================================");
            R<Map> externalGroup = iLabelService.deleteExternalTag(tagParam);
            if (externalGroup.getCode() == R.SUCCESS) {
                logger.info("=============================企业客户标签删除成功===================================");
            } else {
                logger.error("=============================企业客户标签删除失败===================================");
            }
        }

    }

    //suite_ticket缓存
    private String setSuitTicket(Element root) {
        NodeList nodelist = root.getElementsByTagName("SuiteTicket");
        String result = nodelist.item(0).getTextContent();
        logger.info("=================刷新SuiteTicket================");
        logger.info("setKey" + constant.getSuitTicket());
        logger.info("setValue" + result);
        redisService.setCacheObject(constant.getSuitTicket(), result, 1200L, TimeUnit.SECONDS);
        return result;
    }


    public void changeExternalChat(ExternalChatParam externalChatParam) {
        try {
            //变更类型
            String changeType = externalChatParam.getChangeType();
            Map<String, Object> paramMap = new HashMap<>();
            String chatId = externalChatParam.getChatId();
            paramMap.put("corpId", externalChatParam.getCorpId());
            paramMap.put("chatId", chatId);
            paramMap.put("version", externalChatParam.getCurMemVer());
            //创建群
            if (changeType.equals("create")) {
                logger.info("=============================开始创建群聊===================================");
                R<Map> externalGroup = externalGroupService.createExternalGroup(paramMap);
                if (externalGroup.getCode() == R.SUCCESS) {
                    logger.info("=============================群聊创建成功===================================");
                } else {
                    logger.error("=============================群聊创建失败===================================");
                }
            } else if (changeType.equals("update")) {
                //群变更
                String updateDetail = externalChatParam.getUpdateDetail();
                if (StringUtils.isNotEmpty(updateDetail)) {
                    if (updateDetail.equals("change_name") || updateDetail.equals("change_notice")) {
                        logger.info("=============================开始群信息变更===================================");
                        R<Map> mapR = externalGroupService.updateExternalGroup(paramMap);
                        if (mapR.getCode() == R.SUCCESS) {
                            logger.info("=============================群信息变更成功===================================");
                        } else {
                            logger.error("=============================群信息变更失败===================================");
                        }
                        logger.info("=============================群信息变更结束===================================");
                    } else if (updateDetail.equals("add_member")) {
                        List<String> memChangeList = externalChatParam.getMemChangeList();
                        if (memChangeList != null && memChangeList.size() > 0) {
                            paramMap.put("userList", memChangeList);
                            logger.info("=============================开始添加群成员===================================");
                            //添加成员
                            externalGroupService.addGrouChatMember(paramMap);
                            logger.info("=============================群成员添加结束===================================");
                        }
                    } else if (updateDetail.equals("del_member")) {
                        //成员退群
                        List<String> memChangeList = externalChatParam.getMemChangeList();
                        if (memChangeList != null && memChangeList.size() > 0) {
                            logger.info("=============================开始退出群成员===================================");
                            paramMap.put("userList", memChangeList);
                            paramMap.put("quitScene", externalChatParam.getQuitScene());
                            externalGroupService.delGroupChatMember(paramMap);
                            logger.info("=============================退出群成员结束===================================");
                        }
                    } else if (updateDetail.equals("change_owner")) {
                        //变更群主
                        logger.info("=============================开始变更群主===================================");
                        externalGroupService.updateGroupChatOwner(paramMap);
                        logger.info("=============================变更群主结束===================================");
                    }
                }

            } else if (changeType.equals("dismiss")) {
                //群解散
                logger.info("=============================开始解散群聊===================================");
                externalGroupService.dismissGroupChat(paramMap);
                logger.info("=============================解散群聊结束===================================");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String setLoginSuitTicket(Element root) {
        NodeList nodelist = root.getElementsByTagName("SuiteTicket");
        String result = nodelist.item(0).getTextContent();
        logger.info("=================刷新loginSuiteTicket================");
        logger.info("setKey" + constant.getLoginSuitTicket());
        logger.info("setValue" + result);
        redisService.setCacheObject(constant.getLoginSuitTicket(), result, 600L, TimeUnit.SECONDS);
        return result;
    }

    /**
     * 成员变更事件通知
     *
     * @return
     */
    public boolean memberChangeNotice(MemberChangeParam memberChangeParam) {
        String changeType = memberChangeParam.getChangeType();
        logger.info("============================开始执行企业成员变更事件=============================");
        //更新/新增企业成员
        if (changeType.equals("update_user") || changeType.equals("create_user")) {
            R<String> corpAccessTokenR = weChatDataService.queryCorpAccessToken(memberChangeParam.getCorpId());
            if (corpAccessTokenR.getCode() == R.SUCCESS) {
                //查询用户详情
                R<Map> personnelDetailR = weChatDataService.queryPersonnelDetails(memberChangeParam.getUserId(), corpAccessTokenR.getData());
                if (personnelDetailR.getCode() == R.SUCCESS) {
                    WeChatCorpUser weChatCorpUser = new WeChatCorpUser();
                    Map userDetailData = personnelDetailR.getData();
                    String mainDepartment = MapUtils.getString(userDetailData, "main_department");
                    Integer status = MapUtils.getInteger(userDetailData, "status");
                    //主部门id
                    weChatCorpUser.setDepartmentId(mainDepartment);
                    weChatCorpUser.setStatus(status);
                    List<Integer> orderList = (List<Integer>) userDetailData.get("order");
                    if (orderList != null && orderList.size() > 0) {
                        Integer order = orderList.get(0);
                        weChatCorpUser.setOrder(order);
                    }
                    //获取人员所属部门列表
                    List<Integer> departmentList = (List<Integer>) userDetailData.get("department");
                    if (departmentList != null && departmentList.size() > 0) {
                        if (StringUtils.isEmpty(mainDepartment)) {
                            //默认第一个为主部门
                            weChatCorpUser.setDepartmentId(departmentList.get(0).toString());
                        }
                    }
                    //查询是否是可见范围内的顶级成员
                    List<String> useridList = getAppAgentUser(memberChangeParam.getCorpId());
                    if (useridList != null && useridList.size() > 0) {
                        boolean contains = useridList.contains(memberChangeParam.getUserId());
                        if (contains) {
                            departmentList.add(1);
                        }
                    }
                    weChatCorpUser.setDepartmentIdList(departmentList);
                    String thumbAvatar = MapUtils.getString(userDetailData, "thumb_avatar");
                    weChatCorpUser.setThumbAvatar(thumbAvatar);
                    weChatCorpUser.setUserId(memberChangeParam.getUserId());
                    String openUserid = MapUtils.getString(userDetailData, "open_userid");
                    weChatCorpUser.setOpenUserid(openUserid);
                    weChatCorpUser.setCorpId(memberChangeParam.getCorpId());
                    weChatCorpUser.setSuiteId(constant.getSuiteID());
                    List<WeChatCorpUser> userArrayList = new ArrayList<>();
                    userArrayList.add(weChatCorpUser);
                    weChatCorpUserService.saveCorpUser(userArrayList);
                    //同步外部联系人
                    synExternalContacts(memberChangeParam.getCorpId(), memberChangeParam.getUserId());
                    //同步该成员的外部群聊
                    //同步企业所有群聊
                    synExternalGroup(memberChangeParam.getCorpId(), memberChangeParam.getUserId());
                }
            }
        }//删除企业成员
        else if (changeType.equals("delete_user")) {
            WeChatCorpUser weChatCorpUser = new WeChatCorpUser();
            weChatCorpUser.setUserId(memberChangeParam.getUserId());
            weChatCorpUser.setSuiteId(memberChangeParam.getSuiteId());
            weChatCorpUser.setCorpId(memberChangeParam.getCorpId());
            weChatCorpUser.setStatus(6);
            weChatCorpUser.setOpenUserid(memberChangeParam.getOpenUserId());
            weChatCorpUserService.deleteCorpUser(weChatCorpUser);
        }

        //todo 同步 es
//        rmEsSynDataService.saveEsData(memberChangeParam.getUserId(), null, memberChangeParam.getCorpId(), EsSynDataUpdateEnum.UPDATE_USER.getCode(), 1);
        logger.info("============================执行企业成员变更事件结束=============================");
        return true;
    }

    /**
     * 退出成员
     */
    public void exitMember(String corpId, String userId) {
        WeChatCorpUser weChatCorpUser = new WeChatCorpUser();
        weChatCorpUser.setUserId(userId);
        weChatCorpUser.setSuiteId(constant.getSuiteID());
        weChatCorpUser.setCorpId(corpId);
        weChatCorpUser.setStatus(5);
        weChatCorpUser.setOpenUserid(userId);
        R<Map> resultR = weChatCorpUserService.deleteCorpUser(weChatCorpUser);
//        if (resultR.getCode() == R.SUCCESS) {
//            //todo 同步 es
//            rmEsSynDataService.saveEsData(userId, null, corpId, EsSynDataUpdateEnum.UPDATE_USER.getCode(), 1);
//        }
    }

    /**
     * 共享应用事件回调
     */
    public void shareAgentChangeNotice(ShareAgentChangeParam shareAgentChangeParam) {
        //查询上游企业应用信息
        logger.info("===================开始处理应用共享============================");
        logger.info("参数:" + JSON.toJSONString(shareAgentChangeParam));
        WeChatThirdCompany weChatThirdCompany = new WeChatThirdCompany();
        weChatThirdCompany.setCorpId(shareAgentChangeParam.getCorpId());
        weChatThirdCompany.setSuiteId(shareAgentChangeParam.getSuiteId());
        R<WeChatThirdCompany> thirdCompanyR = weChatThirdCompanyService.selectCompanyInfo(weChatThirdCompany);
        if (thirdCompanyR.getCode() == R.SUCCESS) {
            WeChatThirdCompany data = thirdCompanyR.getData();
            if (data != null) {
                //查询上游企业对应下游企业
                R<List<Map<String, Object>>> appShareInfo = weChatDataService.queryAppShareInfo(shareAgentChangeParam.getAgentId(), shareAgentChangeParam.getCorpId());
                logger.info("企业关联信息:" + JSON.toJSONString(appShareInfo));
                if (appShareInfo.getCode() == R.SUCCESS) {
                    Map<String, Object> paramMap = new HashMap<>();
                    paramMap.put("corpId", shareAgentChangeParam.getCorpId());
                    paramMap.put("suiteId", shareAgentChangeParam.getSuiteId());
                    weChatCorpRelationshipService.deleteQywxCorpRelationShip(paramMap);
                    List<Map<String, Object>> shareInfoData = appShareInfo.getData();
                    if (CollectionUtils.isNotEmpty(shareInfoData)) {
                        for (Map<String, Object> shareInfoDatum : shareInfoData) {
                            weChatCorpRelationshipService.saveQywxCorpRelationship(shareInfoDatum);
                        }
                    }
                    logger.info("===================处理应用共享结束============================");
                } else {
                    logger.error("上下游企业获取失败");
                    logger.error("===================处理应用共享失败============================");
                }
            } else {
                logger.error("共享应用事件回调，上游企业不存在");
                logger.error("===================处理应用共享失败:上游企业不存在============================");
            }
        }
    }

    /**
     * 部门变更事件通知
     *
     * @return
     */
    public boolean departmentChangeEventNotice(DepartmentChangeParam departmentChangeParam) {
        String changeType = departmentChangeParam.getChangeType();
        WeChatCorpDepartment weChatCorpDepartment = new WeChatCorpDepartment();
        weChatCorpDepartment.setSuiteId(departmentChangeParam.getSuiteId());
        weChatCorpDepartment.setCorpId(departmentChangeParam.getCorpId());
        weChatCorpDepartment.setDepartmentId(departmentChangeParam.getDepartmentId());
        if (changeType.equals("create_party") || changeType.equals("update_party")) {
            //查询部门详情
            R<Map> departmentInfoR = weChatDataService.queryDepartmentInfo(departmentChangeParam.getCorpId(), departmentChangeParam.getDepartmentId());
            if (departmentInfoR.getCode() == R.SUCCESS) {
                Map departmentInfoRData = departmentInfoR.getData();
                Map departmentMap = (Map) MapUtils.getObject(departmentInfoRData, "department");
                if (departmentMap != null) {
                    String parentid = MapUtils.getString(departmentMap, "parentid");
                    if (StringUtils.isNotEmpty(parentid)) {
                        weChatCorpDepartment.setDepartmentParentId(parentid);
                    }
                    String order = MapUtils.getString(departmentMap, "order");
                    if (StringUtils.isNotEmpty(order)) {
                        weChatCorpDepartment.setDepartmentOrder(order);
                    }
                }
            }
            R<T> tr = weChatCorpDepartmentService.updateCorpDepartment(weChatCorpDepartment);
//            if (tr.getCode() == R.SUCCESS) {
//                //todo 同步 es
//                rmEsSynDataService.saveEsData(departmentChangeParam.getDepartmentId(), null, departmentChangeParam.getCorpId(), EsSynDataUpdateEnum.UPDATE_DEPT.getCode(), 1);
//            }
        } else {
            //删除部门
            R<T> tr = weChatCorpDepartmentService.deleteCorpDepartment(weChatCorpDepartment);
//            if (tr.getCode() == R.SUCCESS) {
//                //todo 同步 es
//                rmEsSynDataService.saveEsData(departmentChangeParam.getDepartmentId(), null, departmentChangeParam.getCorpId(), EsSynDataUpdateEnum.UPDATE_DEPT.getCode(), 1);
//            }
        }

        return true;
    }

    public String externalContactChangeNotice(WeChatExternalContactParam weChatExternalContactParam) {
        //todo 查询企业对应的服务名
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("corpId", weChatExternalContactParam.getCorpId());
        paramMap.put("suiteId", weChatExternalContactParam.getSuiteId());
        //查询公司配置的的服务名
        //企业成员删除外部联系人 或 成员被外部联系人删除
        String changeType = weChatExternalContactParam.getChangeType();
        if (changeType.equals("del_external_contact") || changeType.equals("del_follow_user")) {
            Map<String, Object> deleteParamMap = new HashMap<>();
            deleteParamMap.put("corpId", weChatExternalContactParam.getCorpId());
            deleteParamMap.put("suiteId", weChatExternalContactParam.getSuiteId());
            deleteParamMap.put("userId", weChatExternalContactParam.getUserId());
            deleteParamMap.put("externalUserId", weChatExternalContactParam.getExternalUserId());
            //2、把客户删除 3 被客户删除
            switch (changeType) {
                case "del_external_contact":
                    deleteParamMap.put("status", 2);
                    break;
                case "del_follow_user":
                    deleteParamMap.put("status", 3);
                    break;
            }
            System.out.println("调用居民信息删除服务参数：：" + JSON.toJSONString(deleteParamMap));
            R<RmExternalStatisticsNum> rmExternalStatisticsNumR = externalContactService.deleteExternalContract(deleteParamMap);
//            if (rmExternalStatisticsNumR.getCode() == R.SUCCESS) {
//                //todo 同步 es
//                rmEsSynDataService.saveEsData(qywxExternalContactParam.getUserId(), qywxExternalContactParam.getExternalUserId(), qywxExternalContactParam.getCorpId(), EsSynDataUpdateEnum.UPDATE_EXTERNAL.getCode(), 1);
//            }
        } else if (changeType.equals("add_external_contact") || changeType.equals("edit_external_contact")) {

            //通过OpenFeign调用远程服务
            logger.info("调用居民信息存储服务参数：：" + JSON.toJSONString(weChatExternalContactParam));
            R<RmExternalStatisticsNum> externalStatisticsNumR = externalContactService.addExternalContract(weChatExternalContactParam);
//            if (externalStatisticsNumR.getCode() == R.SUCCESS) {
//                if (changeType.equals("add_external_contact")) {
//                    //todo 同步 es
//                    rmEsSynDataService.saveEsData(qywxExternalContactParam.getUserId(), qywxExternalContactParam.getExternalUserId(), weChatExternalContactParam.getCorpId(), EsSynDataUpdateEnum.ADD_EXTERNAL.getCode(), 1);
//                } else {
//                    //todo 同步 es
//                    rmEsSynDataService.saveEsData(qywxExternalContactParam.getUserId(), qywxExternalContactParam.getExternalUserId(), weChatExternalContactParam.getCorpId(), EsSynDataUpdateEnum.UPDATE_EXTERNAL.getCode(), 1);
//                }
//            }
        }
        return "success";
    }



    public String changeAuth(String corpId, String userId) {
        //将企业原人员数据设置为无效
        deleteCorpUser(corpId);
        //权限变更时，重新拉取部门、人员数据
        asyncSynCorpDeptAndUser(corpId);
        //一同步该企业外部联系人数据
        synExternalContacts(corpId, userId);
        //同步该企业的外部群聊数据
        synExternalGroup(corpId, userId);

        //todo 同步 es
       // rmEsSynDataService.saveEsData(null, null, corpId, EsSynDataUpdateEnum.UPDATE_DEPT.getCode(), 0);
        return "success";
    }

    public String dataCallback(String sVerifyMsgSig, String sVerifyTimeStamp, String sVerifyNonce, String sData) {

        String sToken = constant.getTOKEN();
        String sEncodingAESKey = constant.getEncodingAESKey();

        String result = "error";
        WeChatBizMsgCrypt wxcpt = null;
        try {
            // ReceiveId 在各个场景的含义不同：
            //企业应用的回调，表示corpid
            //第三方事件的回调，表示suiteid
            //但一般为推送过来的ToUserName
            Object[] encrypt = XMLParse.extract(sData);
            String sSuiteid = (String) encrypt[2];
            wxcpt = new WeChatBizMsgCrypt(sToken, sEncodingAESKey, sSuiteid);
        } catch (AesException E) {
            return result;
        }
        try {
            String sMsg = wxcpt.DecryptMsg(sVerifyMsgSig, sVerifyTimeStamp, sVerifyNonce, sData);
            System.out.println("after encrypt sEncrytMsg: " + sMsg);
            // 加密成功
            // TODO: 解析出明文xml标签的内容进行处理
            // For example:
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            StringReader sr = new StringReader(sMsg);
            InputSource is = new InputSource(sr);
            Document document = db.parse(is);

            Element root = document.getDocumentElement();

            //获取消息类型
            // 如果是事件  <MsgType><![CDATA[event]]></MsgType>
            //https://open.work.weixin.qq.com/api/doc/90001/90143/90376
            //如果是成员给应用发送消息  <MsgType><![CDATA[text]]></MsgType> 文本 图片 语音 视频  位置 链接等
            //https://open.work.weixin.qq.com/api/doc/90001/90143/90375
            NodeList msgTypeNode = root.getElementsByTagName("MsgType");
            String msgType = msgTypeNode.item(0).getTextContent();
            //封装企微回调日志
            WeChatCallbackLog weChatCallbackLog = new WeChatCallbackLog();
            weChatCallbackLog.setParam(sMsg);
            //'1：数据回调
            weChatCallbackLog.setInterfaceType(2);
            System.out.println("msgType: " + msgType);
            if (msgType.equals("event")) {
                NodeList eventNode = root.getElementsByTagName("Event");
                String event = eventNode.item(0).getTextContent();
                logger.info(event);
                weChatCallbackLog.setType(event);
                switch (event) {
                    case "subscribe":
                        //加入成员
                        //获取corp_id
                        String subscribe_corpId = document.getElementsByTagName("ToUserName").item(0).getTextContent();
                        String subscribe_userId = document.getElementsByTagName("FromUserName").item(0).getTextContent();
                        //保存企业回调日志记录
                        weChatCallbackLog.setCorpId(subscribe_corpId);
                        weChatCallbackLog.setTypeDescribe("加入成员");
                        saveCallBackLog(weChatCallbackLog);
                     /*   //一同步该企业外部联系人数据
                        CompletableFuture.runAsync(() -> synExternalContacts(subscribe_corpId,subscribe_userId));*/
                        //CompletableFuture.runAsync(() -> changeAuth(subscribe_corpId,subscribe_userId));
                        break;
                    case "unsubscribe":
                        //退出成员
                        //获取corp_id
                        String unsubscribe_corpId = document.getElementsByTagName("ToUserName").item(0).getTextContent();
                        String unsubscribe_userId = document.getElementsByTagName("FromUserName").item(0).getTextContent();
                        //保存企业回调日志记录
                        weChatCallbackLog.setCorpId(unsubscribe_corpId);
                        weChatCallbackLog.setTypeDescribe("退出成员");
                        saveCallBackLog(weChatCallbackLog);
                        CompletableFuture.runAsync(() -> exitMember(unsubscribe_corpId, unsubscribe_userId));
                        break;
                    //第三方应用审批状态变化通知回调 https://work.weixin.qq.com/api/doc/90001/90143/93798
                    case "open_approval_change":
                        break;
                    //管理员变更
                    case "change_app_admin":
                        //获取corp_id
                        NodeList toUserNameNode = document.getElementsByTagName("ToUserName");
                        String changeAdminCorpId = toUserNameNode.item(0).getTextContent();
                        //保存企业回调日志记录
                        weChatCallbackLog.setCorpId(changeAdminCorpId);
                        weChatCallbackLog.setTypeDescribe("管理员变更");
                        saveCallBackLog(weChatCallbackLog);

                        //异步执行管理员变更
                        CompletableFuture.runAsync(() -> asyncSynCorpAppAdmin(changeAdminCorpId));
                        break;
                    case "click":
                        break;
                    case "view":

                        String createTime = String.valueOf(System.currentTimeMillis());
                        String nonce = "sdfsdfsd";

                        //文本消息
                      /*  MessageText msgText = new MessageText();
                        msgText.setToUserName("");
                        msgText.setFromUserName("");
                        msgText.setCreateTime(createTime);
                        msgText.setMsgType("text");
                        msgText.setContent("test");
                        msgText.setMsgId("111111111111111111");
                        msgText.setAgentID("");
                        XmlConvertUtils.convertToXml(msgText,"utf-8");*/
                        String msgTextXmlStr = "";
                        //加密消息xml
                        result = wxcpt.EncryptMsg(msgTextXmlStr, createTime, nonce);
                        break;
                    default:
                        logger.info(event);
                }
            } else {
                switch (msgType) {
                    case "text":
                        break;
                    case "image":
                        break;
                    case "voice":
                        break;
                    case "location":
                        break;
                    case "video":
                        break;
                    case "link":
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 加密失败
            System.out.println(result);
            return result;
        }
        result = "success";
        return result;

    }

    public String registerCallback(String sVerifyMsgSig, String sVerifyTimeStamp, String sVerifyNonce, String sData) {
        String sToken = constant.getTOKEN();
        String sCorpID = constant.getCorpID();
        String sEncodingAESKey = constant.getEncodingAESKey();
        String sSuiteid = constant.getSuiteID();
        String result = "error";
        WeChatBizMsgCrypt wxcpt = null;
        try {
            wxcpt = new WeChatBizMsgCrypt(sToken, sEncodingAESKey, sSuiteid);
        } catch (AesException E) {
            return result;
        }
        try {
            String sMsg = wxcpt.DecryptMsg(sVerifyMsgSig, sVerifyTimeStamp, sVerifyNonce, sData);
            System.out.println("after encrypt sEncrytMsg: " + sMsg);
            // 加密成功
            // TODO: 解析出明文xml标签的内容进行处理
            // For example:
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            StringReader sr = new StringReader(sMsg);
            InputSource is = new InputSource(sr);
            Document document = db.parse(is);

            Element root = document.getDocumentElement();
            NodeList infoTypeNode = root.getElementsByTagName("InfoType");
            String infoType = infoTypeNode.item(0).getTextContent();
            logger.info(infoType);
            //保存日志
            WeChatCallbackLog weChatCallbackLog = new WeChatCallbackLog();
            weChatCallbackLog.setType(infoType);
            weChatCallbackLog.setParam(sMsg);
            //'3：register
            weChatCallbackLog.setInterfaceType(3);
            switch (infoType) {
                case "create_auth":
                    //获取auth_code
                    NodeList authcodeNode = root.getElementsByTagName("AuthCode");
                    String authcode = authcodeNode.item(0).getTextContent();
                    logger.info("auth code:" + authcode);
                    logger.info("registerCallback==============>" + authcode);
                    weChatCallbackLog.setTypeDescribe("授权成功通知");
                    CompletableFuture.runAsync(() -> createAuth(authcode, weChatCallbackLog));
                    break;
                case "register_corp":
                    NodeList stateNode = root.getElementsByTagName("state");
                    String state = stateNode.item(0).getTextContent();
                    logger.info("state :" + state);
                    break;

                default:
                    logger.info(infoType);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 加密失败
            return result;
        }
        result = "success";
        return result;

    }

    /**
     * 系统回调url验证 get请求
     *
     * @param sVerifyMsgSig
     * @param sVerifyTimeStamp
     * @param sVerifyNonce
     * @param sVerifyEchoStr
     * @return
     */
    public String systemEventCallbacksVerify(String sVerifyMsgSig, String sVerifyTimeStamp, String sVerifyNonce, String sVerifyEchoStr) {

        String sToken = constant.getGeneralDevelopmentParameters_Token();
        String sCorpID = constant.getCorpID();
        String sEncodingAESKey = constant.getGeneralDevelopmentParameters_EncodingAESKey();

        WeChatBizMsgCrypt wxcpt = null;
        try {
            wxcpt = new WeChatBizMsgCrypt(sToken, sEncodingAESKey, sCorpID);
        } catch (AesException E) {
            return "error";
        }
        String sEchoStr; //需要返回的明文
        try {
            sEchoStr = wxcpt.VerifyURL(sVerifyMsgSig, sVerifyTimeStamp, sVerifyNonce, sVerifyEchoStr);
        } catch (Exception e) {
            //验证URL失败，错误原因请查看异常
            e.printStackTrace();
            return "error";
        }
        return sEchoStr;
    }


    public String eventCallbacks(String sVerifyMsgSig, String sVerifyTimeStamp, String sVerifyNonce, String sData) {
        //转发请求
        String sToken = constant.getGeneralDevelopmentParameters_Token();
        String sEncodingAESKey = constant.getGeneralDevelopmentParameters_EncodingAESKey();
        String corpID = constant.getCorpID();
        String result = "error";
        WeChatBizMsgCrypt wxcpt = null;
        try {
            wxcpt = new WeChatBizMsgCrypt(sToken, sEncodingAESKey, corpID);
        } catch (AesException E) {
            return result;
        }
        try {
            String sMsg = wxcpt.DecryptMsg(sVerifyMsgSig, sVerifyTimeStamp, sVerifyNonce, sData);
            System.out.println("after encrypt sEncrytMsg: " + sMsg);
            // 加密成功
            // TODO: 解析出明文xml标签的内容进行处理
            // For example:
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            StringReader sr = new StringReader(sMsg);
            InputSource is = new InputSource(sr);
            Document document = db.parse(is);

            Element root = document.getDocumentElement();
            NodeList infoTypeNode = root.getElementsByTagName("InfoType");
            String infoType = infoTypeNode.item(0).getTextContent();
            logger.info(infoType);
            //保存日志
            WeChatCallbackLog weChatCallbackLog = new WeChatCallbackLog();
            weChatCallbackLog.setType(infoType);
            weChatCallbackLog.setParam(sMsg);
            //'4：job
            weChatCallbackLog.setInterfaceType(4);
            switch (infoType) {
                //batch_job_result
                case "batch_job_result":
                    //获取auth_code
                    NodeList authcodeNode = root.getElementsByTagName("AuthCode");
                    String authcode = authcodeNode.item(0).getTextContent();
                    logger.info("auth code:" + authcode);
                    logger.info("registerCallback==============>" + authcode);
//                    qywxCallbackLog.setTypeDescribe("授权成功通知");
//                    CompletableFuture.runAsync(() -> createAuth(authcode,qywxCallbackLog));
                    break;
                case "register_corp":
                    NodeList stateNode = root.getElementsByTagName("state");
                    String state = stateNode.item(0).getTextContent();
                    logger.info("state :" + state);
                    break;

                default:
                    logger.info(infoType);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 加密失败
            return result;
        }
        result = "success";
        return result;

    }


    /**
     * 取消授权（应用删除、卸载）
     *
     * @param corpId constant.getCorpAccessToken()
     * @return
     */
    public String cancelAuth(String corpId) {
        deleteCompany(corpId);
        deleteAuthUser(corpId);
        deleteAuthDepartment(corpId);
        //清除缓存数据
        redisService.deleteObject(constant.getCorpAccessToken() + "_" + constant.getSuiteID() + "_" + corpId);
        //清除appjsTicket缓存
        redisService.deleteObject("AppJSAPI_" + constant.getSuiteID() + "_" + corpId);
        //清除corpJSticket
        redisService.deleteObject("CorpJSAPI_" + constant.getSuiteID() + "_" + corpId);
        deleteCorpUser(corpId);
        //删除用户部门绑定数据
        deleteUserDepartment(corpId);
        //删除该企业企业微信管理员数据
        deleteUserRole(corpId);
        return "success";
    }

    public boolean deleteUserRole(String corpId) {
        //获取企业微信管理员角色id
        RmRole rmRole = rmRoleMapper.selectRoleByRoleCode(constant.getWechatAdministrator());
        if (rmRole != null) {
            //清除当前企业微信管理员角色对应人员
            rmUserRoleMapper.deleteUserRoleByCorpIdAndRoleId(corpId, rmRole.getId());
        }
        return true;
    }

    public void saveCallBackLog(WeChatCallbackLog weChatCallbackLog) {
        //数据处理
        weChatCallbackLog.setId(UUID.randomUUID().toString());
        weChatCallbackLog.setCreateTime(new Date());
        weChatCallbackLog.setSuiteId(constant.getSuiteID());
        weChatCallbackLogService.saveCallBackLog(weChatCallbackLog);
    }

    public Boolean createAuth(String authCode, WeChatCallbackLog qywxCallbackLog) {
        Object isChuli = redisService.getCacheObject(authCode + "_check");
        if (ObjectUtil.isNotEmpty(isChuli)) {
            //已被处理
            return true;
        } else {
            // 检查该请求是否已经被标记为已处理
            redisService.setCacheObject(authCode + "_check", "1", 60L, TimeUnit.SECONDS);
        }
        R<Map> corpPermanentCodeResult = weChatDataService.queryCorpPermanentCode(authCode);
        if (corpPermanentCodeResult.getCode() != R.SUCCESS) {
            logger.error(corpPermanentCodeResult.getMsg());
            return false;
        }

        logger.info("临时授权码获取公司信息:" + JSON.toJSONString(corpPermanentCodeResult));
        Map corpPermanentCodeData = corpPermanentCodeResult.getData();
        //获取永久授权码
        String permanenCode = (String) corpPermanentCodeData.get("permanent_code");
        //获取corpId
        Map authCorpInfo = (Map) corpPermanentCodeData.get("auth_corp_info");
        String corpId = (String) authCorpInfo.get("corpid");
        //获取agent
        Map authInfo = (Map) corpPermanentCodeData.get("auth_info");
        List agentList = (List) authInfo.get("agent");
        Map agent = (Map) agentList.get(0);
        Integer agentId = (Integer) agent.get("agentid");

        WeChatThirdCompany company = new WeChatThirdCompany();
        company.setPermanentCode(permanenCode);
        company.setCorpId(corpId);
        company.setCorpName((String) authCorpInfo.get("corp_name"));
        String fullName = authCorpInfo.get("corp_full_name") == null ? "" : (String) authCorpInfo.get("corp_full_name");
        company.setCorpFullName(fullName);
        company.setSubjectType((Integer) authCorpInfo.get("subject_type") + "");
        //设置授权应用id  用于Jssdk agentconfig等使用
        company.setAgentId(String.valueOf(agentId));
        company.setStatus(1);
        company.setSuiteId(constant.getSuiteID());

        logger.info("================================公司信息=======================================");
        logger.info(JSON.toJSONString(company));
        weChatThirdCompanyService.saveCompanyInfo(company);
        WeChatThirdUser user = new WeChatThirdUser();
        Map authUserInfo = (Map) corpPermanentCodeData.get("auth_user_info");
        user.setUserId((String) authUserInfo.get("open_userid"));
        user.setName((String) authUserInfo.get("name"));
        user.setAvatar((String) authUserInfo.get("avatar"));
        user.setCorpId(corpId);
        user.setStatus(1);
        user.setSuiteId(constant.getSuiteID());
        logger.info("授权人员:" + user.toString());
        weChatThirdUserService.saveQywxThirdAuthUser(user);

        //保存企业微信回调日志
        qywxCallbackLog.setCorpId(corpId);
        saveCallBackLog(qywxCallbackLog);

        asyncSynCorpLabel(corpId);


        //异步同步部门，人员
        asyncSynCorpDeptAndUser(corpId);
        //初始化企业 反诈标签
        asyncSynInitializeAntifraudLabel(corpId);
        //同步企业所有外部联系人
        synExternalContacts(corpId, "");
        //同步企业所有群聊
        synExternalGroup(corpId, "");

        //初始化权限

        logger.info("==============================================================授权结束========================================================================");
        return true;

    }

    /**
     * 初始化系统反诈画像标签
     *
     * @param corpId
     */
    public void asyncSynInitializeAntifraudLabel(String corpId) {
        iLabelService.initializeAntifraudLabel(corpId);
    }

    /**
     * 同步该企业标签数据
     */

    public void asyncSynCorpLabel(String corpId) {
        logger.info("===================开始获取该企业标签数据=================");
        iLabelService.getQywxLabelData(corpId);
        logger.info("===================获取该企业标签数据结束=================");
    }

    /**
     * 同步该企业应用管理员
     *
     * @param corpId
     */
    void asyncSynCorpAppAdmin(String corpId) {

        //获取企业微信管理员角色id
        RmRole rmRole = rmRoleMapper.selectRoleByRoleCode(constant.getWechatAdministrator());
        if (rmRole != null) {
            //清除当前企业微信管理员角色对应人员
            rmUserRoleMapper.deleteUserRoleByCorpIdAndRoleId(corpId, rmRole.getId());
        }
        //获取suiteAccessToken
        R<String> suiteTokenResult = weChatDataService.queryServiceProviderSuiteToken();
        if (suiteTokenResult.getCode() != R.SUCCESS) {
            logger.error(suiteTokenResult.getMsg());
            return;
        }
        String suiteToken = suiteTokenResult.getData();
        String getAgenAdminListUrl = String.format(WeChatUrlUtil.getAgentAdminList, suiteToken);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("auth_corpid", corpId);
        //获取公司信息
        R<String> corpAccessTokenResult = weChatDataService.queryCorpAccessToken(corpId);
        if (corpAccessTokenResult.getCode() != R.SUCCESS) {
            logger.error(corpAccessTokenResult.getMsg());
            return;
        }
        String accessToken = corpAccessTokenResult.getData();
        Object companyInfoObject = redisService.getCacheObject(constant.getSuiteID() + "_" + corpId);
        WeChatThirdCompany thirdCompany = JSON.parseObject(companyInfoObject.toString(), WeChatThirdCompany.class);
        jsonObject.put("agentid", thirdCompany.getAgentId());
        JSONObject resultObject = RestUtilsTwo.post(getAgenAdminListUrl, jsonObject);
        if (resultObject.containsKey("errcode") && (Integer) resultObject.get("errcode") != 0) {
            logger.error("获取企业应用管理员失败");
            logger.error(JSON.toJSONString(resultObject));
        } else {
            logger.info("获取企业应用管理员成功");
            List<Map> adminList = (List<Map>) resultObject.get("admin");
            if (CollectionUtils.isNotEmpty(adminList)) {
                for (Map adminMap : adminList) {
                    List<WeChatCorpUser> paramUserList = new ArrayList<>();
                    String userid = MapUtils.getString(adminMap, "userid");
                    String openUserid = MapUtils.getString(adminMap, "open_userid");
                    String authType = MapUtils.getString(adminMap, "auth_type");
                    //不是管理权限,不认为是管理员
                    if (!authType.equals("1")) {
                        continue;
                    }
                    R<Map> userInfoResult = weChatDataService.queryPersonnelDetails(userid, accessToken);
                    if (userInfoResult.getCode() != 200) {
                        //说明身份是管理员，但看不见应用，不同步
                        continue;
                    } else {
                        Map userInfoMap = userInfoResult.getData();
                        WeChatCorpUser weChatCorpUser = new WeChatCorpUser();
                        String mainDepartment = MapUtils.getString(userInfoMap, "main_department");
                        Integer status = MapUtils.getInteger(userInfoMap, "status");
                        //主部门id
                        weChatCorpUser.setDepartmentId(mainDepartment);
                        weChatCorpUser.setStatus(status);
                        List<Integer> orderList = (List<Integer>) userInfoMap.get("order");
                        if (orderList != null && orderList.size() > 0) {
                            Integer order = orderList.get(0);
                            weChatCorpUser.setOrder(order);
                        }
                        //获取人员所属部门列表
                        List<Integer> departmentList = (List<Integer>) userInfoMap.get("department");
                        if (departmentList != null && departmentList.size() > 0) {
                            if (StringUtils.isEmpty(mainDepartment)) {
                                //默认第一个为主部门
                                weChatCorpUser.setDepartmentId(departmentList.get(0).toString());
                            }
                        }
                        //查询是否是可见范围内的顶级成员
                        List<String> useridList = getAppAgentUser(corpId);
                        if (useridList != null && useridList.size() > 0) {
                            boolean contains = useridList.contains(userid);
                            if (contains) {
                                departmentList.add(1);
                            }
                        }
                        weChatCorpUser.setDepartmentIdList(departmentList);
                        String thumbAvatar = MapUtils.getString(userInfoMap, "thumb_avatar");
                        weChatCorpUser.setThumbAvatar(thumbAvatar);
                        weChatCorpUser.setUserId(userid);
                        weChatCorpUser.setOpenUserid(openUserid);
                        weChatCorpUser.setCorpId(corpId);
                        weChatCorpUser.setSuiteId(constant.getSuiteID());
                        paramUserList.add(weChatCorpUser);
                        R<List<WeChatCorpUser>> corpUserR = weChatCorpUserService.saveCorpUser(paramUserList);
                        //保存角色数据 默认为企业管理员角色
                        List<WeChatCorpUser> weChatCorpUserList = corpUserR.getData();
                        if (weChatCorpUserList != null && weChatCorpUserList.size() > 0) {
                            for (WeChatCorpUser weChatCorpUser1 : weChatCorpUserList) {
                                String systemUserId = weChatCorpUser1.getId();
                                //不存在
                                String role_id = "notPresent";
                                if (rmRole != null) {
                                    role_id = rmRole.getId();
                                }
                                //写入用户和角色关联关系
                                RmUserRole rmUserRole = new RmUserRole();
                                rmUserRole.setId(UUID.randomUUID().toString());
                                rmUserRole.setSystemUserId(systemUserId);
                                rmUserRole.setUserId(userid);
                                rmUserRole.setCreateDate(new Date());
                                rmUserRole.setRoleId(role_id);
                                rmUserRole.setCorpId(corpId);
                                //查询是否已存在
                                List<RmUserRole> rmUserRoles = rmUserRoleMapper.selectUserRoleByUserId(rmUserRole);
                                if (rmUserRoles != null && rmUserRoles.size() > 0) {
                                    continue;
                                } else {
                                    rmUserRoleMapper.insertSelective(rmUserRole);
                                }

                            }

                        }
                    }
                }


            }
        }
    }

    public void asyncSynCorpDeptAndUser(String corpId) {
        logger.info("---------------------开始执行异步方法--------------------------");
        logger.info("===================开始清除用户关联部门数据=================");
        deleteUserDepartment(corpId);
        logger.info("===================清除用户关联部门数据结束=================");
        logger.info("===================开始清除企业部门数据=================");
        deleteAuthDepartment(corpId);
        logger.info("===================清除企业部门数据结束=================");
        logger.info("===================开始获取应用可见范围的人员数据=================");
        synCorpAppAgentUser(corpId);
        logger.info("===================获取应用可见范围的人员数据结束=================");
        logger.info("===================开始获取应用管理员的人员数据=================");
        asyncSynCorpAppAdmin(corpId);
        logger.info("===================获取应用管理员的人员数据结束=================");

        // 异步执行方法
        //获取企业访问配置
        logger.info("===================开始获取企业部门数据=================");
  /*      R<String> corpAccessTokenR = weChatDataService.queryCorpAccessToken(corpId);
        if (corpAccessTokenR.getCode() != R.SUCCESS) {
            logger.error(corpAccessTokenR.getMsg());
            return;
        }
        String accessToken = corpAccessTokenR.getData();
        if (StringUtils.isNotEmpty(accessToken)) {
            R<Map> departmentIdListR = weChatDataService.queryCorpDepartmentIdList(corpId);
            if (departmentIdListR.getCode() != R.SUCCESS) {
                logger.error("===================获取企业部门数据失败=================");
                return;
            } else {
                logger.info("===================开始获取企业人员数据=================");
                Map departmentIdListRData = departmentIdListR.getData();
                List<QywxCorpDepartment> departmentIdList = new ArrayList<>();
                List<Map> departmentIdMapList = (List<Map>) departmentIdListRData.get("department_id");
                if (CollectionUtils.isNotEmpty(departmentIdMapList)) {
                    for (Map map : departmentIdMapList) {
                        List<QywxCorpDepartmentParam> paramDeptList = new ArrayList<>();
                        QywxCorpDepartment qywxCorpDepartment = new QywxCorpDepartment();
                        String departmentId = MapUtils.getString(map, "id");
                        qywxCorpDepartment.setDepartmentId(departmentId);
                        qywxCorpDepartment.setDepartmentParentId(MapUtils.getString(map, "parentid"));
                        qywxCorpDepartment.setDepartmentOrder(MapUtils.getString(map, "order"));
                        departmentIdList.add(qywxCorpDepartment);
                        R<Map> childUserR = weChatDataService.queryDepartmentChildUser(accessToken, departmentId);
                        if (childUserR.getCode() == R.SUCCESS) {
                            Map responseUserMap = childUserR.getData();
                            List<Map> userList = (List<Map>) responseUserMap.get("userlist");
                            if (CollectionUtils.isNotEmpty(userList)) {
                                for (Map userMap : userList) {
                                    List<QywxCorpUser> paramUserList = new ArrayList<>();
                                    QywxCorpUser qywxCorpUser = new QywxCorpUser();
                                    //String userid =  MapUtils.getString(userMap, "open_userid");
                                    String userid= MapUtils.getString(userMap, "userid");
                                    String open_userid = MapUtils.getString(userMap, "open_userid");
                                    R<Map> userInfoR = weChatDataService.queryPersonnelDetails(userid, accessToken);
                                    if (userInfoR.getCode() == R.SUCCESS) {
                                        Map userInfoMap = userInfoR.getData();
                                        logger.error("===================获取人员详情数据成功=================\n" + JSON.toJSONString(userInfoMap));
                                        String mainDepartment = MapUtils.getString(userInfoMap, "main_department");
                                        Integer status = MapUtils.getInteger(userInfoMap, "status");
                                        //主部门id
                                        qywxCorpUser.setDepartmentId(mainDepartment);
                                        qywxCorpUser.setStatus(status);
                                        List<Integer> orderList = (List<Integer>) userInfoMap.get("order");
                                        if (orderList != null && orderList.size() > 0) {
                                            Integer order = orderList.get(0);
                                            qywxCorpUser.setOrder(order);
                                        }
                                        //获取人员所属部门列表
                                        List<Integer> departmentList = (List<Integer>) userInfoMap.get("department");
                                        if (departmentList != null && departmentList.size() > 0) {
                                            if (StringUtils.isEmpty(mainDepartment)) {
                                                //默认第一个为主部门
                                                qywxCorpUser.setDepartmentId(departmentList.get(0).toString());
                                            }
                                        }
                                        qywxCorpUser.setDepartmentIdList(departmentList);
                                        String thumbAvatar = MapUtils.getString(userInfoMap, "thumb_avatar");
                                        qywxCorpUser.setThumbAvatar(thumbAvatar);
                                    }
                                    qywxCorpUser.setUserId(userid);
                                    qywxCorpUser.setOpenUserid(open_userid);
                                    qywxCorpUser.setCorpId(corpId);
                                    //qywxCorpUser.setUserName(userName);
                                    qywxCorpUser.setSuiteId(constant.getSuiteID());
                                    paramUserList.add(qywxCorpUser);
                                    qywxCorpUserService.saveCorpUser(paramUserList);
                                }
                            }
                        }
                        QywxCorpDepartmentParam qywxCorpDepartmentParam = new QywxCorpDepartmentParam();
                        qywxCorpDepartmentParam.setCorpId(corpId);
                        qywxCorpDepartmentParam.setSuiteId(constant.getSuiteID());
                        qywxCorpDepartmentParam.setQywxCorpDepartmentList(departmentIdList);
                        paramDeptList.add(qywxCorpDepartmentParam);
                        qywxCorpDepartmentService.saveCorpDepartment(paramDeptList);
                    }
                }
            }


        }

*/
        R<Map> mapR = weChatUserDepartmentService.synchronizeUserDepartment(corpId, constant.getSuiteID());

        logger.info("---------------------异步方法执行结束--------------------------");
    }

    /**
     * 同步外部联系人数据
     *
     * @param corpId
     */
    public void synExternalContacts(String corpId, String userId) {
        logger.info("---------------------开始异步同步外部联系人数据--------------------------");
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("corpId", corpId);
        paramMap.put("suiteId", constant.getSuiteID());
        paramMap.put("userId", userId);
        //externalStatisticsNumService.synExternalContacts(paramMap);
        externalContactService.synExternalContacts(paramMap);
        logger.info("---------------------异步同步外部联系人数据结束--------------------------");
    }

    /**
     * 同步外部联系人群聊
     *
     * @param corpId
     */
    public void synExternalGroup(String corpId, String userId) {
        logger.info("---------------------开始异步同步群聊数据--------------------------");
        externalGroupService.synCorpExternalGroup(corpId, userId);
        logger.info("---------------------异步同步群聊数据结束--------------------------");
    }

    /*
    *初始化权限
    * */
    public void synSysRole(){

    }

    /**
     * 获取应用可见范围的人员
     */
    public void synCorpAppAgentUser(String corpId) {
        //获取应用可见范围
        R<String> corpAccessTokenR = weChatDataService.queryCorpAccessToken(corpId);
        if (corpAccessTokenR.getCode() != R.SUCCESS) {
            logger.error(corpAccessTokenR.getMsg());
            return;
        }

        String accessToken = corpAccessTokenR.getData();
        Object companyInfoObject = redisService.getCacheObject(constant.getSuiteID() + "_" + corpId);
        WeChatThirdCompany thirdCompany = JSON.parseObject(companyInfoObject.toString(), WeChatThirdCompany.class);
        String agentUrl = String.format(WeChatUrlUtil.getAgent, accessToken, thirdCompany.getAgentId());
        String agentUrlResult = HttpUtil.get(agentUrl);
        Map agentResultMap = JSON.parseObject(agentUrlResult, Map.class);
        if (agentResultMap.containsKey("errcode") && MapUtils.getInteger(agentResultMap, "errcode") != 0) {
            logger.error("============================查询应用可见范围人员失败===================================");
        } else {
            Map allowUserinfos = MapUtils.getMap(agentResultMap, "allow_userinfos");
            if (allowUserinfos != null) {
                List<Map> userList = (List<Map>) allowUserinfos.get("user");
                if (allowUserinfos != null && allowUserinfos.size() > 0) {
                    for (Map userMap : userList) {
                        List<WeChatCorpUser> paramUserList = new ArrayList<>();
                        WeChatCorpUser qywxCorpUser = new WeChatCorpUser();
                        //查询人员详情
                        String userid = MapUtils.getString(userMap, "userid");
                        R<Map> userInfoResult = weChatDataService.queryPersonnelDetails(userid, accessToken);
                        if (userInfoResult.getCode() != 200) {
                            logger.error("===================获取人员详情失败=================");
                            logger.error(JSON.toJSONString(userInfoResult));
                            continue;
                        } else {
                            Map userInfoMap = userInfoResult.getData();
                            qywxCorpUser.setUserId(userid);
                            qywxCorpUser.setSuiteId(constant.getSuiteID());
                            qywxCorpUser.setCorpId(corpId);
                            int status = MapUtils.getInteger(userInfoMap, "status");
                            qywxCorpUser.setStatus(status);
                            String open_userid = MapUtils.getString(userInfoMap, "open_userid");
                            qywxCorpUser.setOpenUserid(open_userid);
                            String thumbAvatar = MapUtils.getString(userInfoMap, "thumb_avatar");
                            qywxCorpUser.setThumbAvatar(thumbAvatar);
                            String mainDepartment = MapUtils.getString(userInfoMap, "main_department");
                            qywxCorpUser.setDepartmentId(mainDepartment);
                            List<Integer> orderList = (List<Integer>) userInfoMap.get("order");
                            if (orderList != null && orderList.size() > 0) {
                                Integer order = orderList.get(0);
                                qywxCorpUser.setOrder(order);
                            }
                            //获取人员所属部门列表
                            List<Integer> departmentList = (List<Integer>) userInfoMap.get("department");
                            if (departmentList != null && departmentList.size() > 0) {
                                if (StringUtils.isEmpty(mainDepartment)) {
                                    //默认第一个为主部门
                                    qywxCorpUser.setDepartmentId(departmentList.get(0).toString());
                                }
                                departmentList.add(1);
                            } else {
                                departmentList = new ArrayList<>();
                                departmentList.add(1);
                            }
                            if (StringUtils.isEmpty(qywxCorpUser.getDepartmentId())) {
                                qywxCorpUser.setDepartmentId("1");
                            }
                            qywxCorpUser.setDepartmentIdList(departmentList);
                            paramUserList.add(qywxCorpUser);
                            weChatCorpUserService.saveCorpUser(paramUserList);
                        }
                    }

                }
            }

        }

    }

    /**
     * 判断人员是否在可见范围内
     * @param corpId
     * @return
     */
    public List<String> getAppAgentUser(String corpId) {
        //获取应用可见范围
        R<String> corpAccessTokenR = weChatDataService.queryCorpAccessToken(corpId);
        if (corpAccessTokenR.getCode() != R.SUCCESS) {
            logger.error(corpAccessTokenR.getMsg());
            return null;
        }
        String accessToken = corpAccessTokenR.getData();
        Object companyInfoObject = redisService.getCacheObject(constant.getSuiteID() + "_" + corpId);
        WeChatThirdCompany thirdCompany = JSON.parseObject(companyInfoObject.toString(), WeChatThirdCompany.class);
        String agentUrl = String.format(WeChatUrlUtil.getAgent, accessToken, thirdCompany.getAgentId());
        String agentUrlResult = HttpUtil.get(agentUrl);
        Map agentResultMap = JSON.parseObject(agentUrlResult, Map.class);
        if (agentResultMap.containsKey("errcode") && MapUtils.getInteger(agentResultMap, "errcode") != 0) {
            logger.error("============================查询应用可见范围人员失败===================================");
        } else {
            Map allowUserinfos = MapUtils.getMap(agentResultMap, "allow_userinfos");
            if (allowUserinfos != null) {
                List<Map> userList = (List<Map>) allowUserinfos.get("user");
                if (userList != null && userList.size() > 0) {
                    List<String> userIds = userList.stream()
                            .map(user -> (String) user.get("userid")) // 提取每个用户的 userid 字段
                            .collect(Collectors.toList()); // 收集成 List<String>
                    return userIds;
                } else {
                    return new ArrayList<>();
                }
            } else {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    private boolean deleteCompany(String corpId) {
        WeChatThirdCompany weChatThirdCompany = new WeChatThirdCompany();
        weChatThirdCompany.setStatus(0);
        weChatThirdCompany.setCorpId(corpId);
        weChatThirdCompany.setSuiteId(constant.getSuiteID());
        weChatThirdCompanyService.updateCompanyStatus(weChatThirdCompany);
        return true;
    }

    private boolean deleteAuthDepartment(String corpId) {
        WeChatCorpDepartment weChatCorpDepartment = new WeChatCorpDepartment();
        weChatCorpDepartment.setCorpId(corpId);
        weChatCorpDepartment.setSuiteId(constant.getSuiteID());
        //删除企业部门数据
        weChatCorpDepartmentService.deleteAllCorpDepartment(weChatCorpDepartment);
        return true;
    }

    private boolean deleteAuthUser(String corpId) {
        weChatThirdUserService.deleteQywxThirdAuthUser(corpId, constant.getSuiteID());
        return true;
    }

    private boolean deleteUserDepartment(String corpId) {
        WeChatUserDepartment weChatUserDepartment = new WeChatUserDepartment();
        weChatUserDepartment.setSuiteId(constant.getSuiteID());
        weChatUserDepartment.setCorpId(corpId);
        weChatUserDepartmentService.deleteUserDepartment(weChatUserDepartment);
        return true;
    }

    private boolean deleteCorpUser(String corpId) {
        WeChatCorpUser weChatCorpUser = new WeChatCorpUser();
        weChatCorpUser.setSuiteId(constant.getSuiteID());
        weChatCorpUser.setCorpId(corpId);
        weChatCorpUser.setStatus(0);
        weChatCorpUserService.updateCorpUserStatus(weChatCorpUser);
        return true;
    }

}
