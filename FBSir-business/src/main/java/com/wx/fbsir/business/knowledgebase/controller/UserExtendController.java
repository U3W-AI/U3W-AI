package com.wx.fbsir.business.knowledgebase.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.business.knowledgebase.domain.KnowledgeBaseInfo;
import com.wx.fbsir.business.knowledgebase.service.IKnowledgeBaseService;
import com.wx.fbsir.business.knowledgebase.domain.SysUserExtend;
import com.wx.fbsir.business.knowledgebase.service.IUserExtendService;

/**
 * 用户扩展Controller
 * 
 * @author FBSir
 * @date 2025-12-20
 */
@RestController
@RequestMapping("/api/kb")
public class UserExtendController extends BaseController
{
    @Autowired
    private IUserExtendService userExtendService;

    @Autowired
    private IKnowledgeBaseService knowledgeBaseService;

    /**
     * 查询用户扩展信息
     */
    @GetMapping("/account/{userId}")
    public AjaxResult getUserExtendInfo(@PathVariable("userId") Long userId)
    {
        SysUserExtend userExtend = userExtendService.selectUserExtendByUserId(userId);
        return success(userExtend);
    }

    /**
     * 查询当前用户可查看的知识库列表
     *
     * 包含：
     * - 自己拥有的知识库（非公共模板）
     * - 所有公共模板
     */
    @GetMapping("/user/knowledge")
    public AjaxResult getUserVisibleKnowledge()
    {
        java.util.List<KnowledgeBaseInfo> list = knowledgeBaseService.selectUserVisibleKnowledgeBase();
        return success(list);
    }

    /**
     * 查询用户收藏的知识库列表
     *
     * 包含：
     * - 用户收藏的知识库（从kb_likes_ids字段获取）
     */
    @GetMapping("/user/favorite")
    public AjaxResult getFavoriteKnowledgeList()
    {
        java.util.List<KnowledgeBaseInfo> list = knowledgeBaseService.selectFavoriteKnowledgeBase();
        return success(list);
    }

    /**
     * 修改账户权限
     */
    @Log(title = "账户权限", businessType = BusinessType.UPDATE)
    @PutMapping("/account/permission")
    public AjaxResult updateAccountPermission(@RequestParam("userId") Long userId, 
                                               @RequestParam("permissionType") Integer permissionType,
                                               @RequestParam("isOpen") Integer isOpen)
    {
        boolean result = userExtendService.updateAccountPermission(userId, permissionType, isOpen);
        return result ? success() : error("修改账户权限失败");
    }

    /**
     * 查询所有用户的权限信息列表
     * 
     * 权限要求：只有拥有账户权限管理权限的用户或超级账户才能查看
     */
    @GetMapping("/account/permissions")
    public AjaxResult getAllUserPermissions()
    {
        java.util.List<SysUserExtend> list = userExtendService.selectAllUserPermissions();
        return success(list);
    }
}
