package com.wx.fbsir.engine.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备信息工具类
 * 
 * 收集本机硬件和系统信息，生成设备指纹
 * 用于主节点识别同一设备的多次连接
 * 
 * 设备指纹生成策略：
 * - 优先使用稳定的硬件标识（物理网卡MAC、主板序列号等）
 * - 结合操作系统信息和主机名增强唯一性
 * - 排除虚拟网卡、WiFi等可能变化的标识
 *
 * @author FBSir
 * @date 2025-12-15
 */
public class DeviceInfoUtil {

    private static final Logger log = LoggerFactory.getLogger(DeviceInfoUtil.class);

    private static String cachedDeviceId = null;
    private static Map<String, String> cachedDeviceInfo = null;

    /**
     * 获取设备信息
     *
     * @return 设备信息Map
     */
    public static synchronized Map<String, String> getDeviceInfo() {
        if (cachedDeviceInfo != null) {
            return cachedDeviceInfo;
        }

        Map<String, String> info = new HashMap<>();
        
        try {
            // 操作系统信息
            info.put("osName", System.getProperty("os.name", "unknown"));
            info.put("osVersion", System.getProperty("os.version", "unknown"));
            info.put("osArch", System.getProperty("os.arch", "unknown"));
            
            // Java信息
            info.put("javaVersion", System.getProperty("java.version", "unknown"));
            info.put("javaVendor", System.getProperty("java.vendor", "unknown"));
            
            // 主机信息
            info.put("hostname", getHostname());
            info.put("username", System.getProperty("user.name", "unknown"));
            
            // MAC地址（优先物理网卡）
            info.put("macAddress", getPhysicalMacAddress());
            
            // 硬件序列号（如果可获取）
            String hardwareId = getHardwareSerialNumber();
            if (hardwareId != null && !hardwareId.isEmpty()) {
                info.put("hardwareId", hardwareId);
            }
            
        } catch (Exception e) {
            log.warn("[设备信息] 获取设备信息时发生错误: {}", e.getMessage());
        }

        cachedDeviceInfo = info;
        return info;
    }

    /**
     * 获取设备唯一ID（基于稳定硬件信息生成的MD5）
     * 
     * 生成策略：
     * 1. 优先使用硬件序列号（最稳定）
     * 2. 其次使用物理网卡MAC（较稳定，排除虚拟网卡）
     * 3. 结合主机名和用户名（增强唯一性）
     *
     * @return 设备ID
     */
    public static synchronized String getDeviceId() {
        if (cachedDeviceId != null) {
            return cachedDeviceId;
        }

        try {
            Map<String, String> info = getDeviceInfo();
            
            // 组合关键信息生成指纹（使用稳定的标识）
            StringBuilder fingerprintBuilder = new StringBuilder();
            
            // 硬件序列号（最稳定）
            String hardwareId = info.get("hardwareId");
            if (hardwareId != null && !hardwareId.isEmpty() && !"unknown".equals(hardwareId)) {
                fingerprintBuilder.append(hardwareId).append("|");
            }
            
            // 物理网卡MAC（较稳定）
            fingerprintBuilder.append(info.getOrDefault("macAddress", "")).append("|");
            
            // 操作系统信息
            fingerprintBuilder.append(info.getOrDefault("osName", "")).append("|");
            fingerprintBuilder.append(info.getOrDefault("osArch", "")).append("|");
            
            // 主机名和用户名
            fingerprintBuilder.append(info.getOrDefault("hostname", "")).append("|");
            fingerprintBuilder.append(info.getOrDefault("username", ""));
            
            String fingerprint = fingerprintBuilder.toString();
            
            // 计算MD5
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(fingerprint.getBytes());
            
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            
            cachedDeviceId = sb.toString();
            
        } catch (Exception e) {
            log.error("[设备信息] 生成设备ID失败: {}", e.getMessage());
            cachedDeviceId = "unknown-" + System.currentTimeMillis();
        }

        return cachedDeviceId;
    }

    /**
     * 获取主机名
     */
    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 获取物理网卡MAC地址
     * 
     * 排除规则：
     * - 回环接口（127.0.0.1）
     * - 虚拟网卡（VMware、VirtualBox、Docker等）
     * - 未启用的网卡
     */
    private static String getPhysicalMacAddress() {
        try {
            List<String> physicalMacs = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                
                // 跳过回环接口
                if (ni.isLoopback()) {
                    continue;
                }
                
                // 跳过虚拟接口
                if (ni.isVirtual()) {
                    continue;
                }
                
                // 跳过未启用的接口
                if (!ni.isUp()) {
                    continue;
                }
                
                // 跳过常见虚拟网卡
                String name = ni.getName().toLowerCase();
                String displayName = ni.getDisplayName() != null ? ni.getDisplayName().toLowerCase() : "";
                if (isVirtualInterface(name, displayName)) {
                    continue;
                }
                
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length == 6) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X", mac[i]));
                        if (i < mac.length - 1) {
                            sb.append(":");
                        }
                    }
                    physicalMacs.add(sb.toString());
                }
            }
            
            // 返回第一个物理网卡MAC
            if (!physicalMacs.isEmpty()) {
                return physicalMacs.get(0);
            }
            
        } catch (Exception e) {
            log.warn("[设备信息] 获取MAC地址失败: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * 判断是否为虚拟网卡
     */
    private static boolean isVirtualInterface(String name, String displayName) {
        String[] virtualKeywords = {
            "vmware", "vmnet", "vbox", "virtualbox", "docker", 
            "veth", "br-", "virbr", "tap", "tun", "vpn",
            "hyper-v", "virtual", "vnic", "vswitch"
        };
        
        for (String keyword : virtualKeywords) {
            if (name.contains(keyword) || displayName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取硬件序列号
     * 
     * Windows: WMIC 命令获取主板序列号
     * Linux: /sys/class/dmi/id/product_serial 或 dmidecode
     * Mac: system_profiler 获取硬件UUID
     */
    private static String getHardwareSerialNumber() {
        String os = System.getProperty("os.name", "").toLowerCase();
        
        try {
            if (os.contains("win")) {
                return executeCommand("wmic baseboard get serialnumber");
            } else if (os.contains("mac")) {
                return executeCommand("system_profiler SPHardwareDataType | grep 'Hardware UUID'");
            } else if (os.contains("linux")) {
                // 尝试读取 DMI 信息
                String serial = executeCommand("cat /sys/class/dmi/id/product_serial 2>/dev/null");
                if (serial == null || serial.isEmpty() || "None".equals(serial)) {
                    serial = executeCommand("cat /sys/class/dmi/id/board_serial 2>/dev/null");
                }
                return serial;
            }
        } catch (Exception e) {
            // 获取失败不影响主流程
        }
        return null;
    }

    /**
     * 执行系统命令并获取输出
     */
    private static String executeCommand(String command) {
        try {
            String[] cmd;
            String os = System.getProperty("os.name", "").toLowerCase();
            
            if (os.contains("win")) {
                cmd = new String[]{"cmd", "/c", command};
            } else {
                cmd = new String[]{"/bin/sh", "-c", command};
            }
            
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // 跳过空行和标题行
                if (!line.isEmpty() && !line.equalsIgnoreCase("serialnumber") 
                    && !line.contains("Hardware UUID:")) {
                    output.append(line);
                }
            }
            
            process.waitFor();
            String result = output.toString().trim();
            
            // 清理输出
            if (result.contains(":")) {
                result = result.substring(result.indexOf(":") + 1).trim();
            }
            
            return result.isEmpty() ? null : result;
            
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取本机IP地址（内网IP）
     * 
     * @return IP 地址
     */
    public static String getLocalIp() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();
                
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') == -1) {
                        return addr.getHostAddress();
                    }
                }
            }
            
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("[设备信息] 获取本机IP失败: {}", e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * 获取本机公网出口IP
     * 通过访问外部服务获取真实出口IP
     * 
     * @return 公网IP地址，获取失败返回null
     */
    public static String getPublicIp() {
        // 多个公网IP查询服务，按优先级尝试
        String[] ipServices = {
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://ifconfig.me/ip",
            "https://api.ip.sb/ip"
        };
        
        for (String service : ipServices) {
            try {
                java.net.URL url = new java.net.URL(service);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");
                
                if (conn.getResponseCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()))) {
                        String ip = reader.readLine();
                        if (ip != null && !ip.isEmpty() && isValidIp(ip.trim())) {
                            return ip.trim();
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                // 尝试下一个服务
            }
        }
        
        log.warn("[设备信息] 无法获取公网IP，所有服务均不可用");
        return null;
    }
    
    /**
     * 验证IP地址格式是否有效
     */
    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        // 简单的IPv4格式验证
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
}
