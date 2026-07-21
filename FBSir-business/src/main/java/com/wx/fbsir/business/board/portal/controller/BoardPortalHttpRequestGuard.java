package com.wx.fbsir.business.board.portal.controller;

import com.wx.fbsir.business.board.portal.BoardPortalBadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class BoardPortalHttpRequestGuard {

    private BoardPortalHttpRequestGuard() {
    }

    static void requireExactParameters(
            HttpServletRequest request, Set<String> required, Set<String> optional) {
        Map<String, String[]> parameters = request.getParameterMap();
        Set<String> allowed = new HashSet<>(required);
        allowed.addAll(optional);
        if (!allowed.containsAll(parameters.keySet())) {
            throw new BoardPortalBadRequestException("UNEXPECTED_QUERY_PARAMETER");
        }
        if (!parameters.keySet().containsAll(required)) {
            throw new BoardPortalBadRequestException("MISSING_QUERY_PARAMETER");
        }
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            String[] values = entry.getValue();
            if (values == null || values.length != 1) {
                throw new BoardPortalBadRequestException("DUPLICATE_QUERY_PARAMETER");
            }
            if (values[0] == null || values[0].isBlank()) {
                throw new BoardPortalBadRequestException("BLANK_QUERY_PARAMETER");
            }
        }
    }
}
