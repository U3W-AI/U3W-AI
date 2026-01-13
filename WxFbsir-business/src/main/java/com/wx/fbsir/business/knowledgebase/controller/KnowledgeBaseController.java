package com.wx.fbsir.business.knowledgebase.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.common.utils.StringUtils;
import com.wx.fbsir.common.config.WxFbsirConfig;
import com.wx.fbsir.common.utils.file.FileUploadUtils;
import com.wx.fbsir.common.utils.file.MimeTypeUtils;
import com.wx.fbsir.business.knowledgebase.domain.KnowledgeBaseInfo;
import com.wx.fbsir.business.knowledgebase.service.IKnowledgeBaseService;
import com.wx.fbsir.business.knowledgebase.service.IUserExtendService;

/**
 * 知识库Controller
 * 
 * @author wxfbsir
 * @date 2025-12-20
 */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController extends BaseController
{
    @Autowired
    private IKnowledgeBaseService knowledgeBaseService;

    @Autowired
    private IUserExtendService userExtendService;

    /**
     * 查询公共模板列表
     * 
     * 接口说明：查询所有标记为公共模板的知识库列表
     * 权限要求：无需权限，所有用户可访问
     * 
     * @return 公共模板列表
     */
    @GetMapping("/public")
    public AjaxResult getPublicTemplates()
    {
        List<KnowledgeBaseInfo> list = knowledgeBaseService.selectPublicTemplateList();
        return success(list);
    }

    /**
     * 查询知识库详细信息
     * 
     * 接口说明：根据知识库ID查询知识库的详细信息
     * 权限要求：无需权限，所有用户可访问
     * 
     * @param kbId 知识库ID
     * @return 知识库详细信息
     */
    @GetMapping("/base")
    public AjaxResult getInfo(@RequestParam("kbId") Long kbId)
    {
        KnowledgeBaseInfo knowledgeBase = knowledgeBaseService.selectKnowledgeBaseByKbId(kbId);
        return success(knowledgeBase);
    }

    /**
     * 新增知识库
     * 
     * 接口说明：创建新的知识库，可以是私有知识库或公共模板
     * 
     * @param knowledgeBaseInfo 知识库对象，包含：
     *                          - kbName: 知识库名字（必填）
     *                          - kbContent: 知识库内容，JSON格式
     *                          - isPublicTemplate: 是否为公共模板，0-私有，1-公共（可选，默认0）
     * @return 新增后的知识库ID
     */
    @Log(title = "知识库", businessType = BusinessType.INSERT)
    @PostMapping("/base")
    public AjaxResult add(@RequestBody KnowledgeBaseInfo knowledgeBaseInfo)
    {
        if (StringUtils.isEmpty(knowledgeBaseInfo.getKbName()))
        {
            return error("知识库名字不能为空");
        }
        int result = knowledgeBaseService.insertKnowledgeBase(knowledgeBaseInfo);
        if (result > 0)
        {
            return success(knowledgeBaseInfo.getKbId());
        }
        return error("新增知识库失败");
    }

    /**
     * 修改知识库
     * 
     * 接口说明：修改已存在的知识库信息
     * 特殊说明：如果修改的是公共模板，需要模块功能操作权限或超级账户权限（在Service层检查）
     * 
     * @param knowledgeBaseInfo 知识库对象，必须包含kbId
     * @return 修改后的知识库ID
     */
    @Log(title = "知识库", businessType = BusinessType.UPDATE)
    @PutMapping("/base")
    public AjaxResult edit(@RequestBody KnowledgeBaseInfo knowledgeBaseInfo)
    {
        if (knowledgeBaseInfo.getKbId() == null)
        {
            return error("知识库ID不能为空");
        }
        int result = knowledgeBaseService.updateKnowledgeBase(knowledgeBaseInfo);
        if (result > 0)
        {
            return success(knowledgeBaseInfo.getKbId());
        }
        return error("修改知识库失败");
    }

    /**
     * 删除知识库
     * 
     * 接口说明：批量删除知识库（支持单个或批量删除）
     * 
     * @param kbId 知识库ID
     * @return 被删除的知识库ID数组
     */
    @Log(title = "知识库", businessType = BusinessType.DELETE)
    @DeleteMapping("/base/{kbId}")
    public AjaxResult remove(@PathVariable Long kbId)
    {
        int result = knowledgeBaseService.deleteKnowledgeBaseByKbIds(kbId);
        if (result > 0)
        {
            return success(kbId);
        }
        return error("删除知识库失败");
    }

    /**
     * 收藏/取消收藏知识库
     * 
     * 接口说明：将知识库添加到用户的收藏列表或从收藏列表中移除
     * 业务逻辑：
     * 1. 检查知识库是否存在
     * 2. 如果已收藏则取消收藏，如果未收藏则添加收藏
     * 
     * @param kbId 知识库ID
     * @param userId 用户ID
     * @return 操作结果
     */
    @Log(title = "知识库收藏", businessType = BusinessType.UPDATE)
    @PostMapping("/favorite")
    public AjaxResult toggleFavoriteKnowledge(@RequestParam("kbId") Long kbId, @RequestParam("userId") Long userId)
    {
        boolean result = knowledgeBaseService.toggleFavoriteKnowledge(kbId, userId);
        return result ? success() : error("收藏操作失败");
    }

    /**
     * 修改空间限额
     * 
     * 接口说明：修改用户知识库空间的存储限额（按MB计算）
     * 特殊说明：只有拥有模块功能操作权限的用户或超级账户才能修改空间限额（在Service层检查）
     * 
     * @param userId 被操作用户ID（要修改空间限额的目标用户）
     * @param quota 修改后的额度（按MB计算，单位：MB）
     * @return 操作结果
     */
    @Log(title = "知识库空间限额", businessType = BusinessType.UPDATE)
    @PutMapping("/space/quota")
    public AjaxResult updateSpaceQuota(@RequestParam("userId") Long userId, @RequestParam("quota") Long quota)
    {
        boolean result = userExtendService.updateSpaceQuota(userId, quota);
        return result ? success() : error("修改空间限额失败");
    }

    /**
     * 知识库上传
     * 
     * 接口说明：将知识库内容上传到智能体元器或企业微信机器人
     * 业务逻辑：
     * 1. 查询知识库内容
     * 2. 将知识库内容写入文件并上传到服务器，生成URL
     * 3. 根据上传类型调用PlayWright脚本完成上传
     * 
     * 上传类型：
     * - 1: 上传到智能体元器（需要agentName参数，teamName可选，默认"个人空间"）
     * - 2: 上传到企业微信机器人（需要robotName参数）
     * - 3: 同时上传到两者（需要agentName和robotName参数，teamName可选，默认"个人空间"）
     * 
     * @param kbId 知识库ID
     * @param uploadType 上传类型（1-智能体元器，2-企业微信机器人，3-两者）
     * @param agentName 智能体名称（上传类型为1或3时必填）
     * @param robotName 机器人名称（上传类型为2或3时必填）
     * @param teamName 团队名称（上传类型为1或3时可选，默认"个人空间"）
     * @return 操作结果
     */
    @Log(title = "知识库上传", businessType = BusinessType.OTHER)
    @PostMapping("/upload")
    public AjaxResult uploadKnowledgeBase(@RequestParam("kbId") Long kbId, 
                                          @RequestParam("uploadType") Integer uploadType,
                                          @RequestParam(value = "agentName", required = false) String agentName,
                                          @RequestParam(value = "robotName", required = false) String robotName,
                                          @RequestParam(value = "teamName", required = false) String teamName)
    {
        // 参数验证
        if (uploadType == 1 && StringUtils.isEmpty(agentName))
        {
            return error("上传到智能体元器时，智能体名称不能为空");
        }
        if (uploadType == 2 && StringUtils.isEmpty(robotName))
        {
            return error("上传到企业微信机器人时，机器人名称不能为空");
        }
        if (uploadType == 3)
        {
            if (StringUtils.isEmpty(agentName))
            {
                return error("同时上传到两者时，智能体名称不能为空");
            }
            if (StringUtils.isEmpty(robotName))
            {
                return error("同时上传到两者时，机器人名称不能为空");
            }
        }
        
        // 如果上传到元器或同时上传到两者，且未提供团队名称，则默认"个人空间"
        String finalTeamName = teamName;
        if ((uploadType == 1 || uploadType == 3) && StringUtils.isEmpty(finalTeamName))
        {
            finalTeamName = "个人空间";
        }
        boolean result = knowledgeBaseService.uploadKnowledgeBase(kbId, uploadType, agentName, robotName, finalTeamName);
        return result ? success() : error("知识库上传失败");
    }

    /**
     * 本地文档上传
     * 
     * 接口说明：上传本地文档到智能体元器或企业微信机器人
     * 业务逻辑：
     * 1. 接收上传的文件
     * 2. 将文件保存到服务器，生成可访问的URL
     * 3. 根据上传类型调用PlayWright脚本完成上传
     * 
     * 上传类型：
     * - 1: 上传到智能体元器（需要agentName参数）
     * - 2: 上传到企业微信机器人（需要robotName参数）
     * - 3: 同时上传到两者（需要agentName和robotName参数）
     * 
     * @param file 上传的文件
     * @param uploadType 上传类型（1-智能体元器，2-企业微信机器人，3-两者）
     * @param agentName 智能体名称（上传类型为1或3时必填）
     * @param robotName 机器人名称（上传类型为2或3时必填）
     * @param kbName 知识库名称（可选，如果不提供则使用文件名）
     * @param teamName 团队名称（上传类型为1或3时可选，默认"个人空间"）
     * @return 操作结果
     */
    @Log(title = "本地文档上传", businessType = BusinessType.OTHER)
    @PostMapping("/file/upload")
    public AjaxResult uploadLocalDocument(@RequestParam("file") MultipartFile file, 
                                          @RequestParam("uploadType") Integer uploadType,
                                          @RequestParam(value = "agentName", required = false) String agentName,
                                          @RequestParam(value = "robotName", required = false) String robotName,
                                          @RequestParam(value = "kbName", required = false) String kbName,
                                          @RequestParam(value = "teamName", required = false) String teamName)
    {
        if (file == null || file.isEmpty())
        {
            return error("文件不能为空");
        }
        
        // 文件格式验证：只允许pdf、doc、docx、ppt、mhtml、pptx、wps、ppsx、xlsx、xls、md、txt、csv、html、pg、png、jpeg、tiff、bmp、gif格式
        String[] allowedExtensions = {"pdf", "doc", "docx", "ppt", "mhtml", "pptx", "wps", "ppsx", "xlsx", "xls", "md", "txt", "csv", "html", "pg", "png", "jpeg", "tiff", "bmp", "gif"};
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty())
        {
            return error("文件名不能为空");
        }
        String fileExtension = "";
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < originalFilename.length() - 1)
        {
            fileExtension = originalFilename.substring(lastDotIndex + 1).toLowerCase();
        }
        boolean isAllowedFormat = false;
        for (String ext : allowedExtensions)
        {
            if (ext.equalsIgnoreCase(fileExtension))
            {
                isAllowedFormat = true;
                break;
            }
        }
        if (!isAllowedFormat)
        {
            return error("文件格式不支持，只允许上传以下格式：pdf、doc、docx、ppt、mhtml、pptx、wps、ppsx、xlsx、xls、md、txt、csv、html、pg、png、jpeg、tiff、bmp、gif");
        }
        
        // 文件大小验证：不能超过20MB
        long maxSize = 20L * 1024 * 1024; // 20MB in bytes
        if (file.getSize() > maxSize)
        {
            return error("文件大小不能超过20MB");
        }
        
        // 参数验证
        if (uploadType == 1 && StringUtils.isEmpty(agentName))
        {
            return error("上传到智能体元器时，智能体名称不能为空");
        }
        if (uploadType == 2 && StringUtils.isEmpty(robotName))
        {
            return error("上传到企业微信机器人时，机器人名称不能为空");
        }
        if (uploadType == 3)
        {
            if (StringUtils.isEmpty(agentName))
            {
                return error("同时上传到两者时，智能体名称不能为空");
            }
            if (StringUtils.isEmpty(robotName))
            {
                return error("同时上传到两者时，机器人名称不能为空");
            }
        }
        
        // 保存文件并获取文件访问URL（上传到Linux服务器）
        String fileUrl;
        try
        {
            // 上传文件路径（Linux服务器上的/profile/upload目录）
            String uploadPath = WxFbsirConfig.getUploadPath();
            // 上传并返回完整URL（包含域名）
            fileUrl = FileUploadUtils.uploadAndGetFullUrl(uploadPath, file,
                    MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
        }
        catch (Exception e)
        {
            logger.error("本地文档上传失败", e);
            return error("文件上传失败：" + e.getMessage());
        }
        
        // 如果没有传递知识库名称，使用文件名（不含扩展名）作为知识库名称
        String knowledgeBaseName = kbName;
        if (StringUtils.isEmpty(knowledgeBaseName) && file != null && file.getOriginalFilename() != null)
        {
            knowledgeBaseName = lastDotIndex > 0 ? originalFilename.substring(0, lastDotIndex) : originalFilename;
        }
        if (StringUtils.isEmpty(knowledgeBaseName))
        {
            knowledgeBaseName = "本地文档";
        }
        
        // 如果上传到元器或同时上传到两者，且未提供团队名称，则默认"个人空间"
        String finalTeamName = teamName;
        if ((uploadType == 1 || uploadType == 3) && StringUtils.isEmpty(finalTeamName))
        {
            finalTeamName = "个人空间";
        }
        
        boolean result = knowledgeBaseService.uploadLocalDocument(fileUrl, uploadType, agentName, robotName, knowledgeBaseName, finalTeamName);
        return result ? success() : error("本地文档上传失败");
    }
}
