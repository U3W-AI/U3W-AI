package com.wx.fbsir.business.gitee.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.dailyassistant.domain.YuanqiAgentConfig;
import com.wx.fbsir.business.dailyassistant.service.IYuanqiAgentConfigService;
import com.wx.fbsir.business.gitee.domain.GiteeAnalysisReport;
import com.wx.fbsir.business.gitee.mapper.GiteeAnalysisReportMapper;
import com.wx.fbsir.business.gitee.util.GiteeCacheKeyUtil;
import com.wx.fbsir.business.gitee.util.GiteeOauthUtil;
import com.wx.fbsir.common.core.redis.RedisCache;
import com.wx.fbsir.common.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Gitee分析评测Service
 *
 * @author wxfbsir
 * @date 2026-01-03
 */
@Service
public class GiteeAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(GiteeAnalysisService.class);
    private static final String DEFAULT_API_ENDPOINT = "https://yuanqi.tencent.com/openapi/v1/agent/chat/completions";

    private final RedisCache redisCache;
    private final IYuanqiAgentConfigService yuanqiAgentConfigService;
    private final GiteeAnalysisReportMapper giteeAnalysisReportMapper;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public GiteeAnalysisService(RedisCache redisCache,
                                IYuanqiAgentConfigService yuanqiAgentConfigService,
                                GiteeAnalysisReportMapper giteeAnalysisReportMapper) {
        this.redisCache = redisCache;
        this.yuanqiAgentConfigService = yuanqiAgentConfigService;
        this.giteeAnalysisReportMapper = giteeAnalysisReportMapper;
        this.objectMapper = new ObjectMapper();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        factory.setReadTimeout(180000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 重新评测当前用户的Gitee数据
     *
     * @param userId 用户ID
     * @return 评测结果
     */
    public Map<String, Object> reevaluate(Long userId) {
        String token = redisCache.getCacheObject(GiteeCacheKeyUtil.getAccessTokenKey(userId));
        if (!StringUtils.hasText(token)) {
            throw new RuntimeException("请先完成Gitee授权");
        }

        // 拉取Gitee数据并构建摘要，避免直接传递大量原始信息
        JsonNode profile = fetchProfile(token);
        JsonNode repos = fetchRepos(token);
        JsonNode issues = fetchIssues(token);
        JsonNode notifications = fetchNotifications(token);

        Map<String, Object> summary = buildSummary(profile, repos, issues, notifications);
        YuanqiAgentConfig config = yuanqiAgentConfigService.selectActiveConfigByUserIdDecrypted(userId, "gitee_analysis");
        if (config == null) {
            throw new RuntimeException("未找到启用的腾讯元器智能体配置，请先配置智能体");
        }

        String appKey = config.getApiKey();
        String appId = resolveAnalysisAgentId(config);
        String apiEndpoint = StringUtils.hasText(config.getApiEndpoint()) ? config.getApiEndpoint() : DEFAULT_API_ENDPOINT;

        // 调用智能体生成评测结果，并解析落库
        String content = callYuanqi(apiEndpoint, appKey, appId, String.valueOf(userId), summary);
        Map<String, Object> analysis = parseAnalysisContent(content);
        saveAnalysisReport(userId, analysis);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysis", analysis);
        result.put("generatedAt", System.currentTimeMillis());
        return result;
    }

    private JsonNode fetchProfile(String token) {
        try {
            return GiteeOauthUtil.fetchUserProfileJson(token);
        } catch (Exception ex) {
            log.error("获取Gitee用户资料失败", ex);
            throw new RuntimeException("获取Gitee用户资料失败: " + ex.getMessage());
        }
    }

    private JsonNode fetchRepos(String token) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("per_page", "100");
            return GiteeOauthUtil.fetchUserRepos(token, params);
        } catch (Exception ex) {
            log.error("获取Gitee仓库失败", ex);
            throw new RuntimeException("获取Gitee仓库失败: " + ex.getMessage());
        }
    }

    private JsonNode fetchIssues(String token) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("per_page", "100");
            params.put("filter", "all");
            params.put("state", "all");
            return GiteeOauthUtil.fetchUserIssues(token, params);
        } catch (Exception ex) {
            log.error("获取Gitee Issues失败", ex);
            throw new RuntimeException("获取Gitee Issues失败: " + ex.getMessage());
        }
    }

    private JsonNode fetchNotifications(String token) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("per_page", "50");
            return GiteeOauthUtil.fetchNotifications(token, params);
        } catch (Exception ex) {
            log.error("获取Gitee通知失败", ex);
            throw new RuntimeException("获取Gitee通知失败: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildSummary(JsonNode profile, JsonNode repos, JsonNode issues, JsonNode notifications) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("profile", summarizeProfile(profile));
        summary.put("repoStats", summarizeRepos(repos));
        summary.put("issueStats", summarizeIssues(issues));
        summary.put("notificationStats", summarizeNotifications(notifications));
        return summary;
    }

    private Map<String, Object> summarizeProfile(JsonNode profile) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (profile == null || profile.isMissingNode()) {
            return data;
        }
        data.put("login", text(profile, "login"));
        data.put("name", text(profile, "name"));
        data.put("bio", text(profile, "bio"));
        data.put("blog", text(profile, "blog"));
        data.put("weibo", text(profile, "weibo"));
        data.put("email", text(profile, "email"));
        data.put("publicRepos", number(profile, "public_repos"));
        data.put("followers", number(profile, "followers"));
        data.put("following", number(profile, "following"));
        data.put("stared", number(profile, "stared"));
        data.put("watched", number(profile, "watched"));
        data.put("createdAt", text(profile, "created_at"));
        data.put("updatedAt", text(profile, "updated_at"));
        return data;
    }

    private Map<String, Object> summarizeRepos(JsonNode repos) {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (repos == null || !repos.isArray()) {
            return stats;
        }
        int total = repos.size();
        int forks = 0;
        int stars = 0;
        int watchers = 0;
        List<Map<String, Object>> topRepos = new ArrayList<>();
        for (JsonNode repo : repos) {
            int repoForks = number(repo, "forks_count");
            int repoStars = number(repo, "stargazers_count");
            int repoWatchers = number(repo, "watchers_count");
            forks += repoForks;
            stars += repoStars;
            watchers += repoWatchers;
            // 只保留展示需要的字段，避免详情过多
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", text(repo, "full_name"));
            item.put("description", text(repo, "description"));
            item.put("stars", repoStars);
            item.put("forks", repoForks);
            item.put("watchers", repoWatchers);
            item.put("updatedAt", text(repo, "updated_at"));
            topRepos.add(item);
        }
        topRepos.sort(Comparator.comparingInt(o -> -((Number) o.getOrDefault("stars", 0)).intValue()));
        if (topRepos.size() > 8) {
            topRepos = topRepos.subList(0, 8);
        }
        stats.put("totalRepos", total);
        stats.put("totalStars", stars);
        stats.put("totalForks", forks);
        stats.put("totalWatchers", watchers);
        stats.put("topRepos", topRepos);
        return stats;
    }

    private Map<String, Object> summarizeIssues(JsonNode issues) {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (issues == null || !issues.isArray()) {
            return stats;
        }
        int total = issues.size();
        int open = 0;
        int closed = 0;
        List<Map<String, Object>> recent = new ArrayList<>();
        for (JsonNode issue : issues) {
            String state = text(issue, "state");
            if ("open".equalsIgnoreCase(state)) {
                open += 1;
            } else if ("closed".equalsIgnoreCase(state)) {
                closed += 1;
            }
            // 只保留部分近期Issue用于摘要展示
            if (recent.size() < 6) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", text(issue, "title"));
                item.put("state", state);
                item.put("createdAt", text(issue, "created_at"));
                item.put("comments", number(issue, "comments"));
                recent.add(item);
            }
        }
        stats.put("totalIssues", total);
        stats.put("openIssues", open);
        stats.put("closedIssues", closed);
        stats.put("recentIssues", recent);
        return stats;
    }

    /**
     * 保存Gitee评测报告
     *
     * @param userId 用户ID
     * @param analysis 评测结果
     */
    public void saveAnalysisReport(Long userId, Map<String, Object> analysis) {
        if (userId == null || analysis == null || analysis.isEmpty()) {
            return;
        }
        GiteeAnalysisReport report = new GiteeAnalysisReport();
        report.setUserId(userId);
        report.setProfileScore(toInteger(analysis.get("profileScore")));
        report.setProfileLevel(toStringValue(analysis.get("profileLevel")));
        report.setCommunityScore(toInteger(analysis.get("communityScore")));
        report.setCommunityLevel(toStringValue(analysis.get("communityLevel")));
        report.setTechScore(toInteger(analysis.get("techScore")));
        report.setTechLevel(toStringValue(analysis.get("techLevel")));
        report.setTotalScore(toInteger(analysis.get("totalScore")));
        report.setTotalLevel(toStringValue(analysis.get("totalLevel")));
        report.setReportTime(DateUtils.getNowDate());
        giteeAnalysisReportMapper.insertGiteeAnalysisReport(report);
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private Map<String, Object> summarizeNotifications(JsonNode notifications) {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (notifications == null) {
            return stats;
        }
        JsonNode listNode = notifications.has("list") ? notifications.get("list") : notifications;
        if (!listNode.isArray()) {
            return stats;
        }
        int total = listNode.size();
        int unread = 0;
        for (JsonNode item : listNode) {
            if (item.has("unread") && item.get("unread").asBoolean()) {
                unread += 1;
            }
        }
        stats.put("totalNotifications", total);
        stats.put("unreadNotifications", unread);
        return stats;
    }

    private String callYuanqi(String apiEndpoint, String appKey, String appId, String userId, Map<String, Object> summary) {
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(appId)) {
            throw new RuntimeException("腾讯元器配置不完整，请检查智能体ID与API密钥");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("assistant_id", appId.trim());
        body.put("user_id", "gitee-" + userId);
        body.put("stream", false);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        Map<String, Object> textContent = new LinkedHashMap<>();
        textContent.put("type", "text");
        textContent.put("text", buildPrompt(summary));
        message.put("content", new Object[] { textContent });
        body.put("messages", Collections.singletonList(message));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + appKey.trim());
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            // 请求超时或参数错误时抛出明确异常，便于前端提示
            log.info("调用腾讯元器 - assistantId: {}, userId: {}, endpoint: {}", appId, userId, apiEndpoint);
            ResponseEntity<String> response = restTemplate.exchange(apiEndpoint, HttpMethod.POST, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("腾讯元器调用失败，状态码: " + response.getStatusCode().value());
            }
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            String responseBody = ex.getResponseBodyAsString();
            log.error("腾讯元器请求参数错误: {}", responseBody);
            throw new RuntimeException("腾讯元器请求参数有误: " + responseBody);
        }
    }

    private String buildPrompt(Map<String, Object> summary) {
        String data;
        try {
            data = objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            data = "{}";
        }
        return "你是资深研发能力评测专家。请基于输入的Gitee摘要数据进行分析，输出JSON，"
            + "不要使用Markdown或代码块。评分0-100，等级A/B/C/D。JSON字段必须包含："
            + "profileScore, profileLevel, communityScore, communityLevel, techScore, techLevel, "
            + "totalScore, totalLevel, highlights, risks, suggestions。"
            + "其中 highlights/risks/suggestions 为字符串数组。"
            + "输入数据如下：" + data;
    }

    private Map<String, Object> parseAnalysisContent(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new RuntimeException("腾讯元器返回空响应");
        }
        String content = extractMessageContent(responseBody);
        if (!StringUtils.hasText(content)) {
            throw new RuntimeException("腾讯元器响应未包含有效内容");
        }
        String json = extractJson(content);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(json, Map.class);
            return result;
        } catch (Exception e) {
            log.error("解析元器分析JSON失败，内容: {}", content);
            throw new RuntimeException("解析元器分析结果失败，请检查智能体输出格式");
        }
    }

    private String extractMessageContent(String responseBody) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            Map<String, Object> firstChoice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            if (message == null) {
                return null;
            }
            Object contentObj = message.get("content");
            if (contentObj instanceof String) {
                return (String) contentObj;
            }
            if (contentObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> contentArray = (List<Map<String, Object>>) contentObj;
                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> item : contentArray) {
                    if ("text".equals(item.get("type"))) {
                        sb.append(item.get("text"));
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.error("解析元器响应失败", e);
        }
        return null;
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String resolveAnalysisAgentId(YuanqiAgentConfig config) {
        String appId = config.getAgentId();
        String configJson = config.getConfigJson();
        if (!StringUtils.hasText(configJson)) {
            return appId;
        }
        try {
            JsonNode node = objectMapper.readTree(configJson);
            if (node.has("giteeAnalysisAgentId")) {
                String value = node.get("giteeAnalysisAgentId").asText();
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        } catch (Exception e) {
            log.warn("解析configJson失败，使用默认智能体ID");
        }
        return appId;
    }

    private String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private int number(JsonNode node, String field) {
        return node.has(field) && node.get(field).isNumber() ? node.get(field).asInt() : 0;
    }
}
