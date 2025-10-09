package com.tencent.wework;

import java.util.HashMap;
import java.util.Map;

;

/**
 * @warning: 不要修改成员变量名，native方法内有反射调用
 */
public class SpecCallbackSDK {
    private long specCallbackSDKptr = 0;

    public long GetPtr() { return specCallbackSDKptr; }

    /**
     * @description: 返回包的header
     */
    private Map<String, String> responseHeaders;

    public Map<String, String> GetResponseHeaders() { return responseHeaders; }

    /**
     * @description:  返回包的body，已加密
     */
    private String responseBody;

    public String GetResponseBody() { return responseBody; }

    /**
     * @description:   每个请求构造一个SpecCallbackSDK示例,
     *                 SpecCallbackSDK仅持有headers和body的引用,
     *                 因此需保证headers和body的生存期比SpecCallbackSDK长
     * @param method:  请求方法GET/POST
     * @param headers: 请求header
     * @param body:    请求body
     * @example:
     * SpecCallbackSDK sdk = new SpecCallbackSDK(method, headers, body);
     * if (sdk.IsOk()) {
     *   String corpid = sdk.GetCorpId();
     *   String agentid = sdk.GetAgentId();
     *   String call_type = sdk.GetCallType();
     *   String data = sdk.GetData();
     *   //do something...
     * }
     * String response = ...;
     * sdk.BuildResponseHeaderBody(response);
     * Map<String, String> responseHeaders = sdk.GetResponseHeaders();
     * String body = sdk.GetResponseBody();
     * //do response
     *
     * @return errorcode 示例如下:
     *         -20001: 未设置请求方法
     *         -20002: 未设置请求header
     *         -20003: 未设置请求body
     * */
    public SpecCallbackSDK(String method, Map<String, String> headers, String body) {
        try {
            specCallbackSDKptr = NewCallbackSDK(method, headers, body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private native long NewCallbackSDK(String method, Map<String, String> headers, String body);

    /**
     * @description: 判断构造函数中传入的请求是否解析成功
     * @return:      成功与否
     * */
    public boolean IsOk() {
        return IsOk(specCallbackSDKptr);
    }

    private native boolean IsOk(long specCallbackSDKptr);

    /**
     * @description: 获取请求的企业
     * @require:     仅当IsOk() == true可调用
     * @return:      corpid
     * */
    public String GetCorpId() {
        return GetCorpId(specCallbackSDKptr);
    }

    private native String GetCorpId(long specCallbackSDKptr);

    /**
     * @description: 获取请求的应用
     * @require:     仅当IsOk() == true可调用
     * @return:      agentid
     * */
    public long GetAgentId() {
        return GetAgentId(specCallbackSDKptr);
    }

    private native long GetAgentId(long specCallbackSDKptr);

    /**
     * @description: 获取请求的类型
     * @require:     仅当IsOk() == true可调用
     * @return:      1 - 来自[应用调用专区]的请求
     *               2 - 来自企业微信的回调事件
     * */
    public long GetCallType() {
        return GetCallType(specCallbackSDKptr);
    }

    private native long GetCallType(long specCallbackSDKptr);

    /**
     * @description: 获取请求数据
     * @require:     仅当IsOk() == true可调用
     * @return:      请求数据,根据call_type可能是:
     *               - 企业微信回调事件
     *               - [应用调用专区]接口中的request_data
     * */
    public String GetData() {
        return GetData(specCallbackSDKptr);
    }

    private native String GetData(long specCallbackSDKptr);

    /**
     * @description: 是否异步请求
     * @require:     仅当IsOk() == true可调用
     * @return:      是否异步请求
     * */
    public boolean GetIsAsync() {
        return GetIsAsync(specCallbackSDKptr);
    }

    private native boolean GetIsAsync(long specCallbackSDKptr);

    /**
     * @description: 获取请求的job_info,
     * @require:     仅当IsOk() == true可调用
     * @return:      job_info,无需理解内容,
     *               在同一个请求上下文中使用SpecSDK的时候传入
     * */
    public String GetJobInfo() {
        return GetJobInfo(specCallbackSDKptr);
    }

    private native String GetJobInfo(long specCallbackSDKptr);

    /**
     * @description: 获取请求的ability_id,[应用调用专区]接口时指定
     * @require:     仅当IsOk() == true可调用
     * @return:      ability_id
     * */
    public String GetAbilityId() {
        return GetAbilityId(specCallbackSDKptr);
    }

    private native String GetAbilityId(long specCallbackSDKptr);

    /**
     * @description: 获取请求的notify_id,用于[应用同步调用专区程序]接口
     * @require:     仅当IsOk() == true可调用
     * @return:      notify_id
     * */
    public String GetNotifyId() {
        return GetNotifyId(specCallbackSDKptr);
    }

    private native String GetNotifyId(long specCallbackSDKptr);

    /**
     * @description:    对返回包计算签名&加密
     * @param response: 待加密的回包明文.如果IsOk()==false,传入空串即可
     * @note 本接口的执行问题可查看日志
     * */
    public void BuildResponseHeaderBody(String response) {
        try {
            responseHeaders = new HashMap<String, String>();
            responseBody = "";
            BuildResponseHeaderBody(specCallbackSDKptr, response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private native void BuildResponseHeaderBody(long specCallbackSDKptr, String response);

    static {
        System.loadLibrary("WeWorkSpecSDK");
    }
}
