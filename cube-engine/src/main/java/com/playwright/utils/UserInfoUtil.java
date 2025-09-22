package com.playwright.utils;

import com.playwright.constants.WxExceptionConstants;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author 孔德权
 * description:  TODO
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/23 11:12
 */
@Component
public class UserInfoUtil {
    @Value("${cube.url}")
    private String url;

    public String getUserIdByUnionId(String unionId) throws URISyntaxException, IOException, ParseException {
        if (unionId == null || unionId.trim().isEmpty()) {
            return null;
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 构建带参数的URL（自动处理URL编码）
            URIBuilder uriBuilder = new URIBuilder(url + "/getUserId");
            // 添加请求参数（可根据实际接口需求添加更多参数）
            uriBuilder.addParameter("unionId", unionId);

            URI uri = uriBuilder.build();
            HttpGet httpGet = new HttpGet(uri);

            // 发送请求并获取响应
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getCode();
                String userId = EntityUtils.toString(response.getEntity(), "UTF-8");

                // 处理响应结果
                if (statusCode == 200) {
                    return userId;
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
            throw e;
        }
    }

    public String getUnionIdByUserId(String userId) throws Exception{
        try {
            String getUrl = url + "/getUnionId";
            Map<String, Object> map = new HashMap<>();
            map.put("userId", userId);
            return HttpUtil.doGet(getUrl, map);
        } catch (Exception e) {
            throw new RuntimeException(WxExceptionConstants.WX_AUTH_EXCEPTION);
        }
    }
}
