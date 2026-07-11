package com.wx.fbsir.framework.web.exception;

import com.wx.fbsir.common.constant.HttpStatus;
import com.wx.fbsir.common.core.domain.AjaxResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest
{
    @Test
    void missingResourceReturnsReal404InsteadOfGenericServerError()
    {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), "/druid/login.html");
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "druid/login.html");

        ResponseEntity<AjaxResult> response = handler.handleNoResourceFoundException(exception, request);

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, response.getStatusCode());
        AjaxResult body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.NOT_FOUND, body.get(AjaxResult.CODE_TAG));
        assertEquals("请求资源不存在", body.get(AjaxResult.MSG_TAG));
    }
}
