package com.wx.fbsir.business.knowledgebase.util;

import com.wx.fbsir.business.knowledgebase.domain.KnowledgeBaseInfo;
import com.wx.fbsir.business.knowledgebase.enums.UploadType;
import com.wx.fbsir.business.websocket.message.EngineMessage;
import com.wx.fbsir.business.websocket.server.EngineSessionManager;
import com.wx.fbsir.common.config.WxFbsirConfig;
import com.wx.fbsir.common.utils.SecurityUtils;
import com.wx.fbsir.common.utils.StringUtils;
import com.wx.fbsir.common.utils.file.FileUploadUtils;
import com.wx.fbsir.common.utils.file.MimeTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@Component
public class KnowledgeUtil {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeUtil.class);
    @Autowired
    private EngineSessionManager engineSessionManager;

    /**
     * 将知识库内容写入文件并上传到服务器，返回可访问的URL
     */
    public String saveKnowledgeContentAndGetUrl(KnowledgeBaseInfo kb) throws IOException {
        String content = kb.getKbContent();
        if (content == null) {
            content = "";
        }
        // 将内容转换为UTF-8编码的字节数组，用于文件写入
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // 构建文件原始名称：优先使用知识库名称，无则用默认值 + 知识库ID + .txt后缀
        String originalFilename = (kb.getKbName() != null ? kb.getKbName() : "knowledgebase")
                + "_" + kb.getKbId() + ".txt";
        // 匿名实现MultipartFile接口，封装知识库内容为文件对象（无需实际文件，基于内存字节数组）
        MultipartFile file = new MultipartFile() {
            /**
             * 获取文件参数名
             * @return 拼接了知识库ID的唯一参数名
             */
            @Override
            public String getName() {
                return "kb-" + kb.getKbId();
            }

            /**
             * 获取文件原始名称（带后缀）
             * @return 前面构建的包含知识库名称/ID的文件名
             */
            @Override
            public String getOriginalFilename() {
                return originalFilename;
            }

            /**
             * 获取文件MIME类型
             * @return text文件对应的MIME类型
             */
            @Override
            public String getContentType() {
                return "text/plain";
            }

            /**
             * 判断文件是否为空（即内容字节数组长度是否为0）
             * @return 空返回true，非空返回false
             */
            @Override
            public boolean isEmpty() {
                return bytes.length == 0;
            }

            /**
             * 获取文件大小（字节数）
             * @return 内容字节数组的长度
             */
            @Override
            public long getSize() {
                return bytes.length;
            }

            @Override
            public byte[] getBytes() throws IOException {
                return bytes;
            }

            /**
             * 获取文件输入流
             * @return 基于字节数组构建的输入流，用于文件上传处理
             * @throws IOException 此处无实际IO异常，仅遵循接口定义
             */
            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(bytes);
            }

            /**
             * 将文件内容写入指定的目标文件
             * @param dest 目标文件对象
             * @throws IOException 写入文件时出现IO异常时抛出
             * @throws IllegalStateException 状态异常时抛出
             */
            @Override
            public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                java.nio.file.Files.write(dest.toPath(), bytes);
            }
        };
        // 获取文件上传的基础目录（从配置类中读取）
        String baseDir = WxFbsirConfig.getUploadPath();
        try {
            // 调用文件上传工具类，上传文件并返回完整访问URL
            return FileUploadUtils.uploadAndGetFullUrl(baseDir, file,
                    MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
        } catch (Exception e) {
            throw new IOException("上传知识库内容文件失败", e);
        }
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


        java.util.List<String> engineIds = engineSessionManager.getOnlineEngineIds();
        if (engineIds == null || engineIds.isEmpty()) {
            log.error("无可用Engine在线，无法执行知识库上传");
            return false;
        }
        String engineId = engineIds.get(0);

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

        java.util.List<String> engineIds = engineSessionManager.getOnlineEngineIds();
        if (engineIds == null || engineIds.isEmpty()) {
            log.error("无可用Engine在线，无法执行文档上传");
            return false;
        }
        String engineId = engineIds.get(0);

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
     * 判断某个知识库ID是否包含在“拥有的知识库”列表中
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
