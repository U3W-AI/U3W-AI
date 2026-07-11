package com.wx.fbsir.engine.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;

/**
 * 网络信息工具类
 * 
 * 用于获取本机的IP地址信息
 *
 * @author FBSir
 * @date 2025-12-22
 */
public class NetworkInfoUtil {

    private static final Logger log = LoggerFactory.getLogger(NetworkInfoUtil.class);
    
    private static String cachedLocalIp = null;
    private static String cachedPublicIp = null;
    private static long lastPublicIpFetchTime = 0;
    private static final long PUBLIC_IP_CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存

    /**
     * 获取本地局域网IP地址
     * 
     * @return 本地IP地址，失败返回 "unknown"
     */
    public static synchronized String getLocalIp() {
        if (cachedLocalIp != null) {
            return cachedLocalIp;
        }

        try {
            // 优先获取非回环的IPv4地址
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                
                // 跳过未启用、回环、虚拟网卡
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                
                // 跳过Docker、VMware等虚拟网卡
                String name = ni.getName().toLowerCase();
                if (name.contains("docker") || name.contains("vmware") || 
                    name.contains("vbox") || name.contains("virtualbox")) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    
                    // 只要IPv4地址
                    if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress() && 
                        addr.getHostAddress().indexOf(':') == -1) {
                        cachedLocalIp = addr.getHostAddress();
                        return cachedLocalIp;
                    }
                }
            }
            
            // 降级方案：使用InetAddress.getLocalHost()
            InetAddress addr = InetAddress.getLocalHost();
            cachedLocalIp = addr.getHostAddress();
            
        } catch (Exception e) {
            log.warn("[网络信息] 获取本地IP失败: {}", e.getMessage());
            cachedLocalIp = "unknown";
        }
        
        return cachedLocalIp;
    }

    /**
     * 获取公网IP地址
     * 
     * 使用多个公共API服务，增加成功率
     * 
     * @return 公网IP地址，失败返回 null
     */
    public static synchronized String getPublicIp() {
        // 使用缓存，避免频繁请求
        long now = System.currentTimeMillis();
        if (cachedPublicIp != null && (now - lastPublicIpFetchTime) < PUBLIC_IP_CACHE_DURATION) {
            return cachedPublicIp;
        }

        // 多个公网IP查询服务（备份方案）
        String[] services = {
            "https://api.ipify.org",
            "https://ifconfig.me/ip",
            "https://icanhazip.com",
            "https://ident.me"
        };

        for (String service : services) {
            try {
                URL url = new URL(service);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000); // 3秒超时
                conn.setReadTimeout(3000);
                conn.setRequestProperty("User-Agent", "FBSir-Engine/1.0");
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()))) {
                        String ip = reader.readLine().trim();
                        
                        // 验证IP格式
                        if (ip != null && ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                            cachedPublicIp = ip;
                            lastPublicIpFetchTime = now;
                            return cachedPublicIp;
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略单个服务的失败，尝试下一个
            }
        }

        return null;
    }

    /**
     * 清除缓存（用于测试或强制刷新）
     */
    public static synchronized void clearCache() {
        cachedLocalIp = null;
        cachedPublicIp = null;
        lastPublicIpFetchTime = 0;
    }
}
