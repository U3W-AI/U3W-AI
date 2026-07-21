package com.wx.fbsir.framework.web.service;

import com.wx.fbsir.common.constant.CacheConstants;
import com.wx.fbsir.common.constant.Constants;
import com.wx.fbsir.common.core.domain.model.LoginUser;
import com.wx.fbsir.common.core.redis.RedisCache;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenServiceAuthorizationHeaderTest {

    private static final String SECRET =
            "w4b2c-token-secret-must-be-longer-than-thirty-two-characters";
    private static final String TOKEN_ID = "login-token-id";

    private TokenService tokenService;
    private RedisCache redisCache;
    private LoginUser loginUser;
    private String jwt;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        redisCache = mock(RedisCache.class);
        ReflectionTestUtils.setField(tokenService, "header", "Authorization");
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
        ReflectionTestUtils.setField(tokenService, "expireTime", 30);
        ReflectionTestUtils.setField(tokenService, "redisCache", redisCache);
        tokenService.validateSecret();
        loginUser = new LoginUser();
        loginUser.setToken(TOKEN_ID);
        jwt = Jwts.builder()
                .claim(Constants.LOGIN_USER_KEY, TOKEN_ID)
                .claim(Constants.JWT_USERNAME, "admin")
                .signWith(SignatureAlgorithm.HS512, SECRET)
                .compact();
        when(redisCache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + TOKEN_ID))
                .thenReturn(loginUser);
    }

    @Test
    void exactlyOneBearerHeaderAuthenticatesTheCachedLogin() {
        assertSame(loginUser, tokenService.getLoginUser(request("Bearer " + jwt)));
    }

    @Test
    void bareJwtIsRejectedBeforeRedisLookup() {
        assertNull(tokenService.getLoginUser(request(jwt)));
        verify(redisCache, never()).getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + TOKEN_ID);
    }

    @Test
    void doubledBearerPrefixIsRejectedBeforeRedisLookup() {
        assertNull(tokenService.getLoginUser(request("Bearer Bearer " + jwt)));
        verify(redisCache, never()).getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + TOKEN_ID);
    }

    @Test
    void duplicateAuthorizationValuesAreRejectedBeforeRedisLookup() {
        HttpServletRequest request = request("Bearer " + jwt, "Bearer oauth-opaque-token");

        assertNull(tokenService.getLoginUser(request));
        verify(redisCache, never()).getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + TOKEN_ID);
    }

    @Test
    void opaqueOAuthBearerCannotResolveARuoYiLogin() {
        reset(redisCache);

        assertNull(tokenService.getLoginUser(
                request("Bearer QWxwaGFCZXRhR2FtbWFEZWx0YUVwc2lsb25aZXRhMTIz")));
        verify(redisCache, never()).getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + TOKEN_ID);
    }

    private static HttpServletRequest request(String... authorizationValues) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        List<String> values = List.of(authorizationValues);
        when(request.getHeader("Authorization"))
                .thenReturn(values.isEmpty() ? null : values.get(0));
        when(request.getHeaders("Authorization"))
                .thenReturn(Collections.enumeration(values));
        return request;
    }
}
