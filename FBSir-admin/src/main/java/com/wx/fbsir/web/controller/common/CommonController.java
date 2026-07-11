package com.wx.fbsir.web.controller.common;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.wx.fbsir.common.config.FBSirConfig;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.utils.StringUtils;
import com.wx.fbsir.common.utils.file.FileUploadUtils;
import com.wx.fbsir.common.utils.file.FileUtils;
import com.wx.fbsir.framework.config.ServerConfig;

/**
 * 通用请求处理
 * 
 * @author FBSir
 */
@RestController
@RequestMapping("/common")
public class CommonController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(CommonController.class);

    @Autowired
    private ServerConfig serverConfig;

    private static final String FILE_DELIMITER = ",";

    /**
     * 通用下载请求
     * 
     * @param fileName 文件名称
     * @param delete 是否删除
     */
    @GetMapping("/download")
    public void fileDownload(String fileName, Boolean delete, HttpServletResponse response, HttpServletRequest request)
    {
        try
        {
            if (!FileUtils.checkAllowDownload(fileName))
            {
                throw new Exception(StringUtils.format("文件名称({})非法，不允许下载。 ", fileName));
            }
            String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
            String filePath = FBSirConfig.getDownloadPath() + fileName;

            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, realFileName);
            FileUtils.writeBytes(filePath, response.getOutputStream());
            if (delete)
            {
                FileUtils.deleteFile(filePath);
            }
        }
        catch (Exception e)
        {
            log.error("下载文件失败", e);
        }
    }

    /**
     * 通用上传请求（单个）
     */
    @PostMapping("/upload")
    public AjaxResult uploadFile(MultipartFile file) throws Exception
    {
        try
        {
            // 上传文件路径
            String filePath = FBSirConfig.getUploadPath();
            // 上传并返回完整URL（包含域名）
            String fullUrl = FileUploadUtils.uploadAndGetFullUrl(filePath, file, com.wx.fbsir.common.utils.file.MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", fullUrl);  // 返回完整URL
            ajax.put("fileName", fullUrl);  // 兼容旧代码
            ajax.put("newFileName", FileUtils.getName(fullUrl));
            ajax.put("originalFilename", file.getOriginalFilename());
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 通用上传请求（多个）
     */
    @PostMapping("/uploads")
    public AjaxResult uploadFiles(List<MultipartFile> files) throws Exception
    {
        try
        {
            // 上传文件路径
            String filePath = FBSirConfig.getUploadPath();
            List<String> urls = new ArrayList<String>();
            List<String> fileNames = new ArrayList<String>();
            List<String> newFileNames = new ArrayList<String>();
            List<String> originalFilenames = new ArrayList<String>();
            for (MultipartFile file : files)
            {
                // 上传并返回完整URL（包含域名）
                String fullUrl = FileUploadUtils.uploadAndGetFullUrl(filePath, file, com.wx.fbsir.common.utils.file.MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
                urls.add(fullUrl);
                fileNames.add(fullUrl);  // 兼容旧代码
                newFileNames.add(FileUtils.getName(fullUrl));
                originalFilenames.add(file.getOriginalFilename());
            }
            AjaxResult ajax = AjaxResult.success();
            ajax.put("urls", StringUtils.join(urls, FILE_DELIMITER));
            ajax.put("fileNames", StringUtils.join(fileNames, FILE_DELIMITER));
            ajax.put("newFileNames", StringUtils.join(newFileNames, FILE_DELIMITER));
            ajax.put("originalFilenames", StringUtils.join(originalFilenames, FILE_DELIMITER));
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 本地资源通用下载
     */
    @GetMapping("/download/resource")
    public void resourceDownload(String resource, HttpServletRequest request, HttpServletResponse response)
            throws Exception
    {
        try
        {
            if (!FileUtils.checkAllowDownload(resource))
            {
                throw new Exception(StringUtils.format("资源文件({})非法，不允许下载。 ", resource));
            }
            // 本地资源路径
            String localPath = FBSirConfig.getProfile();
            // 数据库资源地址
            String downloadPath = localPath + FileUtils.stripPrefix(resource);
            // 下载名称
            String downloadName = StringUtils.substringAfterLast(downloadPath, "/");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, downloadName);
            FileUtils.writeBytes(downloadPath, response.getOutputStream());
        }
        catch (Exception e)
        {
            log.error("下载文件失败", e);
        }
    }

    /**
     * 公众号图片上传请求
     * 上传到 officialaccount/{userId} 目录，并返回完整URL
     * 文件名重复时自动添加序号
     */
    @PostMapping("/upload/officialaccount")
    public AjaxResult uploadOfficialAccountImage(MultipartFile file) throws Exception
    {
        try
        {
            // 获取当前用户ID
            Long userId = getUserId();
            
            // 公众号图片上传路径
            String filePath = FBSirConfig.getOfficialAccountUploadPath();
            
            // 使用公众号专用上传方法（按用户ID分目录，文件名重复自动加序号）
            String fullUrl = FileUploadUtils.uploadOfficialAccountAndGetFullUrl(
                filePath, 
                file, 
                userId, 
                com.wx.fbsir.common.utils.file.MimeTypeUtils.IMAGE_EXTENSION
            );
            
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", fullUrl);  // 返回完整URL
            ajax.put("fileName", fullUrl);  // 兼容旧代码
            ajax.put("newFileName", FileUtils.getName(fullUrl));
            ajax.put("originalFilename", file.getOriginalFilename());
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }
}
