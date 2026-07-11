package com.wx.fbsir.business.officialaccount.controller;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;

import com.wx.fbsir.business.officialaccount.domain.WcOfficePublishRecord;
import com.wx.fbsir.business.officialaccount.service.IWcOfficePublishRecordService;
import com.wx.fbsir.business.officialaccount.service.WechatOfficialApiService;
import com.wx.fbsir.business.dailyassistant.domain.DailyArticle;
import com.wx.fbsir.business.dailyassistant.service.IDailyArticleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.common.utils.IpUtils;
import com.wx.fbsir.business.officialaccount.domain.WcOfficeAccount;
import com.wx.fbsir.business.officialaccount.service.IWcOfficeAccountService;
import com.wx.fbsir.common.utils.poi.ExcelUtil;
import com.wx.fbsir.common.core.page.TableDataInfo;

/**
 * 微信公众号配置Controller
 * 
 * 提供公众号配置的增删改查功能，以及文章发布到公众号草稿箱的功能
 * 
 * @author FBSir
 * @date 2025-12-08
 */
@RestController
@RequestMapping("/business/office-account")
public class WcOfficeAccountController extends BaseController
{
    @Autowired
    private IWcOfficeAccountService wcOfficeAccountService;
    
    @Autowired
    private IWcOfficePublishRecordService wcOfficePublishRecordService;
    
    @Autowired
    private IDailyArticleService dailyArticleService;
    
    @Autowired
    private WechatOfficialApiService wechatOfficialApiService;

    /**
     * 查询微信公众号配置列表
     */
    @PreAuthorize("@ss.hasPermi('business:office-account:list')")
    @GetMapping("/list")
    public TableDataInfo list(WcOfficeAccount wcOfficeAccount)
    {
        startPage();
        List<WcOfficeAccount> list = wcOfficeAccountService.selectWcOfficeAccountList(wcOfficeAccount);
        return getDataTable(list);
    }

    /**
     * 导出微信公众号配置列表
     */
    @PreAuthorize("@ss.hasPermi('business:office-account:export')")
    @Log(title = "微信公众号配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WcOfficeAccount wcOfficeAccount)
    {
        List<WcOfficeAccount> list = wcOfficeAccountService.selectWcOfficeAccountList(wcOfficeAccount);
        ExcelUtil<WcOfficeAccount> util = new ExcelUtil<WcOfficeAccount>(WcOfficeAccount.class);
        util.exportExcel(response, list, "微信公众号配置数据");
    }

    /**
     * 获取微信公众号配置详细信息
     */
    @PreAuthorize("@ss.hasPermi('business:office-account:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(wcOfficeAccountService.selectWcOfficeAccountById(id));
    }

    /**
     * 获取当前用户的微信公众号配置
     * 
     * 安全策略：保存成功后不再返回 appId 和 appSecret 的实际值
     * 只返回配置状态和其他非敏感信息
     */
    @GetMapping("/my-config")
    public AjaxResult getMyConfig()
    {
        Long userId = getUserId();
        WcOfficeAccount account = wcOfficeAccountService.selectWcOfficeAccountByUserId(userId);
        
        // 返回时完全隐藏敏感信息
        if (account != null) {
            // 标记已配置，但不返回实际的 appId 和 appSecret
            account.setAppId(null);  // 完全不返回
            account.setAppSecret(null);  // 完全不返回
            
            // 前端通过 id 是否为 null 判断是否已配置
            // 如果 id 不为 null，说明已配置，前端显示"已配置（不可查看）"
        }
        
        return success(account);
    }

    /**
     * 验证当前用户的微信公众号配置是否可用
     * 通过获取access_token来验证appId和appSecret是否正确
     */
    @PostMapping("/verify")
    public AjaxResult verifyConfig()
    {
        Long userId = getUserId();
        WcOfficeAccount account = wcOfficeAccountService.selectWcOfficeAccountByUserId(userId);
        
        if (account == null) {
            return error("未配置公众号信息");
        }
        
        if (account.getIsActive() == null || account.getIsActive() != 1) {
            return error("您的公众号配置已被管理员禁用，当前无法使用投递功能。如需继续使用，请联系管理员处理。");
        }
        
        try {
            // 尝试获取access_token来验证配置是否正确
            String accessToken = wechatOfficialApiService.getAccessToken(account);
            if (accessToken != null && !accessToken.isEmpty()) {
                return success("公众号配置验证成功");
            } else {
                return error("获取access_token失败，请检查配置是否正确");
            }
        } catch (Exception e) {
            return error("公众号配置验证失败：" + e.getMessage());
        }
    }

    /**
     * 新增或更新微信公众号配置（个人配置，无需权限）
     * 
     * 说明：此接口用于用户配置自己的公众号，任何登录用户都可以使用
     * 接口会自动绑定当前登录用户ID，用户只能配置自己的公众号
     */
    @Log(title = "微信公众号配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WcOfficeAccount wcOfficeAccount)
    {
        // 强制设置为当前用户，防止越权
        wcOfficeAccount.setUserId(getUserId());
        wcOfficeAccount.setCreateBy(getUsername());
        return toAjax(wcOfficeAccountService.saveOrUpdateWcOfficeAccount(wcOfficeAccount));
    }

    /**
     * 修改微信公众号配置
     */
    @PreAuthorize("@ss.hasPermi('business:office-account:edit')")
    @Log(title = "微信公众号配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WcOfficeAccount wcOfficeAccount)
    {
        wcOfficeAccount.setUpdateBy(getUsername());
        return toAjax(wcOfficeAccountService.updateWcOfficeAccount(wcOfficeAccount));
    }

    /**
     * 删除微信公众号配置
     */
    @PreAuthorize("@ss.hasPermi('business:office-account:remove')")
    @Log(title = "微信公众号配置", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(wcOfficeAccountService.deleteWcOfficeAccountByIds(ids));
    }

    /**
     * 发布文章到微信公众号草稿箱
     * 
     * @param articleId 文章ID
     * @param contentType 内容类型：optimized/model1/model2/model3/layout
     * @return 发布结果
     */
    @PostMapping("/publish/{articleId}/{contentType}")
    @Log(title = "发布文章到公众号", businessType = BusinessType.OTHER)
    public AjaxResult publishToWechat(@PathVariable Long articleId, @PathVariable String contentType, @RequestBody(required = false) Map<String, String> body)
    {
        Long userId = getUserId();
        
        try {
            // 1. 查询用户的公众号配置
            WcOfficeAccount account = wcOfficeAccountService.selectWcOfficeAccountByUserId(userId);
            if (account == null) {
                return error("请先配置公众号信息");
            }
            
            // 检查配置是否被管理员禁用
            if (account.getIsActive() == null || account.getIsActive() != 1) {
                return error("您的公众号配置已被管理员禁用，当前无法使用投递功能。如需继续使用，请联系管理员处理。");
            }
            
            // 2. 查询文章
            DailyArticle article = dailyArticleService.selectDailyArticleById(articleId);
            if (article == null) {
                return error("文章不存在");
            }
            
            if (!article.getUserId().equals(userId)) {
                return error("无权操作该文章");
            }
            
            // 3. 获取内容（支持排版后自定义内容）
            String content;
            if ("layout".equals(contentType) && body != null && body.containsKey("layoutedContent")) {
                // 使用前端排版后的HTML内容
                content = body.get("layoutedContent");
                logger.info("使用排版后内容，长度: {} 字符", content.length());
            } else {
                // 从数据库获取内容
                content = getContentByType(article, contentType);
            }
            
            if (content == null || content.isEmpty()) {
                return error("文章内容为空");
            }
            
            String title = article.getArticleTitle();
            
            // 4. 创建发布记录
            WcOfficePublishRecord record = new WcOfficePublishRecord();
            record.setUserId(userId);
            record.setArticleId(articleId);
            record.setOfficeAccountId(account.getId());
            record.setContentType(contentType);
            record.setPublishStatus(0); // 发布中
            record.setCreateBy(getUsername());
            wcOfficePublishRecordService.insertWcOfficePublishRecord(record);
            
            // 5. 调用微信API发布到草稿箱
            try {
                String mediaId = wechatOfficialApiService.publishToWechat(account, title, content);
                
                // 更新记录状态为成功
                record.setPublishStatus(1);
                record.setMediaId(mediaId);
                wcOfficePublishRecordService.updateWcOfficePublishRecord(record);
                
                // 更新文章的发布次数
                article.setPublishCount((article.getPublishCount() == null ? 0 : article.getPublishCount()) + 1);
                dailyArticleService.updateDailyArticle(article);
                
                return success("发布成功，已保存到公众号草稿箱");
            } catch (Exception e) {
                logger.error("发布文章到公众号失败: {}", e.getMessage());
                
                // 更新记录状态为失败
                record.setPublishStatus(2);
                record.setErrorMessage(e.getMessage());
                wcOfficePublishRecordService.updateWcOfficePublishRecord(record);
                
                return error("发布失败：" + e.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("发布文章到公众号异常: {}", e.getMessage());
            return error("发布失败：" + e.getMessage());
        }
    }

    /**
     * 查询文章的发布记录
     * 
     * @param articleId 文章ID
     * @return 发布记录列表
     */
    @GetMapping("/publish-records/{articleId}")
    public AjaxResult getPublishRecords(@PathVariable Long articleId)
    {
        List<WcOfficePublishRecord> records = wcOfficePublishRecordService.selectWcOfficePublishRecordListByArticleId(articleId);
        return success(records);
    }

    /**
     * 获取服务器公网IP地址
     * 
     * 用于方便用户快速获取后台服务器IP，添加到微信公众号IP白名单中
     * 
     * 安全说明：
     * 1. 需要用户登录才能访问
     * 2. 只返回服务器公网IP，不涉及敏感信息
     * 3. 风险：低 - 仅暴露服务器IP地址
     * 4. 好处：方便用户配置微信公众号白名单
     * 
     * @return 公网IP地址
     */
    @GetMapping("/server-ip")
    public AjaxResult getServerPublicIp()
    {
        try {
            String publicIp = IpUtils.getPublicIpWithCache();
            
            if (publicIp != null && !publicIp.isEmpty()) {
                return AjaxResult.success()
                    .put("ip", publicIp)
                    .put("tip", "请将此IP添加到微信公众号IP白名单中");
            } else {
                return error("获取服务器公网IP失败，请稍后重试或手动查询");
            }
            
        } catch (Exception e) {
            logger.error("获取服务器公网IP异常: {}", e.getMessage());
            return error("获取服务器公网IP异常：" + e.getMessage());
        }
    }

    /**
     * 根据内容类型获取文章内容
     * 
     * @param article 文章对象
     * @param contentType 内容类型
     * @return 文章内容
     */
    private String getContentByType(DailyArticle article, String contentType) {
        switch (contentType) {
            case "optimized":
                return article.getOptimizedContent();
            case "model1":
                return article.getModel1Content();
            case "model2":
                return article.getModel2Content();
            case "model3":
                return article.getModel3Content();
            default:
                return article.getOptimizedContent();
        }
    }
}
