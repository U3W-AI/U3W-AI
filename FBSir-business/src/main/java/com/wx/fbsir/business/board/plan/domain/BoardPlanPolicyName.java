package com.wx.fbsir.business.board.plan.domain;

/** Exact immutable plan-name contract shared by transport, command and catalog reads. */
public final class BoardPlanPolicyName {
    private static final int MAX_CODE_POINTS = 128;

    private BoardPlanPolicyName() {
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > MAX_CODE_POINTS) {
            return false;
        }
        int first = value.codePointAt(0);
        int last = value.codePointBefore(value.length());
        if (isBoundaryWhitespace(first) || isBoundaryWhitespace(last)) {
            return false;
        }
        return value.codePoints().noneMatch(codePoint -> {
            int type = Character.getType(codePoint);
            return type == Character.CONTROL
                    || type == Character.FORMAT
                    || type == Character.SURROGATE;
        });
    }

    private static boolean isBoundaryWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
