package com.wx.fbsir.common.utils;

/**
 * 脱敏工具类
 *
 * @author FBSir
 */
public class DesensitizedUtil
{
    /**
     * IP 地址日志脱敏。IPv4 仅保留前两段，IPv6 仅保留第一段；
     * 原始地址仍可用于访问控制和受权限保护的连接台账。
     *
     * @param ipAddress 原始 IP 地址
     * @return 适合写入普通应用日志的脱敏值
     */
    public static String ipAddress(String ipAddress)
    {
        if (StringUtils.isBlank(ipAddress))
        {
            return StringUtils.EMPTY;
        }

        String value = ipAddress.trim();
        if ("unknown".equalsIgnoreCase(value))
        {
            return "unknown";
        }

        String[] ipv4Parts = value.split("\\.", -1);
        if (ipv4Parts.length == 4)
        {
            for (String part : ipv4Parts)
            {
                if (!part.matches("\\d{1,3}"))
                {
                    return "***";
                }
            }
            return ipv4Parts[0] + "." + ipv4Parts[1] + ".*.*";
        }

        if (value.contains(":"))
        {
            if (value.startsWith("::"))
            {
                return "::*";
            }
            int separator = value.indexOf(':');
            return separator > 0 ? value.substring(0, separator) + ":*" : "::*";
        }

        return "***";
    }

    /**
     * 密码的全部字符都用*代替，比如：******
     *
     * @param password 密码
     * @return 脱敏后的密码
     */
    public static String password(String password)
    {
        if (StringUtils.isBlank(password))
        {
            return StringUtils.EMPTY;
        }
        return StringUtils.repeat('*', password.length());
    }

    /**
     * 车牌中间用*代替，如果是错误的车牌，不处理
     *
     * @param carLicense 完整的车牌号
     * @return 脱敏后的车牌
     */
    public static String carLicense(String carLicense)
    {
        if (StringUtils.isBlank(carLicense))
        {
            return StringUtils.EMPTY;
        }
        // 普通车牌
        if (carLicense.length() == 7)
        {
            carLicense = StringUtils.hide(carLicense, 3, 6);
        }
        else if (carLicense.length() == 8)
        {
            // 新能源车牌
            carLicense = StringUtils.hide(carLicense, 3, 7);
        }
        return carLicense;
    }
}
