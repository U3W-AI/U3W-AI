package com.wx.fbsir.business.officialaccount.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.officialaccount.domain.WcOfficeAccount;
import com.wx.fbsir.common.utils.AesEncryptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.wx.fbsir.common.config.FBSirConfig;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信公众号API服务
 * 
 * 封装微信公众号草稿箱API调用
 * 
 * @author FBSir
 * @date 2025-12-08
 */
@Service
public class WechatOfficialApiService {
    
    private static final Logger log = LoggerFactory.getLogger(WechatOfficialApiService.class);
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 微信公众号API地址
    private static final String WECHAT_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";
    private static final String WECHAT_DRAFT_ADD_URL = "https://api.weixin.qq.com/cgi-bin/draft/add?access_token=%s";
    private static final String WECHAT_MATERIAL_UPLOAD_URL = "https://api.weixin.qq.com/cgi-bin/material/add_material?access_token=%s&type=%s";
    
    /**
     * 获取微信公众号access_token
     * 
     * @param account 公众号配置（包含AES加密的appId和appSecret）
     * @return access_token
     * @throws Exception 如果获取失败
     */
    public String getAccessToken(WcOfficeAccount account) throws Exception {
        // 使用AES解密appId和appSecret
        String appId;
        String appSecret;
        
        try {
            appId = AesEncryptUtils.decrypt(account.getAppId());
            appSecret = AesEncryptUtils.decrypt(account.getAppSecret());
            log.debug("AppID和AppSecret解密成功");
        } catch (Exception e) {
            log.error("解密公众号配置失败", e);
            throw new Exception("解密配置失败，请检查AES密钥配置：" + e.getMessage());
        }
        
        String url = String.format(WECHAT_TOKEN_URL, appId, appSecret);
        
        log.debug("获取微信access_token - AppID: {}", appId);
        
        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            
            if (jsonNode.has("access_token")) {
                String accessToken = jsonNode.get("access_token").asText();
                log.debug("获取access_token成功");
                return accessToken;
            } else {
                String errcode = jsonNode.has("errcode") ? jsonNode.get("errcode").asText() : "unknown";
                String errmsg = jsonNode.has("errmsg") ? jsonNode.get("errmsg").asText() : "unknown error";
                log.error("[微信API] 获取access_token失败 - 错误码: {}, 错误信息: {}", errcode, errmsg);
                throw new Exception("获取access_token失败(" + errcode + "): " + errmsg);
            }
        } catch (Exception e) {
            if (e.getMessage().contains("获取access_token失败")) {
                throw e; // 直接抛出业务异常，避免重复包装
            }
            log.error("[微信API] 网络请求异常 - AppID: {}, 错误: {}", appId, e.getMessage());
            throw new Exception("微信API网络异常: " + e.getMessage());
        }
    }
    
    /**
     * 上传图片素材到微信公众号（推荐使用，每次都重新获取token）
     * 
     * @param account 公众号配置（包含AES加密的appId和appSecret）
     * @param picUrl 图片URL
     * @return Map包含media_id和url
     * @throws Exception 如果上传失败
     */
    public Map<String, String> uploadImageMaterial(WcOfficeAccount account, String picUrl) throws Exception {
        log.info("开始上传图片素材（现用现获取token模式）");
        
        // 每次都重新获取access_token，避免token过期问题
        String accessToken = getAccessToken(account);
        
        // 调用实际上传方法
        return uploadImageMaterialInternal(accessToken, picUrl);
    }
    
    /**
     * 上传图片素材到微信公众号（内部方法）
     * 
     * @param accessToken 访问令牌
     * @param picUrl 图片URL
     * @return Map包含media_id和url
     * @throws Exception 如果上传失败
     */
    private Map<String, String> uploadImageMaterialInternal(String accessToken, String picUrl) throws Exception {
        if (picUrl == null || picUrl.trim().isEmpty()) {
            throw new Exception("图片URL不能为空");
        }
        
        String uploadUrl = "https://api.weixin.qq.com/cgi-bin/material/add_material?access_token=" + accessToken + "&type=image";
        
        // 声明tempFile变量在外部，以便finally块访问
        Path tempFile = null;
        
        try {
            // 1. 准备临时文件路径
            final String fileName;  // 声明为final
            
            try {
                // 从urL提取文件名
                String originalFileName;
                int lastSlash = picUrl.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < picUrl.length() - 1) {
                    originalFileName = picUrl.substring(lastSlash + 1);
                } else {
                    originalFileName = "cover.jpg";
                }
                fileName = originalFileName;
                
                // 检查是否是本地文件
                File localFile = null;
                boolean useLocalFile = false;
                
                if (picUrl.contains("/profile/")) {
                    String relativePath = picUrl.substring(picUrl.indexOf("/profile/") + "/profile/".length());
                    String localPath = FBSirConfig.getProfile() + "/" + relativePath;
                    localFile = new File(localPath);
                    
                    if (localFile.exists() && localFile.isFile()) {
                        useLocalFile = true;
                        tempFile = localFile.toPath();
                    }
                }
                
                // 如果不是本地文件，下载到临时文件
                if (!useLocalFile) {
                    tempFile = Files.createTempFile("wechat_upload_", ".tmp");
                    
                    // 下载图片到临时文件
                    try (InputStream in = new java.net.URL(picUrl).openStream()) {
                        Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) {
                        log.error("下载图片失败: {}", picUrl);
                        throw new Exception("下载图片失败：" + e.getMessage());
                    }
                }
                
                // 验证文件大小
                long fileSize = Files.size(tempFile);
                if (fileSize < 100) {
                    throw new Exception("图片文件太小（" + fileSize + " bytes），可能不是有效的图片文件");
                }
                
                final int MAX_SIZE = 2 * 1024 * 1024; // 2MB
                if (fileSize > MAX_SIZE) {
                    throw new Exception("图片文件过大（" + (fileSize / 1024) + " KB），微信限制最大2MB");
                }
                
            } catch (Exception e) {
                log.error("准备图片文件失败，picUrl: {}, 错误: {}", picUrl, e.getMessage());
                throw new Exception("准备图片文件失败：" + e.getMessage());
            }
            
            // 2. 使用curl命令上传
            ProcessBuilder processBuilder = new ProcessBuilder(
                "curl", uploadUrl, "-F", "media=@" + tempFile.toAbsolutePath()
            );
            
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new Exception("curl命令执行失败，退出码: " + exitCode);
            }
            
            // 3. 解析响应
            JsonNode jsonNode = objectMapper.readTree(response.toString());
            
            if (jsonNode.has("media_id")) {
                String mediaId = jsonNode.get("media_id").asText();
                String materialUrl = jsonNode.has("url") ? jsonNode.get("url").asText() : "";
                
                Map<String, String> result = new HashMap<>();
                result.put("media_id", mediaId);
                result.put("url", materialUrl);
                return result;
            } else {
                String errcode = jsonNode.has("errcode") ? jsonNode.get("errcode").asText() : "unknown";
                String errmsg = jsonNode.has("errmsg") ? jsonNode.get("errmsg").asText() : "unknown error";
                log.error("上传图片素材失败，errcode: {}, errmsg: {}", errcode, errmsg);
                throw new Exception("上传图片素材失败：" + errmsg + " (错误码: " + errcode + ")");
            }
        } catch (Exception e) {
            if (e.getMessage().contains("上传图片素材失败") || e.getMessage().contains("准备图片文件失败")) {
                throw e;
            }
            log.error("调用微信API上传图片素材异常: {}", e.getMessage());
            throw new Exception("调用微信API异常：" + e.getMessage());
        } finally {
            // 清理临时文件（不删除本地文件）
            if (tempFile != null && tempFile.toString().contains("wechat_upload_")) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.debug("临时文件已删除: {}", tempFile);
                } catch (Exception e) {
                    log.warn("删除临时文件失败: {}", tempFile, e);
                }
            }
        }
    }
    
    /**
     * 验证公众号配置并上传封面图
     * 此方法用于在保存配置时验证公众号的appId、appSecret是否正确，
     * 以及是否能成功上传素材到公众号
     * 
     * @param appId 开发者ID（明文）
     * @param appSecret 开发者密钥（明文）
     * @param picUrl 封面图URL
     * @return Map包含media_id和url
     * @throws Exception 如果验证失败或上传失败
     */
    public Map<String, String> verifyAndUploadCover(String appId, String appSecret, String picUrl) throws Exception {
        // 1. 获取access_token（验证appId和appSecret是否正确）
        String tokenUrl = String.format(WECHAT_TOKEN_URL, appId, appSecret);
        
        try {
            String response = restTemplate.getForObject(tokenUrl, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            
            if (!jsonNode.has("access_token")) {
                String errcode = jsonNode.has("errcode") ? jsonNode.get("errcode").asText() : "unknown";
                String errmsg = jsonNode.has("errmsg") ? jsonNode.get("errmsg").asText() : "unknown error";
                log.error("验证公众号配置失败 - errcode: {}, errmsg: {}", errcode, errmsg);
                
                // 提供更友好的错误提示
                if ("40013".equals(errcode)) {
                    throw new Exception("AppID无效，请检查开发者ID是否正确");
                } else if ("40001".equals(errcode)) {
                    throw new Exception("AppSecret无效，请检查开发者密钥是否正确");
                } else if ("40164".equals(errcode)) {
                    throw new Exception("IP地址未加入白名单，请在微信公众号后台添加服务器IP到白名单");
                } else {
                    throw new Exception("验证公众号配置失败：" + errmsg + " (错误码: " + errcode + ")");
                }
            }
            
            String accessToken = jsonNode.get("access_token").asText();
            
            // 2. 上传封面图到素材库
            return uploadImageMaterialInternal(accessToken, picUrl);
            
        } catch (Exception e) {
            if (e.getMessage().contains("验证公众号配置失败") || 
                e.getMessage().contains("上传图片素材失败") || 
                e.getMessage().contains("准备图片文件失败") ||
                e.getMessage().contains("AppID") ||
                e.getMessage().contains("AppSecret") ||
                e.getMessage().contains("IP地址")) {
                throw e;
            }
            log.error("验证公众号配置异常: {}", e.getMessage());
            throw new Exception("验证公众号配置异常：" + e.getMessage());
        }
    }
    
    /**
     * 添加文章到公众号草稿箱（推荐使用，每次都重新获取token）
     * 
     * @param account 公众号配置（包含AES加密的appId和appSecret）
     * @param title 文章标题
     * @param author 文章作者
     * @param content 文章内容
     * @param thumbMediaId 封面图素材ID（必填，微信API要求）
     * @return 草稿media_id
     * @throws Exception 如果添加失败
     */
    public String addDraft(WcOfficeAccount account, String title, String author, String content, String thumbMediaId) throws Exception {
        // 每次都重新获取access_token，避免token过期问题
        String accessToken = getAccessToken(account);
        
        // 调用实际添加方法
        return addDraftInternal(accessToken, title, author, content, thumbMediaId);
    }
    
    /**
     * 添加文章到公众号草稿箱（内部方法）
     * 
     * @param accessToken 访问令牌
     * @param title 文章标题
     * @param author 文章作者
     * @param content 文章内容
     * @param thumbMediaId 封面图素材ID（必填，微信API要求）
     * @return 草稿media_id
     * @throws Exception 如果添加失败
     */
    private String addDraftInternal(String accessToken, String title, String author, String content, String thumbMediaId) throws Exception {
        String url = String.format(WECHAT_DRAFT_ADD_URL, accessToken);
        
        // 验证封面图素材ID必填
        if (thumbMediaId == null || thumbMediaId.trim().isEmpty()) {
            throw new Exception("封面图素材ID(thumb_media_id)为必填项，符合微信API要求");
        }
        
        // 构建文章数据（只包含必需字段，参考旧代码）
        Map<String, Object> article = new HashMap<>();
        article.put("title", title);
        article.put("author", author != null ? author : "");  // 作者
        
        // 微信API要求content必须是HTML格式
        // 参考旧代码：只发送title+author+content+thumb_media_id，不需要digest
        
        // 首先清理排版API可能返回的代码块标记
        String cleanedContent = content
            .replaceAll("^```html\\s*", "")    // 删除开头的```html
            .replaceAll("^```\\s*", "")         // 删除开头的```
            .replaceAll("\\s*```$", "")         // 删除结尾的```
            .trim();
        
        String htmlContent;
        if (cleanedContent.trim().startsWith("<")) {
            // 已经是HTML格式，只需清理换行符
            htmlContent = cleanedContent
                .replaceAll("\n\n+", "")  // 删除连续换行
                .replaceAll("\n", "");     // 删除单个换行
            
            log.debug("[内容处理] HTML格式，清理换行符 - 原始: {} → 处理后: {} 字符", content.length(), htmlContent.length());
        } else {
            // Markdown格式，需要转换为HTML
            htmlContent = convertMarkdownToHtml(cleanedContent);
            log.debug("[内容处理] Markdown转HTML - 原始: {} → 转换后: {} 字符", content.length(), htmlContent.length());
        }
        article.put("content", htmlContent);  // 内容（HTML格式）
        
        log.trace("[内容处理] 最终content(前300字符): {}", htmlContent.length() > 300 ? htmlContent.substring(0, 300) : htmlContent);
        
        article.put("thumb_media_id", thumbMediaId);  // 封面图素材ID，必填
        
        // 使用List而不是数组
        List<Map<String, Object>> articlesList = new ArrayList<>();
        articlesList.add(article);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("articles", articlesList);
        
        log.debug("添加文章到草稿箱 - 标题: {}, 封面ID: {}", title, thumbMediaId);
        
        try {
            // 使用FastJSON序列化请求体（与旧代码保持一致）
            String requestBodyJson = JSON.toJSONString(requestBody);
            
            log.debug("[草稿箱] 请求体长度: {} 字符, Content长度: {} 字符", requestBodyJson.length(), htmlContent.length());
            log.trace("[草稿箱] 完整请求: {}", requestBodyJson);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            
            // 直接发送JSON字符串，而不是Map对象（与旧代码RestUtils一致）
            HttpEntity<String> request = new HttpEntity<>(requestBodyJson, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            log.debug("[草稿箱] 微信API响应: {}", response.getBody());
            
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            
            if (jsonNode.has("media_id")) {
                String mediaId = jsonNode.get("media_id").asText();
                log.info("[草稿箱] 添加成功 - 标题: {}, MediaID: {}", title, mediaId);
                return mediaId;
            } else {
                String errcode = jsonNode.has("errcode") ? jsonNode.get("errcode").asText() : "unknown";
                String errmsg = jsonNode.has("errmsg") ? jsonNode.get("errmsg").asText() : "unknown error";
                log.error("[草稿箱] 添加失败 - 标题: {}, 错误码: {}, 错误信息: {}", title, errcode, errmsg);
                throw new Exception("微信API返回错误(" + errcode + "): " + errmsg + "，请检查文章内容");
            }
        } catch (Exception e) {
            if (e.getMessage().contains("微信API返回错误")) {
                throw e; // 直接抛出业务异常
            }
            // 区分HTTP异常和其他异常
            if (e instanceof org.springframework.web.client.HttpClientErrorException) {
                org.springframework.web.client.HttpClientErrorException httpError = 
                    (org.springframework.web.client.HttpClientErrorException) e;
                log.error("[草稿箱] HTTP请求失败 - 标题: {}, 状态码: {}, 错误: {}", 
                    title, httpError.getStatusCode(), httpError.getStatusText());
                throw new Exception("微信API HTTP错误(" + httpError.getStatusCode() + "): " + httpError.getStatusText());
            }
            log.error("[草稿箱] 网络请求异常 - 标题: {}, 错误: {}", title, e.getMessage());
            throw new Exception("添加草稿网络异常: " + e.getMessage());
        }
    }
    
    /**
     * 发布文章到公众号草稿箱（完整流程，每次都重新获取token）
     * 
     * @param account 公众号配置
     * @param title 文章标题
     * @param content 文章内容
     * @return 草稿media_id
     * @throws Exception 如果发布失败
     */
    public String publishToWechat(WcOfficeAccount account, String title, String content) throws Exception {
        
        // 直接调用addDraft重载方法，它会内部重新获取token
        String mediaId = addDraft(account, title, account.getAuthorName(), content, account.getMediaId());
        
        log.info("文章发布成功 - 标题: {}, 作者: {}, 草稿media_id: {}", title, account.getAuthorName(), mediaId);
        
        return mediaId;
    }
    
    /**
     * 将Markdown格式转换为HTML格式（简单实现）
     * 微信公众号草稿箱API要求content必须是HTML格式
     * 
     * @param markdown Markdown格式的内容
     * @return HTML格式的内容
     */
    private String convertMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        
        StringBuilder html = new StringBuilder();
        String[] lines = markdown.split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // 跳过空行
            if (line.isEmpty()) {
                continue;
            }
            
            // 转换标题（先判断再转义）
            if (line.startsWith("### ")) {
                String content = escapeHtml(line.substring(4));
                html.append("<h3>").append(content).append("</h3>");
            } else if (line.startsWith("## ")) {
                String content = escapeHtml(line.substring(3));
                html.append("<h2>").append(content).append("</h2>");
            } else if (line.startsWith("# ")) {
                String content = escapeHtml(line.substring(2));
                html.append("<h1>").append(content).append("</h1>");
            } else {
                // 普通段落：先转义，再处理粗体/斜体
                String processedLine = escapeHtml(line);
                processedLine = processedLine.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
                processedLine = processedLine.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
                
                html.append("<p>").append(processedLine).append("</p>");
            }
        }
        
        log.debug("Markdown转HTML完成，原始长度: {}, 转换后长度: {}", markdown.length(), html.length());
        
        return html.toString();
    }
    
    /**
     * 转义HTML标签之间的文本内容（保留HTML标签结构）
     * 用于处理已经是HTML格式的内容，转义其中的特殊字符
     * 同时清理微信API不支持的style/class/id属性
     */
    private String escapeHtmlTextContent(String html) {
        if (html == null || html.isEmpty()) return "";
        
        // 1. 清理HTML标签的style/class/id属性（微信API可能不支持）
        String cleaned = html
            .replaceAll("\\s+style=\"[^\"]*\"", "")     // 删除 style="..."
            .replaceAll("\\s+style='[^']*'", "")         // 删除 style='...'
            .replaceAll("\\s+class=\"[^\"]*\"", "")     // 删除 class="..."
            .replaceAll("\\s+class='[^']*'", "")         // 删除 class='...'
            .replaceAll("\\s+id=\"[^\"]*\"", "")         // 删除 id="..."
            .replaceAll("\\s+id='[^']*'", "");           // 删除 id='...'
        
        // 2. 修复排版API可能返回的错误格式
        cleaned = cleaned
            .replace("和号ldquo分号", "&ldquo;")
            .replace("和号rdquo分号", "&rdquo;")
            .replace("和号lsquo分号", "&lsquo;")
            .replace("和号rsquo分号", "&rsquo;")
            .replace("和号amp分号", "&amp;");
        
        // 3. 转义中文引号为HTML实体
        cleaned = cleaned
            .replace("\u201C", "&ldquo;")  // 中文左双引号 "
            .replace("\u201D", "&rdquo;")  // 中文右双引号 "
            .replace("\u2018", "&lsquo;")  // 中文左单引号 '
            .replace("\u2019", "&rsquo;"); // 中文右单引号 '
        
        return cleaned;
        // 注意：不转义&<>"等，因为HTML标签需要这些字符
    }
    
    /**
     * HTML实体转义（只转义必要字符，保留HTML标签）
     * 注意：不转义<>，因为需要保留<strong>等标签
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            // 先转义&（必须最先）
            .replace("&", "&amp;")
            // 转义英文引号
            .replace("\"", "&quot;")
            // 转义中文引号（使用Unicode转义避免编译错误）
            .replace("\u201C", "&ldquo;")  // 中文左双引号 "
            .replace("\u201D", "&rdquo;")  // 中文右双引号 "
            .replace("\u2018", "&lsquo;")  // 中文左单引号 '
            .replace("\u2019", "&rsquo;"); // 中文右单引号 '
            // 注意：不转义<>，因为我们的<strong>等标签需要保留
    }
}
