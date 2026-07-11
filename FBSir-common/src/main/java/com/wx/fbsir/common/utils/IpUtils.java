package com.wx.fbsir.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * IP地址工具类
 * 
 * @author FBSir
 * @date 2025-12-08
 */
public class IpUtils {
    
    private static final Logger log = LoggerFactory.getLogger(IpUtils.class);
    
    // 多个公网IP查询服务，提高可用性
    private static final String[] IP_CHECK_URLS = {
        "https://api.ipify.org",           // 推荐，简洁可靠
        "https://ifconfig.me/ip",          // 备用
        "https://icanhazip.com",           // 备用
        "https://checkip.amazonaws.com"    // AWS服务
    };
    
    /**
     * 获取服务器的公网IP地址
     * 
     * @return 公网IP地址，获取失败返回null
     */
    public static String getPublicIp() {
        // 尝试所有可用的IP查询服务
        for (String url : IP_CHECK_URLS) {
            try {
                String ip = getIpFromUrl(url);
                if (ip != null && !ip.isEmpty()) {
                    log.debug("[IP获取] 成功获取公网IP: {} (来源: {})",
                        DesensitizedUtil.ipAddress(ip), url);
                    return ip.trim();
                }
            } catch (Exception e) {
                // 单个服务失败不记录，继续尝试下一个
            }
        }
        
        log.error("[IP获取] 所有公网IP查询服务均失败，已尝试: {}", String.join(", ", IP_CHECK_URLS));
        return null;
    }
    
    /**
     * 从指定URL获取IP地址
     * 
     * @param urlString URL地址
     * @return IP地址
     * @throws Exception 获取失败
     */
    private static String getIpFromUrl(String urlString) throws Exception {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 5秒连接超时
            connection.setReadTimeout(5000);    // 5秒读取超时
            connection.setRequestProperty("User-Agent", "FBSir-Server/1.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String ip = reader.readLine();
                
                // 验证IP格式
                if (ip != null && isValidIp(ip.trim())) {
                    return ip.trim();
                }
            }
            
            return null;
            
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    // ignore
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * 验证IP地址格式是否正确（IPv4）
     * 
     * @param ip IP地址
     * @return true-合法，false-非法
     */
    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 获取公网IP并缓存（建议在需要频繁获取IP时使用）
     * 缓存有效期：5分钟
     */
    private static String cachedIp = null;
    private static long cacheTime = 0;
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5分钟
    
    /**
     * 获取公网IP（带缓存）
     * 
     * @return 公网IP地址
     */
    public static String getPublicIpWithCache() {
        long now = System.currentTimeMillis();
        
        // 如果缓存有效，直接返回
        if (cachedIp != null && (now - cacheTime) < CACHE_DURATION) {
            log.debug("使用缓存的公网IP: {}", DesensitizedUtil.ipAddress(cachedIp));
            return cachedIp;
        }
        
        // 获取新的IP并缓存
        String ip = getPublicIp();
        if (ip != null) {
            cachedIp = ip;
            cacheTime = now;
        }
        
        return ip;
    }
}
