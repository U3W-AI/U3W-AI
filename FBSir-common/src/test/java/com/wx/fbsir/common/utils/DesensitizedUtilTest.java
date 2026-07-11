package com.wx.fbsir.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesensitizedUtilTest
{
    @Test
    void masksIpAddressesForApplicationLogs()
    {
        assertEquals("203.0.*.*", DesensitizedUtil.ipAddress("203.0.113.42"));
        assertEquals("127.0.*.*", DesensitizedUtil.ipAddress("127.0.0.1"));
        assertEquals("2001:*", DesensitizedUtil.ipAddress("2001:db8::1"));
        assertEquals("::*", DesensitizedUtil.ipAddress("::1"));
        assertEquals("unknown", DesensitizedUtil.ipAddress("unknown"));
        assertEquals("", DesensitizedUtil.ipAddress(null));
        assertEquals("***", DesensitizedUtil.ipAddress("not-an-ip"));
    }
}
