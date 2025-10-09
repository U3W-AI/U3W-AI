package com.cube.web.controller.system;

import com.cube.common.constant.Constants;
import com.cube.common.core.domain.AjaxResult;
import com.cube.common.core.domain.entity.SysMenu;
import com.cube.common.core.domain.entity.SysUser;
import com.cube.common.core.domain.model.LoginBody;
import com.cube.common.utils.SecurityUtils;
import com.cube.framework.web.service.SysLoginService;
import com.cube.framework.web.service.SysPermissionService;
import com.cube.system.service.ISysMenuService;
import com.cube.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 登录验证
 *
 * @author ruoyi
 */
@RestController
public class SysLoginController
{
    @Autowired
    private SysLoginService loginService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private ISysUserService userService;

    /**
     * 登录方法
     *
     * @param loginBody 登录信息
     * @return 结果
     */
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody loginBody)
    {
        AjaxResult ajax = AjaxResult.success();
        // 生成令牌
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid());
        ajax.put(Constants.TOKEN, token);


        return ajax;
    }

    /**
     * 获取用户信息
     *
     * @return 用户信息
     */
    @GetMapping("getInfo")
    public AjaxResult getInfo()
    {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        // 角色集合
        Set<String> roles = permissionService.getRolePermission(user);
        // 权限集合
        Set<String> permissions = permissionService.getMenuPermission(user);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", user);
        ajax.put("roles", roles);
        ajax.put("permissions", permissions);
        return ajax;
    }

    /**
     * 获取路由信息
     *
     * @return 路由信息
     */
    @GetMapping("getRouters")
    public AjaxResult getRouters()
    {
        Long userId = SecurityUtils.getUserId();
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
        return AjaxResult.success(menuService.buildMenus(menus));
    }

    /**
     * 刷新企业ID
     *
     * @return 刷新后的用户信息
     */
    @PostMapping("/refreshCorpId")
    public AjaxResult refreshCorpId()
    {
        SysUser currentUser = SecurityUtils.getLoginUser().getUser();
        // 重新查询用户信息以获取最新的企业ID
        SysUser refreshedUser = userService.selectUserById(currentUser.getUserId());
        
        if (refreshedUser != null) {
            // 更新当前登录用户的企业ID信息
            currentUser.setCorpId(refreshedUser.getCorpId());
            // 更新其他可能需要刷新的字段
            currentUser.setNickName(refreshedUser.getNickName());
            currentUser.setAvatar(refreshedUser.getAvatar());
            currentUser.setPoints(refreshedUser.getPoints());
            
            AjaxResult ajax = AjaxResult.success();
            ajax.put("corpId", refreshedUser.getCorpId());
            ajax.put("user", refreshedUser);
            return ajax;
        } else {
            return AjaxResult.error("用户信息不存在");
        }
    }
}
