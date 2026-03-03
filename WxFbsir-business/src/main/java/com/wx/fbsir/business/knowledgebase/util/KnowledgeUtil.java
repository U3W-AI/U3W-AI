package com.wx.fbsir.business.knowledgebase.util;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.wx.fbsir.business.knowledgebase.domain.KnowledgeBaseInfo;
import com.wx.fbsir.business.knowledgebase.enums.UploadType;
import com.wx.fbsir.business.websocket.message.EngineMessage;
import com.wx.fbsir.business.websocket.server.EngineSessionManager;
import com.wx.fbsir.common.config.WxFbsirConfig;
import com.wx.fbsir.common.utils.SecurityUtils;
import com.wx.fbsir.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class KnowledgeUtil {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeUtil.class);
    @Autowired
    private EngineSessionManager engineSessionManager;

    /**
     * 将知识库内容保存为PDF文件并上传，返回文件URL
     *
     * @param kb 知识库信息
     * @return PDF文件URL（可直接下载）
     */
    public String saveKnowledgeContentAndGetUrl(KnowledgeBaseInfo kb) throws IOException {
        // 将知识库内容保存为PDF文件并上传到服务器
        String content = kb.getKbContent();
        if (StringUtils.isEmpty(content)) {
            throw new IOException("知识库内容为空");
        }

        // 清理知识库名称，移除特殊字符（只保留中文、英文、数字、下划线、短横线）
        String cleanKbName = kb.getKbName().replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9_-]", "_");

        // 生成文件名：知识库名称_kbId.pdf
        String fileName = cleanKbName + "_" + kb.getKbId() + ".pdf";

        // 生成上传目录（按日期分类）
        String datePath = com.wx.fbsir.common.utils.DateUtils.datePath();
        String uploadDir = WxFbsirConfig.getProfile() + "/" + datePath;

        // 创建目录
        java.io.File dir = new java.io.File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 生成唯一文件名（避免重复）
        String uniqueFileName = java.util.UUID.randomUUID().toString().replace("-", "") + "_" + fileName;
        String filePath = uploadDir + "/" + uniqueFileName;

        // 生成PDF文件
        PdfWriter writer = null;
        PdfDocument pdfDoc = null;
        Document document = null;

        try {
            // 创建PDF文档
            writer = new PdfWriter(filePath);
            pdfDoc = new PdfDocument(writer);
            document = new Document(pdfDoc);

            // 设置中文字体（使用iText内置的中文字体）
            PdfFont font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");

            // 添加标题
            Paragraph title = new Paragraph(kb.getKbName())
                    .setFont(font)
                    .setFontSize(18)
                    .setBold();
            document.add(title);

            // 添加空行
            document.add(new Paragraph("\n"));

            // 添加内容（按行分割，避免单个段落过长）
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (StringUtils.isNotEmpty(line.trim())) {
                    Paragraph paragraph = new Paragraph(line)
                            .setFont(font)
                            .setFontSize(12);
                    document.add(paragraph);
                } else {
                    // 空行
                    document.add(new Paragraph("\n"));
                }
            }

            log.info("[知识库PDF生成] PDF内容已添加 - 知识库: {}, 行数: {}", kb.getKbName(), lines.length);

        } catch (Exception e) {
            log.error("[知识库PDF生成] 生成PDF失败 - 知识库: {}", kb.getKbName(), e);
            throw new IOException("生成PDF文件失败: " + e.getMessage(), e);
        } finally {
            // 确保资源正确关闭
            try {
                if (document != null) {
                    document.close();
                }
                if (pdfDoc != null) {
                    pdfDoc.close();
                }
                if (writer != null) {
                    writer.close();
                }
            } catch (Exception e) {
                log.warn("[知识库PDF生成] 关闭PDF资源时出错: {}", e.getMessage());
            }
        }

        // 验证PDF文件
        java.io.File pdfFile = new java.io.File(filePath);
        if (!pdfFile.exists()) {
            throw new IOException("PDF文件生成失败：文件不存在");
        }

        long fileSize = pdfFile.length();
        if (fileSize == 0) {
            throw new IOException("PDF文件生成失败：文件大小为0");
        }

        log.info("[知识库PDF生成] 成功生成PDF - 知识库: {}, 文件: {}, 大小: {} bytes",
                kb.getKbName(), uniqueFileName, fileSize);

        // 生成完整的文件访问URL
        String domain = WxFbsirConfig.getDomain();
        if (StringUtils.isEmpty(domain)) {
            throw new IOException("域名配置为空，请在application.yml中配置wxfbsir.domain");
        }
        // 确保域名不以/结尾
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }

        // 返回文件URL（格式：http://domain/profile/upload/2026/03/02/xxx.pdf）
        String fileUrl = domain + com.wx.fbsir.common.constant.Constants.RESOURCE_PREFIX + "/" + datePath + "/" + uniqueFileName;
        log.info("[知识库PDF生成] 文件已上传 - URL: {}", fileUrl);

        return fileUrl;
    }

    /**
     * 发送知识库URL到对应的Engine（元器 / 企业微信机器人 / 两者）
     *
     * @param kb           知识库信息
     * @param importWebUrl 知识库内容文件URL
     * @param uploadType   上传类型
     * @param agentName    智能体名称（上传类型为YUANQI或BOTH时必填）
     * @param robotName    机器人名称（上传类型为WECHAT_BOT或BOTH时必填）
     * @param teamName     团队名称（上传类型为YUANQI或BOTH时可选，默认"个人空间"）
     */
    public boolean sendKnowledgeToEngines(KnowledgeBaseInfo kb, String importWebUrl, UploadType uploadType, String agentName, String robotName, String teamName) {
        //获取用户id
        Long userId = SecurityUtils.getUserId();
        String userIdStr = userId != null ? String.valueOf(userId) : null;

        //用户ID为空时直接返回失败，避免锁异常
        if (StringUtils.isEmpty(userIdStr)) {
            log.error("用户ID为空，无法执行知识库上传");
            return false;
        }

        // 优先使用当前用户配置的hostId作为engineId
        String engineId = resolveEngineId();

        boolean success = true;

        if (uploadType == UploadType.YUANQI || uploadType == UploadType.BOTH) {
            if (StringUtils.isEmpty(agentName)) {
                log.error("上传到智能体元器时，智能体名称不能为空");
                return false;
            }

            // 如果未提供团队名称，默认"个人空间"
            String finalTeamName = StringUtils.isNotEmpty(teamName) ? teamName : "个人空间";

            EngineMessage msg = EngineMessage.builder()
                    .type("YUANQI_SET_KNOWLEDGE")
                    .engineId(engineId)
                    .userId(userIdStr)
                    .payload("requestId", "kb-" + kb.getKbId() + "-" + System.currentTimeMillis())
                    .payload("knowledgeBaseName", kb.getKbName())
                    .payload("importWebUrl", importWebUrl)
                    .payload("agentName", agentName)
                    .payload("teamName", finalTeamName)
                    .build();

            boolean sent = engineSessionManager.sendMessage(engineId, msg);
            if (!sent) {
                log.error("发送元器知识库配置任务失败，engineId: {}, agentName: {}", engineId, agentName);
                success = false;
            }
        }
        if (uploadType == UploadType.WECHAT_BOT || uploadType == UploadType.BOTH) {
            if (StringUtils.isEmpty(robotName)) {
                log.error("上传到企业微信机器人时，机器人名称不能为空");
                return false;
            }

            EngineMessage msg = EngineMessage.builder()
                    .type("ROBOT_SET_KNOWLEDGE")
                    .engineId(engineId)
                    .userId(userIdStr)
                    .payload("requestId", "kb-" + kb.getKbId() + "-" + System.currentTimeMillis())
                    .payload("importWebUrl", importWebUrl)
                    .payload("robotName", robotName)
                    .build();

            boolean sent = engineSessionManager.sendMessage(engineId, msg);
            if (!sent) {
                log.error("发送企业微信机器人知识库配置任务失败，engineId: {}, robotName: {}", engineId, robotName);
                success = false;
            }
        }

        return success;


    }

    /**
     * 发送文档URL到对应的Engine（元器 / 企业微信机器人 / 两者）
     *
     * @param fileUrl    文档文件URL
     * @param uploadType 上传类型
     * @param agentName  智能体名称（上传类型为YUANQI或BOTH时必填）
     * @param robotName  机器人名称（上传类型为WECHAT_BOT或BOTH时必填）
     * @param kbName     知识库名称（可选，如果不提供则使用"本地文档"）
     * @param teamName   团队名称（上传类型为YUANQI或BOTH时可选，默认"个人空间"）
     */
    public boolean sendDocumentToEngines(String fileUrl, UploadType uploadType, String agentName, String robotName, String kbName, String teamName) {
        Long userId = SecurityUtils.getUserId();
        String userIdStr = userId != null ? String.valueOf(userId) : null;

        // 优先使用当前用户配置的hostId作为engineId
        String engineId = resolveEngineId();

        boolean success = true;

        // 如果没有提供知识库名称，使用默认值
        String knowledgeBaseName = StringUtils.isNotEmpty(kbName) ? kbName : "本地文档";

        if (uploadType == UploadType.YUANQI || uploadType == UploadType.BOTH) {
            if (StringUtils.isEmpty(agentName)) {
                log.error("上传到智能体元器时，智能体名称不能为空");
                return false;
            }

            // 如果未提供团队名称，默认"个人空间"
            String finalTeamName = StringUtils.isNotEmpty(teamName) ? teamName : "个人空间";

            EngineMessage msg = EngineMessage.builder()
                    .type("YUANQI_SET_KNOWLEDGE")
                    .engineId(engineId)
                    .userId(userIdStr)
                    .payload("requestId", "file-" + System.currentTimeMillis())
                    .payload("knowledgeBaseName", knowledgeBaseName)
                    .payload("importWebUrl", fileUrl)
                    .payload("agentName", agentName)
                    .payload("teamName", finalTeamName)
                    .build();

            boolean sent = engineSessionManager.sendMessage(engineId, msg);
            if (!sent) {
                log.error("发送元器文档上传任务失败，engineId: {}, agentName: {}", engineId, agentName);
                success = false;
            }
        }

        if (uploadType == UploadType.WECHAT_BOT || uploadType == UploadType.BOTH) {
            if (StringUtils.isEmpty(robotName)) {
                log.error("上传到企业微信机器人时，机器人名称不能为空");
                return false;
            }

            EngineMessage msg = EngineMessage.builder()
                    .type("ROBOT_SET_KNOWLEDGE")
                    .engineId(engineId)
                    .userId(userIdStr)
                    .payload("requestId", "file-" + System.currentTimeMillis())
                    .payload("importWebUrl", fileUrl)
                    .payload("robotName", robotName)
                    .build();

            boolean sent = engineSessionManager.sendMessage(engineId, msg);
            if (!sent) {
                log.error("发送企业微信机器人文档上传任务失败，engineId: {}, robotName: {}", engineId, robotName);
                success = false;
            }
        }

        return success;
    }

    /**
     * 解析当前用户应使用的engineId
     * 优先级：用户配置的hostId（且在线） > 第一个在线Engine
     */
    private String resolveEngineId() {
        // 优先使用用户配置的hostId
        try {
            String userHostId = SecurityUtils.getLoginUser().getUser().getHostId();
            if (StringUtils.isNotEmpty(userHostId)) {
                java.util.List<String> onlineIds = engineSessionManager.getOnlineEngineIds();
                if (onlineIds != null && onlineIds.contains(userHostId)) {
                    return userHostId;
                }
                log.warn("用户配置的主机ID [{}] 不在线，尝试使用其他在线Engine", userHostId);
            }
        } catch (Exception e) {
            log.warn("获取用户hostId失败: {}", e.getMessage());
        }

        // 降级：使用第一个在线Engine
        java.util.List<String> engineIds = engineSessionManager.getOnlineEngineIds();
        if (engineIds == null || engineIds.isEmpty()) {
            log.error("无可用Engine在线");
            return null;
        }
        return engineIds.get(0);
    }

    /**
     * 判断某个知识库ID是否包含在"拥有的知识库"列表中
     */
    public boolean isKbOwnedByUser(String hasKnowledgeBase, Long kbId) {
        if (kbId == null || StringUtils.isEmpty(hasKnowledgeBase)) {
            return false;
        }
        String[] parts = hasKnowledgeBase.split(",");
        for (String part : parts) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            try {
                if (kbId.equals(Long.valueOf(part.trim()))) {
                    return true;
                }
            } catch (NumberFormatException ignore) {
                // 忽略非法ID
            }
        }
        return false;
    }

    /**
     * 向逗号分隔的ID列表中追加一个ID（避免重复）
     */
    public String appendIdToList(String idList, Long id) {
        if (id == null) {
            return idList;
        }
        if (StringUtils.isEmpty(idList)) {
            return String.valueOf(id);
        }
        if (isKbOwnedByUser(idList, id)) {
            return idList;
        }
        return idList + "," + id;
    }
}
