package com.wx.fbsir.business.documentparse.controller;

import com.wx.fbsir.business.documentparse.domain.DocumentParse;
import com.wx.fbsir.business.documentparse.service.IDocumentParseService;
import com.wx.fbsir.common.annotation.Anonymous;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.common.utils.poi.ExcelUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档解析Controller
 *
 * @author wxfbsir
 * @date 2025-12-26
 */
@RestController
@RequestMapping("/system/document-parse")
public class DocumentParseController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(DocumentParseController.class);

    @Autowired
    private IDocumentParseService documentParseService;

    /**
     * 查询文档解析列表
     */
    @PreAuthorize("@ss.hasPermi('business:document:query')")
    @GetMapping("/list")
    public TableDataInfo list(DocumentParse documentParse)
    {
        startPage();
        List<DocumentParse> list = documentParseService.selectDocumentParseList(documentParse);
        return getDataTable(list);
    }

    /**
     * 查询当前用户的文档解析列表（支持分页）
     */
    @PreAuthorize("@ss.hasPermi('business:document:query')")
    @GetMapping("/myList")
    public TableDataInfo myList(DocumentParse documentParse)
    {
        Long userId = getUserId();
        documentParse.setUserId(userId);
        startPage();
        List<DocumentParse> list = documentParseService.selectDocumentParseList(documentParse);
        return getDataTable(list);
    }

    /**
     * 导出文档解析列表
     */
    @Log(title = "文档解析", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, DocumentParse documentParse)
    {
        List<DocumentParse> list = documentParseService.selectDocumentParseList(documentParse);
        ExcelUtil<DocumentParse> util = new ExcelUtil<DocumentParse>(DocumentParse.class);
        util.exportExcel(response, list, "文档解析数据");
    }

    /**
     * 获取文档解析详细信息
     */
    @PreAuthorize("@ss.hasPermi('business:document:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(documentParseService.selectDocumentParseById(id));
    }

    /**
     * 查询文档解析处理状态（用于前端轮询）
     * 返回轻量级状态信息，不包含大字段内容
     */
    @GetMapping("/status/{id}")
    public AjaxResult getDocumentParseStatus(@PathVariable("id") Long id)
    {
        DocumentParse documentParse = documentParseService.selectDocumentParseById(id);
        if (documentParse == null) {
            return error("文档解析记录不存在");
        }
        
        // 只返回状态相关字段，减少数据传输
        Map<String, Object> status = new HashMap<>();
        status.put("id", documentParse.getId());
        status.put("documentId", documentParse.getDocumentId());
        status.put("documentName", documentParse.getDocumentName());
        status.put("processStatus", documentParse.getProcessStatus());  // 0-处理中, 1-成功, 2-失败
        status.put("errorMessage", documentParse.getErrorMessage());
        status.put("agentTaskId", documentParse.getAgentTaskId());
        
        // 只有成功时才返回内容长度信息
        if (documentParse.getProcessStatus() == 1) {
            status.put("hasParsedContent", documentParse.getParsedContent() != null);
        }
        
        return success(status);
    }

    /**
     * 新增文档解析
     */
    @PreAuthorize("@ss.hasPermi('business:document:add')")
    @Log(title = "文档解析", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody DocumentParse documentParse)
    {
        documentParse.setCreateBy(getUsername());
        return toAjax(documentParseService.insertDocumentParse(documentParse));
    }

    /**
     * 上传文档并调用智能体解析（异步处理）
     * 
     * 新流程：
     * 1. 后端接收文件，调用内部 /upload 接口完成文件存储
     * 2. 从响应中提取 url 和 originalFilename
     * 3. 对 url 进行域名替换：localhost:8080 → wxfbdemo.free.idcfengye.com
     * 4. 构造新参数：{documentId, prompt, url, originalFilename}
     * 5. 调用腾讯元器工作流
     * 
     * 返回后文档解析处理状态为 0（处理中），前端需要轮询 /status/{id} 接口查询处理结果
     */
    @PreAuthorize("@ss.hasPermi('business:document:add')")
    @Log(title = "文档解析-上传并解析", businessType = BusinessType.INSERT)
    @PostMapping("/uploadAndParse")
    public AjaxResult uploadAndParse(
            @RequestParam("file") MultipartFile file,
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "documentId", required = false) String documentId,
            @RequestParam(value = "documentName", required = false) String documentName,
            HttpServletRequest request)
    {
        try {
            // 1. 参数校验
            if (file == null || file.isEmpty()) {
                return error("文档文件不能为空");
            }
            
            if (prompt == null || prompt.trim().isEmpty()) {
                return error("提示词不能为空");
            }

            // 2. 生成文档ID（如果未提供）
            if (documentId == null || documentId.trim().isEmpty()) {
                documentId = "DOC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            }

            // 3. 获取文档名称（如果未提供，使用文件名）
            if (documentName == null || documentName.trim().isEmpty()) {
                documentName = file.getOriginalFilename();
                if (documentName == null || documentName.trim().isEmpty()) {
                    documentName = "未命名文档";
                }
            }

            // 4. 调用内部 /upload 接口上传文件
            RestTemplate restTemplate = new RestTemplate();
            String uploadUrl = "http://localhost:8080/common/upload";
            
            HttpHeaders uploadHeaders = new HttpHeaders();
            uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            // 获取当前请求的 Authorization 头并传递给内部调用
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader != null && !authorizationHeader.isEmpty()) {
                uploadHeaders.set("Authorization", authorizationHeader);
            }
            
            org.springframework.util.LinkedMultiValueMap<String, Object> uploadBody = new org.springframework.util.LinkedMultiValueMap<>();
            uploadBody.add("file", file.getResource());
            
            HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> uploadRequest = new HttpEntity<>(uploadBody, uploadHeaders);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResponse = restTemplate.postForObject(uploadUrl, uploadRequest, Map.class);
            
            if (uploadResponse == null || 
                uploadResponse.get("code") == null || 
                !uploadResponse.get("code").equals(200)) {
                return error("文件上传失败");
            }
            
            // 5. 从上传响应中提取 url 和 originalFilename
            String url = (String) uploadResponse.get("url");
            String originalFilename = (String) uploadResponse.get("originalFilename");
            
            if (url == null || url.isEmpty()) {
                return error("上传响应中缺少 url 字段");
            }
            
            // 5. 对 url 进行域名替换：localhost:8080 → 内网穿透域名
            String replacedUrl = url.replace("http://localhost:8080", "http://内网穿透域名");
            
            log.info("文件上传成功 - 原始URL: {}, 替换后URL: {}, 原始文件名: {}", url, replacedUrl, originalFilename);

            // 6. 获取用户ID
            Long userId = getUserId();

            // 7. 快速创建文档解析记录（同步，< 100ms）
            DocumentParse documentParse = documentParseService.createDocumentParseAndProcess(
                    userId, documentId, documentName, prompt, null);
            
            // 8. 触发异步解析任务（从Controller调用确保@Async生效）
            // 传递新参数：documentId, prompt, url, originalFilename
            documentParseService.triggerDocumentParse(
                    documentParse.getId(), userId, documentId, prompt, replacedUrl, originalFilename);

            // 9. 立即返回（不等待AI解析结果）
            Map<String, Object> result = new HashMap<>();
            result.put("id", documentParse.getId());
            result.put("documentId", documentParse.getDocumentId());
            result.put("documentName", documentParse.getDocumentName());
            result.put("processStatus", documentParse.getProcessStatus());
            result.put("message", "文档上传成功，AI解析正在进行中，请稍后查询处理状态");

            return success(result);
            
        } catch (Exception e) {
            log.error("上传文档并解析失败", e);
            return error("上传文档并解析失败: " + e.getMessage());
        }
    }

    /**
     * 更新解析后的内容
     * 注意：此接口供工作流回调使用，无需用户认证，因此不记录操作日志
     */
    @Anonymous
    @PostMapping("/updateParsedContent")
    public AjaxResult updateParsedContent(@RequestBody Map<String, Object> params)
    {
        try {
            // 参数校验
            if (params.get("documentId") == null) {
                return error("缺少参数: documentId");
            }
            if (params.get("parsedContent") == null) {
                return error("缺少参数: parsedContent");
            }
            
            String documentId = params.get("documentId").toString();
            String parsedContent = params.get("parsedContent").toString();

            // 根据documentId查找记录
            DocumentParse queryParam = new DocumentParse();
            queryParam.setDocumentId(documentId);
            List<DocumentParse> list = documentParseService.selectDocumentParseList(queryParam);
            DocumentParse documentParse = list != null && !list.isEmpty() ? list.get(0) : null;
            
            if (documentParse == null) {
                return error("未找到文档解析记录，documentId: " + documentId);
            }

            log.info("保存解析内容 - 记录ID: {}, 文档ID: {}", documentParse.getId(), documentId);

            int result = documentParseService.updateParsedContent(documentParse.getId(), parsedContent);
            
            // 显式更新状态为已完成
            if (result > 0) {
                documentParseService.updateProcessStatus(documentParse.getId(), 1);
                log.info("文档解析ID: {} 已完成解析", documentParse.getId());
            }

            return toAjax(result);
            
        } catch (Exception e) {
            log.error("更新解析内容异常", e);
            return error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 修改文档解析
     */
    @Log(title = "文档解析", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody DocumentParse documentParse)
    {
        documentParse.setUpdateBy(getUsername());
        return toAjax(documentParseService.updateDocumentParse(documentParse));
    }

    /**
     * 删除文档解析
     */
    @PreAuthorize("@ss.hasPermi('business:document:remove')")
    @Log(title = "文档解析", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(documentParseService.deleteDocumentParseByIds(ids));
    }
}

