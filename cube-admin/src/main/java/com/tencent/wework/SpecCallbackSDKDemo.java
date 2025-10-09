package com.tencent.wework;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONValidator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @usage 本demo为专区程序服务器示例，支持如下功能：
 *        1. 透传企业应用的请求，在专区程序内调用sdk请求专区后台
 *        2. 接收专区后台的回调，暂存异步请求的结果
 *        3. 通知企业应用获取异步请求的结果
 */
@SuppressWarnings("restriction")
public class SpecCallbackSDKDemo {

    private static final List<String> SUPPORT_API_LIST = Arrays.asList(
        "program_async_job_call_back",
        "sync_msg",
        "get_group_chat",
        "get_agree_status_single",
        "get_agree_status_room",
        "set_hide_sensitiveinfo_config",
        "get_hide_sensitiveinfo_config",
        "search_chat",
        "search_msg",
        "create_rule",
        "get_rule_list",
        "get_rule_detail",
        "update_rule",
        "delete_rule",
        "get_hit_msg_list",
        "create_sentiment_task",
        "get_sentiment_result",
        "create_summary_task",
        "get_summary_result ",
        "create_customer_tag_task",
        "get_customer_tag_result",
        "create_recommend_dialog_task",
        "get_recommend_dialog_result",
        "create_private_task",
        "get_private_task_result",
        "document_list",
        "create_spam_task",
        "get_spam_result",
        "create_chatdata_export_job",
        "get_chatdata_export_job_status",
        "spec_notify_app",
        "create_program_task",
        "get_program_task_result"
    );

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
        server.createContext("/", new PostHandler());

        server.setExecutor(null);
        server.start();
        LogUtil.LogInfo(null, "server started on port 8080");
    }

    static class PostHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                if (!("POST".equals(exchange.getRequestMethod()))) {
                    LogUtil.LogError(null, "receive invalid request method, expect POST, but got " + exchange.getRequestMethod());
                    exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
                    return;
                }

                Map<String, List<String>> reqHeadersRaw = exchange.getRequestHeaders();
                Map<String, String> reqHeaders = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : reqHeadersRaw.entrySet()) {
                    reqHeaders.put(InverseNormalize(entry.getKey()), entry.getValue().get(0));
                }
                LogUtil.LogInfo(null, "request headers: " + JSONObject.toJSONString(reqHeaders));

                // 读取请求body
                InputStream reqInput = exchange.getRequestBody();
                byte[] buffer = new byte[1024];
                int read;
                StringBuilder reqBody = new StringBuilder();
                try {
                    while ((read = reqInput.read(buffer)) != -1) {
                        reqBody.append(new String(buffer, 0, read));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                LogUtil.LogInfo(null, "request body: " + reqBody);

                // 构造SpecCallbackSDK，用于解析请求构造sdk和处理响应数据
                SpecCallbackSDK callback = new SpecCallbackSDK("POST", reqHeaders, reqBody.toString());
                String responseContent = "";
                if (callback.IsOk()) {
                    responseContent = HandleCallback(callback);
                } else {
                    LogUtil.LogError(null, "callback is not ok");
                    return;
                }

                // 构造响应headers，加密响应内容，获取加密的body
                callback.BuildResponseHeaderBody(responseContent);
                Map<String, String> rspHeaders = callback.GetResponseHeaders();
                String rspBody = callback.GetResponseBody();
                LogUtil.LogInfo(null, "response headers: " + JSONObject.toJSONString(rspHeaders), "response body: " + rspBody);

                Headers rspHeadersRaw = exchange.getResponseHeaders();
                for (Map.Entry<String, String> entry : rspHeaders.entrySet()) {
                    rspHeadersRaw.set(InverseNormalize(entry.getKey()), entry.getValue());
                }

                // 发送响应
                OutputStream resp = exchange.getResponseBody();
                byte[] rspBodyBytes = rspBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, rspBodyBytes.length);
                resp.write(rspBodyBytes);
                resp.close();
                LogUtil.LogInfo(null, "send response success");
            } catch (Exception e) {
                LogUtil.LogError(null, "handle http post failed", e.getMessage());
                e.printStackTrace();
            }
        }

        /**
         * @usage 将http header转换为callback接受的格式
         * @background demo使用的网络框架sun.net.http server会自动将http请求头的key转换为首字母大写，
         *             如将ww-secret-ver转换为Ww-secret-ver，这会影响到部分场景的协议解析，需要转换为小写
         * @warning 需注意使用的网络框架有没有类似的normalize机制
         * @param httpHeaderKey http header的key
         * @return 反正则化（还原）后的企微协议的key
         */
        private String InverseNormalize(String httpHeaderKey) {
            if (httpHeaderKey.startsWith("Ww-")) {
                return httpHeaderKey.toLowerCase();
            }
            return httpHeaderKey;
        }

        /**
         * @param callback
         * @return response msg
         * @description 应用主动调用API
         */
        private String AppActiveCall(SpecCallbackSDK callback) {
            String abilityId = callback.GetAbilityId();
            SpecSDK sdk = new SpecSDK(callback);
            //透传sdk的请求，充当sdk的代理
            if ("invoke_".equals(abilityId.substring(0, 7))) {
                String apiName = abilityId.substring(7);
                if (!SUPPORT_API_LIST.contains(apiName)) {
                    LogUtil.LogDebug(null, "unsupported api", apiName);
                    return "unsupport api";
                }

                sdk.SetRequest(callback.GetData());
                int ret = sdk.Invoke(apiName);
                if (ret != 0) {
                    LogUtil.LogDebug(null, "invoke failed, ret = " + ret, "apiName = " + apiName, "data = " + callback.GetData());
                    return "invoke failed";
                }

                String invokeRsp = sdk.GetResponse();
                LogUtil.LogInfo(null, "invoke success", "invokeRsp = " + invokeRsp);
                return invokeRsp;

            // 通知企业应用获取异步请求的结果
            } else if ("get_callback_data".equals(abilityId)) {
                String notifyId = callback.GetNotifyId();
                if (notifyId.isEmpty()) {
                    LogUtil.LogError(null, "notify_id is empty");
                    return "notify_id is empty";
                }

                StringBuffer dataBuffer = new StringBuffer();
                DataBaseUtils.ErrorCode errorCode = DataBaseUtils.GetByNotifyId(notifyId, dataBuffer);
                if (errorCode != DataBaseUtils.ErrorCode.SUCCESS) {
                    LogUtil.LogError(null, "get data by notify_id failed", "errorCode = " + errorCode);
                    return "get data by notify_id failed";
                }

                return dataBuffer.toString();
            }

            return "unknown ability_id";
        }

        /**
         * @param callback Spec Callback SDK
         * @return 响应信息
         * @description 企业微信回调事件, 暂存起来, 通知应用主动来获取，产生"program_notify"事件
         * @detail https://developer.work.weixin.qq.com/document/path/99843
         */
        private String NotifyAndStore(SpecCallbackSDK callback) {
            // 调用sdk
            SpecSDK sdk = new SpecSDK(callback);
            sdk.SetRequest("{}");
            int ret = sdk.Invoke("spec_notify_app");
            if (ret != 0) {
                LogUtil.LogError(null, "invoke failed", "ret = " + ret);
                return "invoke failed";
            }
            LogUtil.LogInfo(null, "spec_notify_app invoke success");

            // 检验参数
            String notifyRspStr = sdk.GetResponse();
            JSONObject notifyRspJSON = JSON.parseObject(notifyRspStr);
            if (!JSONValidator.from(notifyRspStr).validate() || notifyRspJSON.isEmpty()) {
                LogUtil.LogError(null, "notifyRsp is invalid", "notifyRsp = " + notifyRspStr);
                return "notifyRsp is invalid";
            }
            if (!notifyRspJSON.containsKey("errcode")) {
                LogUtil.LogError(null, "missing errcode in notifyRsp");
                return "missing errcode in notifyRsp";
            }
            Integer errcode = notifyRspJSON.getInteger("errcode");
            if (errcode != 0) {
                LogUtil.LogError(null, "invoke fail", "errcode = " + errcode, "errmsg = " + notifyRspJSON.getString("errmsg"));
                return "logic error, errcode = " + errcode + ", errmsg = " + notifyRspJSON.getString("errmsg");
            }
            LogUtil.LogInfo(null, "notify success", "notifyRsp = " + notifyRspStr);

            // 获取notify_id
            String notifyId = notifyRspJSON.getString("notify_id");
            if (notifyId.isEmpty()) {
                LogUtil.LogError(null, "missing notify id");
                return "missing notify id";
            }
            LogUtil.LogInfo(null, "notify id = " + notifyId);

            // 存储notify_id和数据，默认20分钟过期
            DataBaseUtils.ErrorCode errorCode = DataBaseUtils.AddNotifyData(notifyId, callback.GetData());
            if (errorCode != DataBaseUtils.ErrorCode.SUCCESS) {
                LogUtil.LogError(null, "data store fail", "errorCode = " + errorCode);
                return "notify data store failed";
            }

            return "notify and store success";
        }

        /**
         * @param callback
         * @return response msg
         * @description 处理请求callback
         */
        private String HandleCallback(SpecCallbackSDK callback) {
            LogUtil.LogInfo(null, "callback args",
                    "\ncorpId = " + callback.GetCorpId(),
                    "\nagentId = " + callback.GetAgentId(),
                    "\ncallType = " + callback.GetCallType(),
                    "\nisAsync = " + callback.GetIsAsync(),
                    "\njobInfo = " + callback.GetJobInfo(),
                    "\nabilityId = " + callback.GetAbilityId(),
                    "\nnotifyId = " + callback.GetNotifyId(),
                    "\ndata = " + callback.GetData()
            );

            switch ((int)callback.GetCallType()) {
                case 1:
                    return AppActiveCall(callback);  // 应用主动调用专区程序
                case 2:
                    return NotifyAndStore(callback);  // 专区后台通知专区程序
                default:
                    LogUtil.LogError(null, "unknown call type", "callType = " + callback.GetCallType());
                    return "unknown call type";
            }
        }
    }
}
