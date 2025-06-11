package com.tencent.wework;

/**
 * @warning: 不要修改成员变量名，native方法内有反射调用
 */
public class SpecSDK {
    private long specSDKptr = 0;

    /**
     * @usage invoke的请求
     * @example "{\"limit\":1}
     */
    private String request;

    public void SetRequest(String request) { this.request = request; }

    /**
     * @usage 访问上一次invoke的结果
     */
    private String response;

    public String GetResponse() { return response; }

    /**
     * @param corpid:     企业corpid，必选参数
     * @param agentid:    应用id，必选参数
     * @param ability_id: 能力ID，可选参数
     * @param job_info:   job_info，可选参数
     * */
    public SpecSDK(String corpId, long agentId) {
        specSDKptr = NewSDK1(corpId, agentId);
    }

    private native long NewSDK1(String corpId, long agentId);

    public SpecSDK(String corpId, long agentId, String abilityId) {
        specSDKptr = NewSDK2(corpId, agentId, abilityId);
    }

    private native long NewSDK2(String corpId, long agentId, String abilityId);

    public SpecSDK(String corpId, long agentId, String abilityId, String jobInfo) {
        specSDKptr = NewSDK3(corpId, agentId, abilityId, jobInfo);
    }

    private native long NewSDK3(String corpId, long agentId, String abilityId, String jobInfo);

    /**
     * @description         使用callback的请求来初始化
     * @param callback_sdk: 要求IsOk()==true
     * @return C++内部指针，创建失败时指针仍为0，并输出错误日志
     * */
    public SpecSDK(SpecCallbackSDK callbackSDK) {
        specSDKptr = NewSDK4(callbackSDK.GetPtr());
    }

    private native long NewSDK4(long callbackSDK);

    /**
     * @description     用于在专区内调用企业微信接口
     * @param api_name 接口名
     * @param request  json格式的请求数据
     * @param response json格式的返回数据
     * @return errorcode 参考如下:
     *            0: 成功
     *            -10001: SDK没有初始化
     *            -10002: 没有设置请求体
     *            -10003: 没有设置请求的API
     *            -10004: 在SDK成员内找不到成员"response",注意lib内有反射机制,不要修改成员变量名
     *            -10006: invoke调用失败,应检查日志查看具体原因
     *            -10007: 响应体为空
     * @note 当返回0时,表示没有网络或请求协议层面或调用方法的失败,
     *       调用方需继续检查response中的errcode字段确保业务层面的成功
     *
     * @usage 当前版本sdk支持的接口列表,每个接口的具体协议请查看企业微信文档:
     *        https://developer.work.weixin.qq.com/document/path/91201
     *
     * +--------------------------------+--------------------------------+
     * |接口名                          |描述                            |
     * |--------------------------------|--------------------------------|
     * |program_async_job_call_back     |上报异步任务结果                |
     * |sync_msg                        |获取会话记录                    |
     * |get_group_chat                  |获取内部群信息                  |
     * |get_agree_status_single         |获取单聊会话同意情况            |
     * |get_agree_status_room           |获取群聊会话同意情况            |
     * |set_hide_sensitiveinfo_config   |设置成员会话组件敏感信息隐藏配置|
     * |get_hide_sensitiveinfo_config   |获取成员会话组件敏感信息隐藏配置|
     * |search_chat                     |会话名称搜索                    |
     * |search_msg                      |会话消息搜索                    |
     * |create_rule                     |新增关键词规则                  |
     * |get_rule_list                   |获取关键词列表                  |
     * |get_rule_detail                 |获取关键词规则详情              |
     * |update_rule                     |修改关键词规则                  |
     * |delete_rule                     |删除关键词规则                  |
     * |get_hit_msg_list                |获取命中关键词规则的会话记录    |
     * |create_sentiment_task           |创建情感分析任务                |
     * |get_sentiment_result            |获取情感分析结果                |
     * |create_summary_task             |创建摘要提取任务                |
     * |get_summary_result              |获取摘要提取结果                |
     * |create_customer_tag_task        |创建标签匹配任务                |
     * |get_customer_tag_result         |获取标签任务结果                |
     * |create_recommend_dialog_task    |创建话术推荐任务                |
     * |get_recommend_dialog_result     |获取话术推荐结果                |
     * |create_private_task             |创建自定义模型任务              |
     * |get_private_task_result         |获取自定义模型结果              |
     * |document_list                   |获取知识集列表                  |
     * |create_spam_task                |会话反垃圾创建分析任务          |
     * |get_spam_result                 |会话反垃圾获取任务结果          |
     * |create_chatdata_export_job      |创建会话内容导出任务            |
     * |get_chatdata_export_job_status  |获取会话内容导出任务结果        |
     * |spec_notify_app                 |专区通知应用                    |
     * |create_program_task             |创建自定义程序任务              |
     * |get_program_task_result         |获取自定义程序结果              |
     * +-----------------------------------------------------------------+
     * */
    public int Invoke(String apiName) {
        return Invoke(specSDKptr, apiName, request);
    }

    private native int Invoke(long sdk, String apiName, String request);

    static {
        System.loadLibrary("WeWorkSpecSDK");
    }
}
