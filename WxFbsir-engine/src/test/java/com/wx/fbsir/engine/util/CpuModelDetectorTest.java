package com.wx.fbsir.engine.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("CpuModelDetector 测试")
class CpuModelDetectorTest {

    @Test
    @DisplayName("能处理 Linux model name 前缀")
    void shouldNormalizeLinuxCpuModelLine() {
        String normalized = CpuModelDetector.normalizeCpuModelLine(
            "model name\t: Intel(R) Core(TM) i5-10400F CPU @ 2.90GHz");

        assertEquals("Intel(R) Core(TM) i5-10400F CPU @ 2.90GHz", normalized);
    }

    @Test
    @DisplayName("会忽略 Windows wmic 表头")
    void shouldIgnoreWmicHeaderLine() {
        assertNull(CpuModelDetector.normalizeCpuModelLine("Name"));
    }

    @Test
    @DisplayName("可从 Windows 输出中找到真实 CPU 型号")
    void shouldPickFirstRealCpuModelLine() {
        String model = CpuModelDetector.firstCpuModelLine(List.of(
            "",
            "Name",
            "Intel(R) Core(TM) i5-10400F CPU @ 2.90GHz"
        ));

        assertEquals("Intel(R) Core(TM) i5-10400F CPU @ 2.90GHz", model);
    }
}
