package com.wx.fbsir.business.documentparse.service.impl;

import com.wx.fbsir.business.dailyassistant.domain.YuanqiAgentConfig;
import com.wx.fbsir.business.dailyassistant.service.IYuanqiAgentConfigService;
import com.wx.fbsir.business.dailyassistant.service.impl.YuanqiAgentApiService;
import com.wx.fbsir.business.dailyassistant.service.impl.YuanqiAgentApiService.YuanqiApiResponse;
import com.wx.fbsir.business.documentparse.domain.DocumentParse;
import com.wx.fbsir.business.documentparse.mapper.DocumentParseMapper;
import com.wx.fbsir.business.documentparse.service.IDocumentParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文档解析Service业务层处理
 *
 * @author wxfbsir
 * @date 2025-12-26
 */
@Service
public class DocumentParseServiceImpl implements IDocumentParseService
{
    private static final Logger log = LoggerFactory.getLogger(DocumentParseServiceImpl.class);
    
    // 定时器：用于检查回调超时
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Autowired
    private DocumentParseMapper documentParseMapper;

    @Autowired
    private IYuanqiAgentConfigService yuanqiAgentConfigService;

    @Autowired
    private YuanqiAgentApiService yuanqiAgentApiService;

    /**
     * 查询文档解析
     *
     * @param id 文档解析ID
     * @return 文档解析
     */
    @Override
    public DocumentParse selectDocumentParseById(Long id)
    {
        return documentParseMapper.selectDocumentParseById(id);
    }

    /**
     * 查询文档解析列表
     *
     * @param documentParse 文档解析
     * @return 文档解析
     */
    @Override
    public List<DocumentParse> selectDocumentParseList(DocumentParse documentParse)
    {
        return documentParseMapper.selectDocumentParseList(documentParse);
    }

    /**
     * 根据用户ID查询文档解析列表
     *
     * @param userId 用户ID
     * @return 文档解析集合
     */
    @Override
    public List<DocumentParse> selectDocumentParseListByUserId(Long userId)
    {
        return documentParseMapper.selectDocumentParseListByUserId(userId);
    }

    /**
     * 新增文档解析
     *
     * @param documentParse 文档解析
     * @return 结果
     */
    @Override
    public int insertDocumentParse(DocumentParse documentParse)
    {
        return documentParseMapper.insertDocumentParse(documentParse);
    }

    /**
     * 修改文档解析
     *
     * @param documentParse 文档解析
     * @return 结果
     */
    @Override
    public int updateDocumentParse(DocumentParse documentParse)
    {
        return documentParseMapper.updateDocumentParse(documentParse);
    }

    /**
     * 批量删除文档解析
     *
     * @param ids 需要删除的文档解析ID
     * @return 结果
     */
    @Override
    public int deleteDocumentParseByIds(Long[] ids)
    {
        return documentParseMapper.deleteDocumentParseByIds(ids);
    }

    /**
     * 删除文档解析信息
     *
     * @param id 文档解析ID
     * @return 结果
     */
    @Override
    public int deleteDocumentParseById(Long id)
    {
        return documentParseMapper.deleteDocumentParseById(id);
    }

    /**
     * 创建文档解析记录（仅创建记录，不触发解析）
     * 
     * 注意：解析任务需要在Controller层调用 triggerDocumentParse 方法触发
     *      这样可以确保异步执行（避免同类调用导致@Async失效）
     */
    @Override
    public DocumentParse createDocumentParseAndProcess(Long userId, String documentId, String documentName, String prompt, String base64Content)
    {
        // 1. 快速创建新文档解析记录
        DocumentParse documentParse = new DocumentParse();
        documentParse.setUserId(userId);
        documentParse.setDocumentId(documentId);
        documentParse.setDocumentName(documentName);
        documentParse.setPrompt(prompt);
        documentParse.setProcessStatus(0); // 处理中

        // 2. 立即保存到数据库
        documentParseMapper.insertDocumentParse(documentParse);
        
        log.info("文档解析记录创建成功 - 记录ID: {}, 文档ID: {}, 等待Controller触发异步解析", documentParse.getId(), documentId);

        // 3. 返回记录（Controller层会调用异步方法）
        return documentParse;
    }
    
    /**
     * 异步处理文档解析
     * 该方法在后台线程池中执行，不会阻塞主线程
     * 
     * 重要：此方法必须由外部类调用才能确保@Async生效
     *      如果在同一个类内部调用（this.method()），Spring AOP代理不会介入，会导致同步执行
     * 
     * @param documentParseId 文档解析ID
     * @param userId 用户ID
     * @param documentId 文档ID
     * @param prompt 提示词
     * @param url 文件访问URL（已替换域名）
     * @param originalFilename 原始文件名
     */
    @Async("asyncTaskExecutor")
    @Override
    public void triggerDocumentParse(Long documentParseId, Long userId, String documentId, String prompt, String url, String originalFilename)
    {
        log.info("[异步任务] 开始处理文档解析 - 记录ID: {}, 文档ID: {}", documentParseId, documentId);
        
        try {
            // 获取用户的腾讯元器智能体配置（解密后的真实配置）
            YuanqiAgentConfig config = yuanqiAgentConfigService.selectActiveConfigByUserIdDecrypted(userId, "document_parse");
            
            if (config == null) {
                log.error("[异步任务] 未找到智能体配置 - 记录ID: {}", documentParseId);
                updateDocumentParseStatus(documentParseId, 2, "未找到启用的腾讯元器智能体配置", null);
                return;
            }

            // 调用腾讯元器智能体API（工作流）
            String appKey = config.getApiKey();     // API密钥（appkey）
            String appId = config.getAgentId();     // 智能体ID（appid）
            String userIdStr = String.valueOf(userId);
            
            log.info("[异步任务] 开始解析文档 - 记录ID: {}, 文档ID: {}", documentParseId, documentId);
            
            // 调用API服务（异步工作流，只提交任务，不等待内容）
            YuanqiApiResponse response = yuanqiAgentApiService.parseDocument(
                appKey, 
                appId, 
                userIdStr, 
                documentId,
                prompt,
                url,
                originalFilename
            );
            
            if (response.isSuccess()) {
                // 工作流任务已提交成功，记录taskId
                // 注意：内容不从这里获取，而是通过工作流的HTTP回调接口传递
                //      - /updateParsedContent 保存解析后的内容
                
                // 检查记录是否已经通过回调完成（避免竞态条件）
                DocumentParse currentDocumentParse = documentParseMapper.selectDocumentParseById(documentParseId);
                if (currentDocumentParse != null && currentDocumentParse.getProcessStatus() == 1) {
                    // 记录已经通过回调完成，不再更新状态
                    log.info("[异步任务] 工作流任务提交成功，但记录已通过回调完成 - 记录ID: {}, TaskId: {}, 跳过状态更新", 
                            documentParseId, response.getTaskId());
                } else {
                    // 记录还未完成，更新taskId并保持处理中状态
                    updateDocumentParseStatus(documentParseId, 0, null, response.getTaskId());  // status仍为0（处理中）
                    
                    log.info("[异步任务] 工作流任务提交成功 - 记录ID: {}, TaskId: {}, 等待回调传递结果", 
                            documentParseId, response.getTaskId());
                    
                    // 启动5分钟超时检查：如果5分钟后还没收到回调，设为失败
                    scheduleCallbackTimeoutCheck(documentParseId, documentId);
                }
            } else {
                // 任务提交失败
                updateDocumentParseStatus(documentParseId, 2, response.getErrorMessage(), null);
                
                log.error("[异步任务] 工作流任务提交失败 - 记录ID: {}, 错误: {}", documentParseId, response.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("[异步任务] 调用腾讯元器智能体异常 - 记录ID: {}", documentParseId, e);
            updateDocumentParseStatus(documentParseId, 2, "调用智能体异常: " + e.getMessage(), null);
        }
    }
    
    /**
     * 更新文档解析状态
     */
    private void updateDocumentParseStatus(Long documentParseId, int status, String errorMessage, String taskId)
    {
        DocumentParse documentParse = new DocumentParse();
        documentParse.setId(documentParseId);
        documentParse.setProcessStatus(status);
        
        if (errorMessage != null) {
            documentParse.setErrorMessage(errorMessage);
        }
        
        if (taskId != null) {
            documentParse.setAgentTaskId(taskId);
        }
        
        documentParseMapper.updateDocumentParse(documentParse);
    }

    /**
     * 更新解析后的内容
     * 注意：此方法会同时将记录状态更新为1（已完成）
     *
     * @param documentParseId 文档解析ID
     * @param parsedContent 解析后的内容
     * @return 结果
     */
    @Override
    public int updateParsedContent(Long documentParseId, String parsedContent)
    {
        // updateParsedContent 的 SQL 会同时将 process_status 设置为 1
        return documentParseMapper.updateParsedContent(documentParseId, parsedContent);
    }

    /**
     * 更新文档解析处理状态
     *
     * @param documentParseId 文档解析ID
     * @param processStatus 处理状态
     * @return 结果
     */
    @Override
    public int updateProcessStatus(Long documentParseId, Integer processStatus)
    {
        return documentParseMapper.updateProcessStatus(documentParseId, processStatus);
    }

    /**
     * 启动回调超时检查
     * 如果5分钟后记录仍然是"处理中"状态，说明没有收到回调，将状态设为失败
     * 
     * @param documentParseId 文档解析ID
     * @param documentId 文档ID
     */
    private void scheduleCallbackTimeoutCheck(Long documentParseId, String documentId) {
        scheduler.schedule(() -> {
            try {
                // 5分钟后检查记录状态
                DocumentParse documentParse = documentParseMapper.selectDocumentParseById(documentParseId);
                
                if (documentParse != null && documentParse.getProcessStatus() == 0) {
                    // 仍然是处理中状态，说明没有收到回调
                    log.warn("[回调超时] 文档解析\"{}\"(ID:{})已超过5分钟未收到回调，设置为失败状态", 
                            documentId, documentParseId);
                    
                    updateDocumentParseStatus(documentParseId, 2, 
                            "工作流回调超时：已等待5分钟仍未收到解析结果，请检查腾讯元器工作流配置或重试", 
                            null);
                } else if (documentParse != null) {
                    log.info("[回调检查] 文档解析\"{}\"(ID:{})状态正常 - processStatus: {}", 
                            documentId, documentParseId, documentParse.getProcessStatus());
                }
            } catch (Exception e) {
                log.error("[回调超时检查] 检查文档解析状态异常 - documentParseId: {}", documentParseId, e);
            }
        }, 5, TimeUnit.MINUTES);  // 5分钟后执行
        
        log.info("[回调超时检查] 已为文档解析\"{}\"(ID:{})启动5分钟超时检查", documentId, documentParseId);
    }
}

