package com.wx.fbsir.business.resume.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.documentparse.controller.DocumentParseController;
import com.wx.fbsir.business.resume.domain.ParseResult;
import com.wx.fbsir.business.resume.service.ResumeManagementService;
import com.wx.fbsir.common.annotation.Anonymous;
import com.wx.fbsir.common.config.WxFbsirConfig;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.utils.file.FileUploadUtils;
import com.wx.fbsir.common.utils.file.MimeTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.UUID;

import com.wx.fbsir.common.core.controller.BaseController;


@RestController
@RequestMapping("/resume")
public class ResumeManagementController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseController.class);

    @Autowired
    private ResumeManagementService resumeManagementService;

    /**
     * 查询简历是否上传
     * @return
     */
    @GetMapping("/exists")
    public AjaxResult hasResume(){
        Map<String,Object> map = resumeManagementService.hasResume(getUserId());
        return success(map);
    }

    /**
     * 上传简历
     * @param file
     * @return
     */
    @PostMapping("/upload")
    public AjaxResult uploadResume(@RequestParam("file") MultipartFile file){
        try {
            if (file == null || file.isEmpty()){
                return error("文档文件不能为空");
            }
            String resumeId = "CV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            String resumeName = file.getOriginalFilename();

            if (resumeName == null || resumeName.trim().isEmpty()) {
                resumeName = "未命名文档";
            }
            //上传简历并获取简历存储路径
            String filePath = WxFbsirConfig.getUploadPath();
            String url = FileUploadUtils.uploadAndGetFullUrl(filePath,file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
            //存储简历
            resumeManagementService.storageResume(resumeId,resumeName,url,getUserId());
            return success();
        } catch (Exception e){
            log.info("{}",e);
            return error();
        }

    }

    /**
     * 更新简历
     * @param file
     * @return
     */
    @PostMapping("/updata")
    public AjaxResult updataResume(@RequestParam("file") MultipartFile file){
        try {
            if (file == null || file.isEmpty()){
                return error("文档文件不能为空");
            }
            String resumeId = "CV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            String resumeName = file.getOriginalFilename();

            if (resumeName == null || resumeName.trim().isEmpty()) {
                resumeName = "未命名文档";
            }
            //上传并获取简历存储路径
            String filePath = WxFbsirConfig.getUploadPath();
            String url = FileUploadUtils.uploadAndGetFullUrl(filePath,file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
            //更新简历
            resumeManagementService.updataResume(resumeId,resumeName,url,getUserId());
            return success();
        } catch (Exception e){
            log.info("{}",e);
            return error();
        }
    }

    /**
     * 腾讯智能体解析简历
     * @return
     */
    @GetMapping("/parse")
    public AjaxResult parseResume(){
        resumeManagementService.parseResume(getUserId());
        return success();
    }

    /**
     * 查询简历状态、并返回简历数据
     * @return
     */
    @GetMapping("/status")
    public AjaxResult getResumeStatus(){
        ParseResult parseResult =  resumeManagementService.getResumeStatus(getUserId());
        return success(parseResult);
    }

    /**
     * 智能体回调接口
     * @param params
     * @return
     */
    @Anonymous
    @PostMapping("/ParsedContent")
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

            String userId = params.get("documentId").toString();
            String parseContent = params.get("parsedContent").toString();
            //解析智能体返回的json
            ObjectMapper mapper = new ObjectMapper();
            ParseResult parseResult = mapper.readValue(parseContent, ParseResult.class);
            //更新简历内容
            int result = resumeManagementService.updataResumeParseResult(userId,parseResult,1);
            return toAjax(result);

        } catch (Exception e) {
            log.error("更新解析内容异常", e);
            return error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 修改简历访问码设置
     * @param available
     * @return
     */
    @GetMapping("/access-code/change")
    public AjaxResult updateAccessCodeAvailable(@RequestParam("available") Integer available){
        resumeManagementService.updateAccessCodeAvailable(available,getUserId());
        return success();
    }
}
