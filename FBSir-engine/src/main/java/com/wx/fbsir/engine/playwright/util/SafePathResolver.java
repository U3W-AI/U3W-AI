package com.wx.fbsir.engine.playwright.util;

import java.nio.file.Path;

/**
 * 将外部标识安全地映射到指定根目录下，阻止路径穿越和 Windows 非法文件名。
 */
public final class SafePathResolver {

    private static final int MAX_SEGMENT_LENGTH = 128;
    private static final String WINDOWS_RESERVED = "<>:\"|?*";

    private SafePathResolver() {
    }

    public static String requireSafeSegment(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        if (value.length() > MAX_SEGMENT_LENGTH) {
            throw new IllegalArgumentException(fieldName + " 长度不能超过 " + MAX_SEGMENT_LENGTH);
        }
        if (".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException(fieldName + " 不能是相对路径标记");
        }
        if (value.endsWith(".") || value.endsWith(" ")) {
            throw new IllegalArgumentException(fieldName + " 不能以点或空格结尾");
        }

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == '/' || c == '\\' || WINDOWS_RESERVED.indexOf(c) >= 0) {
                throw new IllegalArgumentException(fieldName + " 包含非法路径字符");
            }
        }
        return value;
    }

    public static Path resolveUnder(Path root, String... segments) {
        if (root == null) {
            throw new IllegalArgumentException("根目录不能为空");
        }

        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot;
        for (int i = 0; i < segments.length; i++) {
            resolved = resolved.resolve(requireSafeSegment(segments[i], "路径段" + i));
        }
        resolved = resolved.normalize();

        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("目标路径超出允许的根目录");
        }
        return resolved;
    }
}
