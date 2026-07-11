package com.wx.fbsir.business.gitee.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Gitee OAuth工具类
 *
 * @author FBSir
 * @date 2026-01-03
 */
public final class GiteeOauthUtil {
    public static final String AUTHORIZE_URL = "https://gitee.com/oauth/authorize";
    public static final String TOKEN_URL = "https://gitee.com/oauth/token";
    public static final String USER_URL = "https://gitee.com/api/v5/user";
    public static final String USER_ISSUES_URL = "https://gitee.com/api/v5/user/issues";
    public static final String USER_REPOS_URL = "https://gitee.com/api/v5/user/repos";
    public static final String NOTIFICATIONS_URL = "https://gitee.com/api/v5/notifications/threads";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final int MAX_RETRIES = 1;

    private GiteeOauthUtil() {
    }

    /**
     * 构建Gitee授权URL
     *
     * @param clientId 客户端ID
     * @param callbackUrl 回调地址
     * @return 授权地址
     */
    public static String buildAuthorizeUrl(String clientId, String callbackUrl) {
        String encoded = URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8);
        return AUTHORIZE_URL
            + "?response_type=code"
            + "&client_id=" + clientId
            + "&redirect_uri=" + encoded;
    }

    /**
     * 构建带state的Gitee授权URL
     *
     * @param clientId 客户端ID
     * @param callbackUrl 回调地址
     * @param state state参数
     * @return 授权地址
     */
    public static String buildAuthorizeUrl(String clientId, String callbackUrl, String state) {
        String encoded = URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8);
        String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);
        return AUTHORIZE_URL
            + "?response_type=code"
            + "&client_id=" + clientId
            + "&redirect_uri=" + encoded
            + "&state=" + encodedState;
    }

    /**
     * 通过授权码换取访问令牌
     *
     * @param clientId 客户端ID
     * @param clientSecret 客户端密钥
     * @param callbackUrl 回调地址
     * @param code 授权码
     * @return 访问令牌信息
     * @throws Exception 调用异常
     */
    public static GiteeOauthToken exchangeCodeForToken(String clientId, String clientSecret, String callbackUrl, String code)
        throws Exception {
        RestTemplate restTemplate = createRestTemplate();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", callbackUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        String response = executeWithRetry(() -> restTemplate.postForObject(TOKEN_URL, entity, String.class));
        if (response == null || response.isBlank()) {
            throw new Exception("gitee token响应为空");
        }

        JsonNode jsonNode = OBJECT_MAPPER.readTree(response);
        if (!jsonNode.has("access_token")) {
            String error = jsonNode.has("error") ? jsonNode.get("error").asText() : "unknown";
            String desc = jsonNode.has("error_description") ? jsonNode.get("error_description").asText() : "";
            throw new Exception("gitee获取token失败: " + error + (desc.isBlank() ? "" : (": " + desc)));
        }

        GiteeOauthToken token = new GiteeOauthToken();
        token.setAccessToken(jsonNode.get("access_token").asText());
        token.setTokenType(jsonNode.has("token_type") ? jsonNode.get("token_type").asText() : "");
        token.setRefreshToken(jsonNode.has("refresh_token") ? jsonNode.get("refresh_token").asText() : "");
        token.setExpiresIn(jsonNode.has("expires_in") ? jsonNode.get("expires_in").asLong() : 0L);
        token.setScope(jsonNode.has("scope") ? jsonNode.get("scope").asText() : "");
        token.setCreatedAt(jsonNode.has("created_at") ? jsonNode.get("created_at").asLong() : 0L);
        return token;
    }

    /**
     * 获取用户基本信息（对象）
     *
     * @param accessToken 访问令牌
     * @return 用户信息
     * @throws Exception 调用异常
     */
    public static GiteeUserProfile fetchUserProfile(String accessToken) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        String url = USER_URL + "?access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        String response = restTemplate.getForObject(url, String.class);
        if (response == null || response.isBlank()) {
            throw new Exception("gitee用户信息响应为空");
        }
        JsonNode jsonNode = OBJECT_MAPPER.readTree(response);
        if (!jsonNode.has("id")) {
            String message = jsonNode.has("message") ? jsonNode.get("message").asText() : "unknown";
            throw new Exception("gitee获取用户信息失败: " + message);
        }

        GiteeUserProfile profile = new GiteeUserProfile();
        profile.setId(jsonNode.get("id").asText());
        profile.setLogin(jsonNode.has("login") ? jsonNode.get("login").asText() : "");
        profile.setName(jsonNode.has("name") ? jsonNode.get("name").asText() : "");
        profile.setAvatarUrl(jsonNode.has("avatar_url") ? jsonNode.get("avatar_url").asText() : "");
        profile.setEmail(jsonNode.has("email") ? jsonNode.get("email").asText() : "");
        return profile;
    }

    /**
     * 获取用户基本信息（JSON）
     *
     * @param accessToken 访问令牌
     * @return JSON结果
     * @throws Exception 调用异常
     */
    public static JsonNode fetchUserProfileJson(String accessToken) throws Exception {
        return fetchJson(USER_URL, accessToken, Collections.emptyMap());
    }

    /**
     * 获取用户仓库列表
     *
     * @param accessToken 访问令牌
     * @param params 查询参数
     * @return JSON结果
     * @throws Exception 调用异常
     */
    public static JsonNode fetchUserRepos(String accessToken, Map<String, String> params) throws Exception {
        return fetchJson(USER_REPOS_URL, accessToken, params);
    }

    /**
     * 获取用户Issue列表
     *
     * @param accessToken 访问令牌
     * @param params 查询参数
     * @return JSON结果
     * @throws Exception 调用异常
     */
    public static JsonNode fetchUserIssues(String accessToken, Map<String, String> params) throws Exception {
        Map<String, String> resolvedParams = new LinkedHashMap<>();
        if (params != null && !params.isEmpty()) {
            resolvedParams.putAll(params);
        }
        String filter = resolvedParams.get("filter");
        if (filter == null || filter.isBlank()) {
            filter = "assigned";
            resolvedParams.put("filter", filter);
        }
        String state = resolvedParams.get("state");
        if (state == null || state.isBlank()) {
            resolvedParams.put("state", "open");
        }
        if ("all".equalsIgnoreCase(filter)) {
            // Gitee接口不支持filter=all，拆分为assigned/created后合并
            Map<String, String> assignedParams = new LinkedHashMap<>(resolvedParams);
            assignedParams.put("filter", "assigned");
            Map<String, String> createdParams = new LinkedHashMap<>(resolvedParams);
            createdParams.put("filter", "created");
            JsonNode assigned = fetchJson(USER_ISSUES_URL, accessToken, assignedParams);
            JsonNode created = fetchJson(USER_ISSUES_URL, accessToken, createdParams);
            return mergeIssueResults(assigned, created, resolvedParams);
        }
        return fetchJson(USER_ISSUES_URL, accessToken, resolvedParams);
    }

    /**
     * 获取用户通知
     *
     * @param accessToken 访问令牌
     * @param params 查询参数
     * @return JSON结果
     * @throws Exception 调用异常
     */
    public static JsonNode fetchNotifications(String accessToken, Map<String, String> params) throws Exception {
        return fetchJson(NOTIFICATIONS_URL, accessToken, params);
    }

    private static JsonNode fetchJson(String baseUrl, String accessToken, Map<String, String> params) throws Exception {
        RestTemplate restTemplate = createRestTemplate();
        String url = buildUrl(baseUrl, accessToken, params);
        String response = executeWithRetry(() -> restTemplate.getForObject(url, String.class));
        if (response == null || response.isBlank()) {
            throw new Exception("gitee响应为空");
        }
        return OBJECT_MAPPER.readTree(response);
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    private static String executeWithRetry(Supplier<String> request) throws Exception {
        int attempt = 0;
        while (true) {
            try {
                return request.get();
            } catch (ResourceAccessException ex) {
                if (attempt >= MAX_RETRIES) {
                    throw ex;
                }
                attempt += 1;
            }
        }
    }

    private static String buildUrl(String baseUrl, String accessToken, Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        builder.append(baseUrl);
        builder.append("?access_token=").append(URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || key.isBlank() || value == null) {
                    continue;
                }
                if ("access_token".equalsIgnoreCase(key)) {
                    continue;
                }
                builder.append("&")
                    .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        return builder.toString();
    }

    private static JsonNode mergeIssueResults(JsonNode first, JsonNode second, Map<String, String> params) {
        if (first == null || !first.isArray()) {
            return first;
        }
        if (second == null || !second.isArray()) {
            return first;
        }
        ArrayNode merged = OBJECT_MAPPER.createArrayNode();
        Set<String> seen = new HashSet<>();
        // 去重合并Issue列表，避免重复展示
        addUniqueIssues(merged, seen, first);
        addUniqueIssues(merged, seen, second);
        List<JsonNode> list = new ArrayList<>();
        merged.forEach(list::add);
        // 按请求的排序规则排序，并按分页参数截断
        sortIssues(list, params);
        int limit = parseInt(params == null ? null : params.get("per_page"));
        ArrayNode result = OBJECT_MAPPER.createArrayNode();
        int count = limit > 0 ? Math.min(limit, list.size()) : list.size();
        for (int i = 0; i < count; i++) {
            result.add(list.get(i));
        }
        return result;
    }

    private static void addUniqueIssues(ArrayNode target, Set<String> seen, JsonNode source) {
        for (JsonNode issue : source) {
            String id = issue.has("id") ? issue.get("id").asText() : null;
            if (id != null && !id.isBlank()) {
                if (seen.add(id)) {
                    target.add(issue);
                }
            } else {
                target.add(issue);
            }
        }
    }

    private static void sortIssues(List<JsonNode> list, Map<String, String> params) {
        if (list.isEmpty() || params == null) {
            return;
        }
        String sort = params.get("sort");
        if (sort == null || sort.isBlank()) {
            return;
        }
        String key = null;
        if ("created".equalsIgnoreCase(sort)) {
            key = "created_at";
        } else if ("updated".equalsIgnoreCase(sort) || "updated_at".equalsIgnoreCase(sort)) {
            key = "updated_at";
        }
        if (key == null) {
            return;
        }
        String direction = params.get("direction");
        final int multiplier = "asc".equalsIgnoreCase(direction) ? 1 : -1;
        final String sortKey = key;
        list.sort((left, right) -> {
            long leftTime = parseIssueTime(left, sortKey);
            long rightTime = parseIssueTime(right, sortKey);
            return Long.compare(leftTime, rightTime) * multiplier;
        });
    }

    private static long parseIssueTime(JsonNode issue, String key) {
        if (issue == null || key == null) {
            return 0L;
        }
        String value = issue.has(key) ? issue.get(key).asText() : "";
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Gitee OAuth令牌对象
     */
    public static final class GiteeOauthToken {
        private String accessToken;
        private String tokenType;
        private String refreshToken;
        private long expiresIn;
        private String scope;
        private long createdAt;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public long getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
        }

        @Override
        public String toString() {
            return "GiteeOauthToken{" +
                "accessToken='" + mask(accessToken) + '\'' +
                ", tokenType='" + tokenType + '\'' +
                ", refreshToken='" + mask(refreshToken) + '\'' +
                ", expiresIn=" + expiresIn +
                ", scope='" + scope + '\'' +
                ", createdAt=" + createdAt +
                '}';
        }
    }

    /**
     * Gitee用户资料对象
     */
    public static final class GiteeUserProfile {
        private String id;
        private String login;
        private String name;
        private String avatarUrl;
        private String email;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 6) {
            return "******";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 3);
    }
}
