package com.cube.wechat.selfapp.app.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * create by Jason.Dai on 2018/7/11.
 */
public class HttpClientUtil {

    public static String getHttp(String url, String accessToken, Map<String, Object> map) {
        StringHttpMessageConverter m = new StringHttpMessageConverter(Charset.forName("UTF-8"));
        RestTemplate restTemplate = new RestTemplateBuilder().additionalMessageConverters(m).build();

        HttpHeaders headers = new HttpHeaders();
        MediaType type = MediaType.parseMediaType("application/json; charset=UTF-8");
        headers.setContentType(type);
        headers.set("Accept-Encoding","compress,gzip");
        headers.add("Accept", MediaType.APPLICATION_JSON.toString());


        StringBuffer sb = new StringBuffer();
        sb.append(url);
        // 存在accessToken则拼接
        if (!StringUtils.isEmpty(accessToken)) {
            sb.append("?access_token=").append(accessToken);
        }
        // 将参数拼接上去
        if (map != null && map.size() > 0) {
            if (StringUtils.isEmpty(accessToken)) {
                sb.append("?");
            } else {
                sb.append("&");
            }
            int count = 1;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                sb.append(entry.getKey()).append("={").append(entry.getKey()).append("}");
                if (count < map.size()) {
                    sb.append("&");
                }
                count++;
            }
            url = String.valueOf(sb);
        }
        try {
            String res = restTemplate.getForObject(url, String.class, map);
            return res;
        } catch (Exception e) {
            throw e;
        }
    }

    public static String postHttp(String url, String accessToken, Map<String, Object> map) {
        StringHttpMessageConverter m = new StringHttpMessageConverter(Charset.forName("UTF-8"));
        RestTemplate restTemplate = new RestTemplateBuilder().additionalMessageConverters(m).build();
        HttpHeaders headers = new HttpHeaders();
        MediaType type = MediaType.parseMediaType("application/json; charset=UTF-8");
        headers.setContentType(type);
        headers.add("Accept", MediaType.APPLICATION_JSON.toString());
        HttpEntity<String> formEntity = new HttpEntity<>(JSON.toJSONString(map), headers);
        // 存在accessToken则拼接
        if (!StringUtils.isEmpty(accessToken)) {
            url = url + "?access_token=" + accessToken;
        }
        try {
//            String res = restTemplate.postForObject(url, formEntity, String.class);
            ResponseEntity<String> res = restTemplate.postForEntity(url, formEntity, String.class);
//            res.getBody()
            System.out.println(1111);
            return res.getBody();
        } catch (Exception e) {
            throw e;
        }
    }


  /**
   * 发送GET请求
   * @param url 目的地址
   * @param parameters 请求参数，Map类型。
   * @return 远程响应结果
   */
  public static String sendGet(String url, Map<String, String> parameters,String accessToken,String openId) {
    String result="";
    BufferedReader in = null;// 读取响应输入流
    StringBuffer sb = new StringBuffer();// 存储参数
    String params = "";// 编码之后的参数
    try {
      // 编码请求参数
      if(parameters.size()==1){
        for(String name:parameters.keySet()){
          sb.append(name).append("=").append(
            java.net.URLEncoder.encode(parameters.get(name),
              "UTF-8"));
        }
        params=sb.toString();
      }else{
        for (String name : parameters.keySet()) {
          sb.append(name).append("=").append(
            java.net.URLEncoder.encode(parameters.get(name),
              "UTF-8")).append("&");
        }
        String temp_params = sb.toString();
        if (!StringUtils.isEmpty(temp_params)){
          params = temp_params.substring(0, temp_params.length() - 1);
        }
      }
      String full_url = url + "?" + params;
      System.out.println(full_url);
      // 创建URL对象
      java.net.URL connURL = new java.net.URL(full_url);
      // 打开URL连接
      java.net.HttpURLConnection httpConn = (java.net.HttpURLConnection) connURL
        .openConnection();
      // 设置通用属性
      httpConn.setRequestProperty("Access-Token",accessToken);
      httpConn.setRequestProperty("Client-Id", "4cca97a84f4e468ea63824a105426a80");
      httpConn.setRequestProperty("Open-Id",openId);
      // 建立实际的连接
      httpConn.connect();

      // 响应头部获取
      Map<String, List<String>> headers = httpConn.getHeaderFields();
      // 遍历所有的响应头字段
      for (String key : headers.keySet()) {
        System.out.println(key + "\t：\t" + headers.get(key));
      }
      // 定义BufferedReader输入流来读取URL的响应,并设置编码方式
      in = new BufferedReader(new InputStreamReader(httpConn.getInputStream(), "UTF-8"));
      String line;
      // 读取返回的内容
      while ((line = in.readLine()) != null) {
        result += line;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }finally{
      try {
        if (in != null) {
          in.close();
        }
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
    return result ;
  }


    /**
     * 发送GET请求
     * @param url 目的地址
     * @return 远程响应结果
     */
    public static String sendGet2(String url) {
        String result="";
        BufferedReader in = null;// 读取响应输入流
        try {
            String full_url = "";
            int index = url.indexOf("=");
            if (index>-1){
                String[] begin = url.split("=");
                full_url = begin[0]+"="+java.net.URLEncoder.encode(begin[1],
                        "UTF-8");
            }else{
                full_url = url;
            }
            System.out.println(full_url);
            // 创建URL对象
            java.net.URL connURL = new java.net.URL(full_url);
            // 打开URL连接
            java.net.HttpURLConnection httpConn = (java.net.HttpURLConnection) connURL
                    .openConnection();
            // 设置通用属性
            httpConn.setRequestProperty("Accept", "*/*");
            httpConn.setRequestProperty("Connection", "Keep-Alive");
            httpConn.setRequestProperty("Connection", "close");
            httpConn.setRequestProperty("User-Agent",
                    "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 6.1)");
            httpConn.setRequestProperty("apikey", "bcb9b35b80aa5d4a98428dde090f2f2e");
            // 建立实际的连接
            httpConn.connect();

            // 响应头部获取
            Map<String, List<String>> headers = httpConn.getHeaderFields();
            // 遍历所有的响应头字段
            for (String key : headers.keySet()) {
                System.out.println(key + "\t：\t" + headers.get(key));
            }
            // 定义BufferedReader输入流来读取URL的响应,并设置编码方式
            in = new BufferedReader(new InputStreamReader(httpConn.getInputStream(), "UTF-8"));
            String line;
            // 读取返回的内容
            while ((line = in.readLine()) != null) {
                result += line;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return result ;
    }

  /**
   * 发送POST请求
   *
   * @param url 目的地址
   * @param parameters 请求参数，Map类型。
   * @return 远程响应结果
   */
  public static String sendPost(String url, Map<String, String> parameters) {
    String result = "";// 返回的结果
    BufferedReader in = null;// 读取响应输入流
    PrintWriter out = null;
    StringBuffer sb = new StringBuffer();// 处理请求参数
    String params = "";// 编码之后的参数
    try {
      // 编码请求参数
      if (parameters.size() == 1) {
        for (String name : parameters.keySet()) {
          sb.append(name).append("=").append(
            java.net.URLEncoder.encode(parameters.get(name),
              "UTF-8"));
        }
        params = sb.toString();
      } else {
        for (String name : parameters.keySet()) {
          sb.append(name).append("=").append(
            java.net.URLEncoder.encode(parameters.get(name),
              "UTF-8")).append("&");
        }
        String temp_params = sb.toString();
        params = temp_params.substring(0, temp_params.length() - 1);
      }
      // 创建URL对象
      java.net.URL connURL = new java.net.URL(url);
      // 打开URL连接
      java.net.HttpURLConnection httpConn = (java.net.HttpURLConnection) connURL
        .openConnection();
      // 设置通用属性
      httpConn.setRequestProperty("Accept", "*/*");
      httpConn.setRequestProperty("Connection", "Keep-Alive");
      httpConn.setRequestProperty("Content-Type", "application/json");
      httpConn.setRequestProperty("User-Agent",
        "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 6.1)");
      // 设置POST方式
      httpConn.setDoInput(true);
      httpConn.setDoOutput(true);
      // 获取HttpURLConnection对象对应的输出流
      out = new PrintWriter(httpConn.getOutputStream());
      // 发送请求参数
      out.write(params);
      // flush输出流的缓冲
      out.flush();
      // 定义BufferedReader输入流来读取URL的响应，设置编码方式
      in = new BufferedReader(new InputStreamReader(httpConn
        .getInputStream(), "UTF-8"));
      String line;
      // 读取返回的内容
      while ((line = in.readLine()) != null) {
        result += line;
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (out != null) {
          out.close();
        }
        if (in != null) {
          in.close();
        }
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
    return result;
  }

    /**
     * post发送json数据
     * @param url
     * @param param
     * @return
     */
    public static String doPost(String url, JSONObject param) {
        System.out.println("请求参数:"+param);
        HttpPost httpPost = null;
        String result = null;
        try {
            HttpClient client = new DefaultHttpClient();
            httpPost = new HttpPost(url);
            if (param != null) {
                StringEntity se = new StringEntity(param.toString(), "utf-8");
                httpPost.setEntity(se); // post方法中，加入json数据
                httpPost.setHeader("Content-Type", "application/json");
            }

            HttpResponse response = client.execute(httpPost);
            if (response != null) {
                result = EntityUtils.toString(response.getEntity());
                //把json字符串转换成json对象
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }

    public static String sendBgyPost(String url, JSONObject param) {
        HttpPost httpPost = null;
        String result = null;
        try {
            HttpClient client = new DefaultHttpClient();
            httpPost = new HttpPost(url);
            if (param != null) {
                StringEntity se = new StringEntity(param.toString(), "utf-8");
                httpPost.setEntity(se); // post方法中，加入json数据
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setHeader("usercode", "wsl");
                httpPost.setHeader("password", "wsl");
                httpPost.setHeader("sysid", "wsl");
                httpPost.setHeader("syncode", "query_to_mdm_rlzz");
            }

            HttpResponse response = client.execute(httpPost);
            if (response != null) {
                result = EntityUtils.toString(response.getEntity());
                //把json字符串转换成json对象
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }



    /**
     * post发送json数据
     * @param url
     * @param str
     * @return
     */
    public static String doPostByList(String url, String str) {
        HttpPost httpPost = null;
        String result = null;
        try {
            HttpClient client = new DefaultHttpClient();
            httpPost = new HttpPost(url);
            if (str != null) {
                StringEntity se = new StringEntity(str, "utf-8");
                httpPost.setEntity(se); // post方法中，加入json数据
                httpPost.setHeader("Content-Type", "application/json");
            }

            HttpResponse response = client.execute(httpPost);
            if (response != null) {
                result = EntityUtils.toString(response.getEntity());
                //把json字符串转换成json对象
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }
}
