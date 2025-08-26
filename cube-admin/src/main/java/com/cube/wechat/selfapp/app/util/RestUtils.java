package com.cube.wechat.selfapp.app.util;


import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Configuration
public class RestUtils {

    private static final RestTemplate restTemplate = new RestTemplate();

    public static JSONObject get(String url, Map<String,String> urlParams){
        return get(urlToUri(url,urlParams));
    }

    //在处理企业微信某些参数时有问题
    public static JSONObject get(String url){
        return get(URI.create(url));
    }

    private static JSONObject get(URI uri){
        ResponseEntity<JSONObject> responseEntity =restTemplate.getForEntity(uri,JSONObject.class);
        serverIsRight(responseEntity);   //判断服务器返回状态码
        return responseEntity.getBody();
    }

    public static JSONObject post(String url,Map<String,String> urlParams,JSONObject json){
        //组装url
        return post(urlToUri(url,urlParams),json);
    }

    public static JSONObject post(String url,JSONObject json){
        //组装urL
        return post(URI.create(url),json);
    }
    public static JSONObject aiPost(String url,JSONObject json,String bearerToken){
        //组装urL
        return aiPost(URI.create(url),json,bearerToken);
    }

    private static JSONObject aiPost(URI uri,JSONObject json,String bearerToken){
        //组装url
        //设置提交json格式数据
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken); // 添加Bearer Token
        HttpEntity<JSONObject> request = new HttpEntity(json, headers);
        ResponseEntity<JSONObject> responseEntity = restTemplate.postForEntity(uri,request,JSONObject.class);
        serverIsRight(responseEntity);  //判断服务器返回状态码
        return responseEntity.getBody();
    }
    private static JSONObject post(URI uri, JSONObject json) {
        // 设置提交json格式数据，并明确UTF-8编码
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        HttpEntity<JSONObject> request = new HttpEntity<>(json, headers);

        // 配置RestTemplate支持text/plain类型响应解析为JSON
        RestTemplate genericRestTemplate = getGenericRestTemplate();

        // 先以String形式接收响应，再手动转换为JSONObject
        ResponseEntity<JSONObject> stringResponse = genericRestTemplate.postForEntity(uri, request, JSONObject.class);
        // 将String响应转换为JSONObject
        return stringResponse.getBody();
//        return JSONObject.parseObject(stringResponse.getBody());
    }
    private static RestTemplate getGenericRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // 1. 配置超时（保留原有逻辑）
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(10000);
        restTemplate.setRequestFactory(requestFactory);

        // 2. 关键修改：先移除默认的 JSON 转换器（避免冲突），再添加支持 text/plain 的 FastJSON 转换器
        List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
        // 移除默认的 JSON 转换器（如 MappingJackson2HttpMessageConverter，避免和 FastJSON 冲突）
        converters.removeIf(converter -> converter instanceof AbstractHttpMessageConverter &&
                converter.getSupportedMediaTypes().stream().anyMatch(mt -> mt.includes(MediaType.APPLICATION_JSON)));

        // 3. 重新添加 FastJSON 转换器，并明确支持 text/plain 和 application/json
        FastJsonHttpMessageConverter fastJsonConverter = new FastJsonHttpMessageConverter();
        // 设置默认编码为 UTF-8（解决中文乱码）
        fastJsonConverter.setDefaultCharset(StandardCharsets.UTF_8);
        // 关键：添加支持的 Content-Type，包含 text/plain（微信返回的格式）和 application/json
        List<MediaType> fastJsonSupportedTypes = new ArrayList<>();
        fastJsonSupportedTypes.add(MediaType.TEXT_PLAIN);       // 必须加：匹配微信返回的 text/plain
        fastJsonSupportedTypes.add(MediaType.APPLICATION_JSON); // 兼容标准 JSON 格式
        fastJsonConverter.setSupportedMediaTypes(fastJsonSupportedTypes);
        converters.add(fastJsonConverter); // 将 FastJSON 转换器加入列表

        // 4. 确保 String 转换器也支持 UTF-8（避免其他场景乱码，可选但建议保留）
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof StringHttpMessageConverter) {
                StringHttpMessageConverter stringConverter = (StringHttpMessageConverter) converter;
                stringConverter.setDefaultCharset(StandardCharsets.UTF_8);
                stringConverter.setSupportedMediaTypes(List.of(MediaType.ALL));
            }
        }

        return restTemplate;
    }
    // 配置RestTemplate，确保能处理各种响应类型
    private static void configureRestTemplate(RestTemplate restTemplate) {
        // 设置超时时间，避免接口响应过慢
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);  // 连接超时5秒
        requestFactory.setReadTimeout(10000);   // 读取超时10秒
        restTemplate.setRequestFactory(requestFactory);
    }


    private static URI urlToUri(String url,Map<String,String> urlParams){
        //设置提交json格式数据
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url);
        for(Map.Entry<String,String> entry : urlParams.entrySet())  {
            uriBuilder.queryParam((String)entry.getKey(),  (String) entry.getValue()) ;
        }
        return  uriBuilder.build(true).toUri();
    }

    public static JSONObject upload(String url,MultiValueMap formParams){
        //设置表单提交
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formParams, headers);
        ResponseEntity<JSONObject> responseEntity = restTemplate.postForEntity(url,request,JSONObject.class);
        serverIsRight(responseEntity);  //判断服务器返回状态码
        return responseEntity.getBody();
    }

    public static String download(String url,String targetPath,HttpEntity<MultiValueMap<String, String>> httpEntity ) throws IOException {

        ResponseEntity<byte[]> rsp = restTemplate.exchange(url, HttpMethod.GET, httpEntity, byte[].class);
        if(rsp.getStatusCode() != HttpStatus.OK){
            System.out.println("文件下载请求结果状态码：" + rsp.getStatusCode());
        }
        // 将下载下来的文件内容保存到本地
        Files.write(Paths.get(targetPath), Objects.requireNonNull(rsp.getBody()));
        return targetPath;
    }

    public static String download(String url,String targetPath) throws IOException {

        ResponseEntity<byte[]> rsp = restTemplate.getForEntity(url, byte[].class);
        if(rsp.getStatusCode() != HttpStatus.OK){
            System.out.println("文件下载请求结果状态码：" + rsp.getStatusCode());
        }
        // 将下载下来的文件内容保存到本地
        Files.write(Paths.get(targetPath), Objects.requireNonNull(rsp.getBody()));
        return targetPath;

    }

    public static byte[] dowload(String url){
        ResponseEntity<byte[]> rsp = restTemplate.getForEntity(url, byte[].class);
        return rsp.getBody();
    }

    private static void serverIsRight(ResponseEntity responseEntity){
        if(responseEntity.getStatusCodeValue()==200){
//            System.out.println("服务器请求成功：{}"+responseEntity.getStatusCodeValue());
        }else {
            System.out.println("服务器请求异常：{}"+responseEntity.getStatusCodeValue());
        }
    }


}
