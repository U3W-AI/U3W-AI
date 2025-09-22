package com.playwright.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.ContentBody;
import org.apache.hc.client5.http.entity.mime.InputStreamBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
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
import java.util.concurrent.TimeUnit;

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
     * 发送POST请求（支持文件上传和文本参数）
     * @param url 请求地址
     * @param textParams 文本参数
     * @param fileParamName 文件参数名
     * @param fileInputStream 文件输入流
     * @param fileContentType 文件内容类型
     * @param fileName 文件名
     * @return 响应内容字符串
     * @throws IOException IO异常
     * @throws ParseException 解析异常
     */
    public static String doPostWithFile(String url,
                                        Map<String, Object> textParams,
                                        String fileParamName,
                                        InputStream fileInputStream,
                                        String fileContentType,
                                        String fileName) throws IOException, ParseException {
        // 创建HttpClient
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 创建POST请求
            HttpPost httpPost = new HttpPost(url);

            // 构建multipart/form-data请求体
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            // 添加文本参数
            if (textParams != null && !textParams.isEmpty()) {
                for (Map.Entry<String, Object> entry : textParams.entrySet()) {
                    builder.addTextBody(entry.getKey(), entry.getValue().toString(), ContentType.TEXT_PLAIN);
                }
            }

            // 添加文件流
            if (fileInputStream != null && fileParamName != null && !fileParamName.isEmpty()) {
                ContentType contentType = fileContentType != null ?
                        ContentType.create(fileContentType) : ContentType.APPLICATION_OCTET_STREAM;
                ContentBody fileBody = new InputStreamBody(fileInputStream, contentType, fileName);
                builder.addPart(fileParamName, fileBody);
            }

            // 设置请求实体
            httpPost.setEntity(builder.build());

            // 执行请求并获取响应
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                // 解析响应内容
                return EntityUtils.toString(
                        response.getEntity(),
                        StandardCharsets.UTF_8.name()
                );
            }
        } finally {
            // 关闭文件输入流
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    // 记录关闭流异常，但不影响主流程结果
                    e.printStackTrace();
                }
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
