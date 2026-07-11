package com.wx.fbsir.business.gitee.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.wx.fbsir.business.gitee.domain.GiteeAuthState;
import com.wx.fbsir.business.gitee.mapper.GiteeBindMapper;
import com.wx.fbsir.business.gitee.util.GiteeCacheKeyUtil;
import com.wx.fbsir.business.gitee.util.GiteeOauthUtil;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.redis.RedisCache;
import com.wx.fbsir.common.utils.SecurityUtils;
import com.wx.fbsir.common.utils.uuid.IdUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Gitee授权资料Controller
 *
 * @author FBSir
 * @date 2026-01-03
 */
@RestController
@RequestMapping("/business/gitee")
public class GiteeProfileController {
    private static final Logger log = LoggerFactory.getLogger(GiteeProfileController.class);
    private static final int AUTH_STATE_EXPIRE_MINUTES = 10;
    private static final String DEFAULT_REDIRECT_PATH = "/user/profile/gitee";

    @Value("${gitee.oauth.client-id:}")
    private String clientId;

    @Value("${gitee.oauth.callback-url:}")
    private String callbackUrl;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private GiteeBindMapper giteeBindMapper;

    /**
     * 查询当前用户授权状态
     *
     * @return 授权状态
     */
    @GetMapping("/status")
    public AjaxResult status() {
        Long userId = SecurityUtils.getUserId();
        String token = redisCache.getCacheObject(GiteeCacheKeyUtil.getAccessTokenKey(userId));
        Map<String, Object> data = Map.of("authorized", StringUtils.isNotBlank(token));
        return AjaxResult.success(data);
    }

    /**
     * 获取授权跳转链接
     *
     * @param redirect 授权完成后的跳转路径
     * @param request 请求
     * @return 授权链接
     */
    @GetMapping("/authorize")
    public AjaxResult authorize(@RequestParam(value = "redirect", required = false) String redirect,
                                HttpServletRequest request) {
        if (StringUtils.isBlank(clientId)) {
            return AjaxResult.error("gitee clientId未配置");
        }

        // 生成并缓存state，防止CSRF并记录授权后的跳转地址
        String state = IdUtils.fastUUID();
        GiteeAuthState authState = new GiteeAuthState();
        authState.setUserId(SecurityUtils.getUserId());
        authState.setRedirect(normalizeRedirect(redirect));
        redisCache.setCacheObject(GiteeCacheKeyUtil.getAuthStateKey(state), authState,
            AUTH_STATE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        String resolvedCallbackUrl = resolveCallbackUrl(request);
        String authorizeUrl = GiteeOauthUtil.buildAuthorizeUrl(clientId, resolvedCallbackUrl, state);
        Map<String, Object> data = Map.of("url", authorizeUrl);
        return AjaxResult.success(data);
    }

    /**
     * 获取当前用户的Gitee资料
     *
     * @return 用户资料
     */
    @GetMapping("/profile")
    public AjaxResult profile() {
        String token = getAccessToken();
        if (token == null) {
            return AjaxResult.error("请先完成Gitee授权");
        }
        try {
            JsonNode profile = GiteeOauthUtil.fetchUserProfileJson(token);
            return AjaxResult.success(profile);
        } catch (Exception ex) {
            log.error("获取Gitee用户资料失败", ex);
            return AjaxResult.error(ex.getMessage());
        }
    }

    /**
     * 获取用户仓库列表
     *
     * @param params 查询参数
     * @return 仓库数据
     */
    @GetMapping("/repos")
    public AjaxResult repos(@RequestParam Map<String, String> params) {
        String token = getAccessToken();
        if (token == null) {
            return AjaxResult.error("请先完成Gitee授权");
        }
        try {
            JsonNode repos = GiteeOauthUtil.fetchUserRepos(token, params);
            return AjaxResult.success(repos);
        } catch (Exception ex) {
            log.error("获取Gitee仓库失败", ex);
            return AjaxResult.error(ex.getMessage());
        }
    }

    /**
     * 获取用户Issue列表
     *
     * @param params 查询参数
     * @return Issue数据
     */
    @GetMapping("/issues")
    public AjaxResult issues(@RequestParam Map<String, String> params) {
        String token = getAccessToken();
        if (token == null) {
            return AjaxResult.error("请先完成Gitee授权");
        }
        try {
            JsonNode issues = GiteeOauthUtil.fetchUserIssues(token, params);
            return AjaxResult.success(issues);
        } catch (Exception ex) {
            log.error("获取Gitee Issues失败", ex);
            return AjaxResult.error(ex.getMessage());
        }
    }

    /**
     * 获取用户通知列表
     *
     * @param params 查询参数
     * @return 通知数据
     */
    @GetMapping("/notifications")
    public AjaxResult notifications(@RequestParam Map<String, String> params) {
        String token = getAccessToken();
        if (token == null) {
            return AjaxResult.error("请先完成Gitee授权");
        }
        try {
            JsonNode notifications = GiteeOauthUtil.fetchNotifications(token, params);
            return AjaxResult.success(notifications);
        } catch (Exception ex) {
            log.error("获取Gitee通知失败", ex);
            return AjaxResult.error(ex.getMessage());
        }
    }

    /**
     * 解除当前用户Gitee绑定
     *
     * @return 操作结果
     */
    @PostMapping("/unbind")
    public AjaxResult unbind() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            return AjaxResult.error("未获取到用户信息");
        }
        // 同步清理授权token与绑定关系
        redisCache.deleteObject(GiteeCacheKeyUtil.getAccessTokenKey(userId));
        giteeBindMapper.deleteByUserId(userId);
        return AjaxResult.success();
    }

    private String getAccessToken() {
        Long userId = SecurityUtils.getUserId();
        String token = redisCache.getCacheObject(GiteeCacheKeyUtil.getAccessTokenKey(userId));
        return StringUtils.isBlank(token) ? null : token;
    }

    private String resolveCallbackUrl(HttpServletRequest request) {
        if (StringUtils.isNotBlank(callbackUrl)) {
            return callbackUrl;
        }
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (StringUtils.isBlank(scheme)) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("Host");
        if (StringUtils.isBlank(host)) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port > 0 && port != 80 && port != 443) {
                host = host + ":" + port;
            }
        }
        String contextPath = request.getContextPath();
        String path = (contextPath == null || contextPath.isBlank())
            ? "/auth"
            : contextPath + "/auth";
        return scheme + "://" + host + path;
    }

    private String normalizeRedirect(String redirect) {
        if (StringUtils.isBlank(redirect)) {
            return DEFAULT_REDIRECT_PATH;
        }
        if (redirect.startsWith("http") || redirect.startsWith("//")) {
            return DEFAULT_REDIRECT_PATH;
        }
        if (!redirect.startsWith("/")) {
            return "/" + redirect;
        }
        return redirect;
    }
}
