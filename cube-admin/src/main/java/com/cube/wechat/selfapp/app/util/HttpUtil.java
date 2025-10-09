package com.cube.wechat.selfapp.app.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP工具类
 * 基于Apache HttpClient 5.x实现GET和POST请求
 * 参考示例中的编码风格和请求处理方式
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/29 16:16
 */
public class HttpUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发送GET请求
     * @param url 请求基础地址
     * @param params 请求参数Map
     * @return 响应内容字符串
     * @throws URISyntaxException URI构建异常
     * @throws IOException IO异常
     * @throws ParseException 解析异常
     */
    public static String doGet(String url, Map<String, Object> params) throws URISyntaxException, IOException, ParseException {
        // 创建HttpClient
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 构建带参数的URI
            URIBuilder uriBuilder = new URIBuilder(url);
            
            // 添加请求参数
            if (params != null && !params.isEmpty()) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    uriBuilder.addParameter(entry.getKey(), entry.getValue().toString());
                }
            }
            
            URI uri = uriBuilder.build();
            HttpGet httpGet = new HttpGet(uri);
            
            // 执行请求并获取响应
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                // 解析响应内容
                return org.apache.hc.core5.http.io.entity.EntityUtils.toString(
                        response.getEntity(),
                        StandardCharsets.UTF_8.name()
                );
            }
        }
    }

    /**
     * 发送POST请求（JSON格式参数）
     * @param url 请求地址
     * @param params 请求参数Map
     * @return 响应内容字符串
     * @throws IOException IO异常
     * @throws ParseException 解析异常
     */
    public static String doPostJson(String url, Map<String, Object> params) throws IOException, ParseException {
        // 创建HttpClient
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 创建POST请求
            HttpPost httpPost = new HttpPost(url);
            
            // 转换参数为JSON并设置请求体
            if (params != null && !params.isEmpty()) {
                String jsonParams = objectMapper.writeValueAsString(params);
                StringEntity entity = new StringEntity(jsonParams, ContentType.APPLICATION_JSON);
                httpPost.setEntity(entity);
            }
            
            // 执行请求并获取响应
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                // 解析响应内容
                return org.apache.hc.core5.http.io.entity.EntityUtils.toString(
                        response.getEntity(),
                        StandardCharsets.UTF_8.name()
                );
            }
        }
    }

    /**
     * 发送POST请求（表单格式参数）
     * @param url 请求地址
     * @param params 请求参数Map
     * @return 响应内容字符串
     * @throws IOException IO异常
     * @throws ParseException 解析异常
     */
    public static String doPostForm(String url, Map<String, String> params) throws IOException, ParseException {
        // 创建HttpClient
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 创建POST请求
            HttpPost httpPost = new HttpPost(url);
            
            // 构建表单参数
            StringBuilder formData = new StringBuilder();
            if (params != null && !params.isEmpty()) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if (formData.length() > 0) {
                        formData.append("&");
                    }
                    formData.append(entry.getKey()).append("=").append(entry.getValue());
                }
                
                StringEntity entity = new StringEntity(formData.toString(), 
                        ContentType.APPLICATION_FORM_URLENCODED);
                httpPost.setEntity(entity);
            }
            
            // 执行请求并获取响应
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                // 解析响应内容
                return org.apache.hc.core5.http.io.entity.EntityUtils.toString(
                        response.getEntity(),
                        StandardCharsets.UTF_8.name()
                );
            }
        }
    }

    /**
     * 获取图片字节流
     * @param imageUrl 图片地址
     */
    public static InputStream getImageStreamByHttpClient(String imageUrl) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() == 200) {
                return response.body();
            }
        } catch (IOException | InterruptedException e) {
            return null;
        }
        return null;
    }
}
