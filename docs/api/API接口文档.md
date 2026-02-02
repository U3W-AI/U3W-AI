# 福帮手（WxFbsir）完整API接口文档

## 📋 目录

- [项目概述](#项目概述)
- [API基础路径](#api基础路径)
- [一、系统管理模块](#一系统管理模块)
  - [1.1 用户管理接口](#11-用户管理接口-systemuser)
  - [1.2 角色管理接口](#12-角色管理接口-systemrole)
  - [1.3 部门管理接口](#13-部门管理接口-systemdept)
  - [1.4 菜单管理接口](#14-菜单管理接口-systemmenu)
  - [1.5 岗位管理接口](#15-岗位管理接口-systempost)
  - [1.6 字典类型管理接口](#16-字典类型管理接口-systemdicttype)
  - [1.7 字典数据管理接口](#17-字典数据管理接口-systemdictdata)
  - [1.8 参数配置管理接口](#18-参数配置管理接口-systemconfig)
  - [1.9 通知公告管理接口](#19-通知公告管理接口-systemnotice)
  - [1.10 登录相关接口](#110-登录相关接口-system)
- [二、业务管理模块](#二业务管理模块)
  - [2.1 AI内容生成接口](#21-ai内容生成接口-businessaigc)
  - [2.2 证书管理接口](#22-证书管理接口-businesscertificate)
  - [2.3 日更助手接口](#23-日更助手接口-businessdailyassistant)
  - [2.4 文档解析接口](#24-文档解析接口-businessdocumentparse)
  - [2.5 Gitee相关接口](#25-gitee相关接口-businessgitee)
  - [2.6 积分管理接口](#26-积分管理接口-businesspoint)
  - [2.7 公众号管理接口](#27-公众号管理接口-businessofficialaccount)
  - [2.8 WebSocket/Engine管理接口](#28-websocketengine管理接口-businesswebsocket)
- [三、监控管理模块](#三监控管理模块)
  - [3.1 缓存监控接口](#31-缓存监控接口-monitorcache)
  - [3.2 服务器监控接口](#32-服务器监控接口-monitorserver)
  - [3.3 登录日志接口](#33-登录日志接口-monitorlogininfor)
  - [3.4 操作日志接口](#34-操作日志接口-monitoroperlog)
  - [3.5 在线用户接口](#35-在线用户接口-monitoronline)
- [四、通用接口模块](#四通用接口模块)
  - [4.1 通用接口](#41-通用接口-common)
  - [4.2 验证码接口](#42-验证码接口-captchaimage)
- [五、工具模块](#五工具模块)
  - [5.1 测试接口](#51-测试接口-testuser)
- [六、认证与授权](#六认证与授权)
- [七、错误码说明](#七错误码说明)
- [八、使用说明](#八使用说明)

---

## 项目概述
福帮手（WxFbsir）是一个面向企业团队与内容运营场景的AI工具链与智能协同平台，提供完整的用户权限管理、内容生产、文档解析、公众号投递等功能。

## API基础路径
- **基础URL**: `http://localhost:8080`

---

## 一、系统管理模块

### 1.1 用户管理接口 (`/system/user`)

#### 获取用户列表
- **请求方式**: `GET`
- **请求路径**: `/system/user/list`
- **权限要求**: `system:user:list`
- **请求参数**: 
  - `SysUser`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询用户列表，支持按姓名、部门等条件过滤

#### 导出用户数据
- **请求方式**: `POST`
- **请求路径**: `/system/user/export`
- **权限要求**: `system:user:export`
- **请求参数**: 
  - `SysUser`对象（可选查询条件）
- **返回类型**: `void`（Excel文件下载）
- **功能说明**: 导出用户数据为Excel文件

#### 导入用户数据
- **请求方式**: `POST`
- **请求路径**: `/system/user/importData`
- **权限要求**: `system:user:import`
- **请求参数**: 
  - `file`: Multipart文件
  - `updateSupport`: boolean，是否支持更新
- **返回类型**: `AjaxResult`
- **功能说明**: 从Excel文件导入用户数据

#### 获取用户详情
- **请求方式**: `GET`
- **请求路径**: `/system/user/{userId}` 或 `/system/user/`
- **权限要求**: `system:user:query`
- **路径参数**: 
  - `userId`: Long，用户ID（可选）
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定用户详细信息，包括角色、岗位等关联信息

#### 新增用户
- **请求方式**: `POST`
- **请求路径**: `/system/user`
- **权限要求**: `system:user:add`
- **请求体**: `SysUser`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 新增用户信息，自动加密密码

#### 修改用户
- **请求方式**: `PUT`
- **请求路径**: `/system/user`
- **权限要求**: `system:user:edit`
- **请求体**: `SysUser`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新用户信息

#### 删除用户
- **请求方式**: `DELETE`
- **请求路径**: `/system/user/{userIds}`
- **权限要求**: `system:user:remove`
- **路径参数**: 
  - `userIds`: Long[]，用户ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定用户（不能删除当前用户）

#### 重置密码
- **请求方式**: `PUT`
- **请求路径**: `/system/user/resetPwd`
- **权限要求**: `system:user:resetPwd`
- **请求体**: `{userId: Long, password: String}`
- **返回类型**: `AjaxResult`
- **功能说明**: 重置用户密码

#### 修改用户状态
- **请求方式**: `PUT`
- **请求路径**: `/system/user/changeStatus`
- **权限要求**: `system:user:edit`
- **请求体**: `SysUser`对象（包含userId和status）
- **返回类型**: `AjaxResult`
- **功能说明**: 修改用户状态（启用/停用）

#### 获取用户授权角色
- **请求方式**: `GET`
- **请求路径**: `/system/user/authRole/{userId}`
- **权限要求**: `system:user:query`
- **路径参数**: 
  - `userId`: Long，用户ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取用户当前授权的角色信息

#### 用户授权角色
- **请求方式**: `PUT`
- **请求路径**: `/system/user/authRole`
- **权限要求**: `system:user:edit`
- **请求参数**: 
  - `userId`: Long，用户ID
  - `roleIds`: Long[]，角色ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 为用户分配角色权限

#### 获取部门树
- **请求方式**: `GET`
- **请求路径**: `/system/user/deptTree`
- **权限要求**: `system:user:list`
- **请求参数**: 
  - `SysDept`对象（可选查询条件）
- **返回类型**: `AjaxResult`
- **功能说明**: 获取部门树形结构

---

### 1.2 角色管理接口 (`/system/role`)

#### 获取角色列表
- **请求方式**: `GET`
- **请求路径**: `/system/role/list`
- **权限要求**: `system:role:list`
- **请求参数**: 
  - `SysRole`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询角色列表

#### 获取角色详情
- **请求方式**: `GET`
- **请求路径**: `/system/role/{roleId}`
- **权限要求**: `system:role:query`
- **路径参数**: 
  - `roleId`: Long，角色ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定角色详细信息

#### 新增角色
- **请求方式**: `POST`
- **请求路径**: `/system/role`
- **权限要求**: `system:role:add`
- **请求体**: `SysRole`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 新增角色信息

#### 修改角色
- **请求方式**: `PUT`
- **请求路径**: `/system/role`
- **权限要求**: `system:role:edit`
- **请求体**: `SysRole`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新角色信息

#### 删除角色
- **请求方式**: `DELETE`
- **请求路径**: `/system/role/{roleIds}`
- **权限要求**: `system:role:remove`
- **路径参数**: 
  - `roleIds`: Long[]，角色ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定角色

#### 修改角色状态
- **请求方式**: `PUT`
- **请求路径**: `/system/role/changeStatus`
- **权限要求**: `system:role:edit`
- **请求体**: `SysRole`对象（包含roleId和status）
- **返回类型**: `AjaxResult`
- **功能说明**: 修改角色状态

#### 数据权限
- **请求方式**: `PUT`
- **请求路径**: `/system/role/dataScope`
- **权限要求**: `system:role:edit`
- **请求体**: `SysRole`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 配置角色的数据权限范围

#### 角色选择框
- **请求方式**: `GET`
- **请求路径**: `/system/role/optionselect`
- **返回类型**: `AjaxResult`
- **功能说明**: 获取角色下拉选择项

---

### 1.3 部门管理接口 (`/system/dept`)

#### 获取部门列表
- **请求方式**: `GET`
- **请求路径**: `/system/dept/list`
- **权限要求**: `system:dept:list`
- **请求参数**: 
  - `SysDept`对象（可选查询条件）
- **返回类型**: `AjaxResult`
- **功能说明**: 查询部门列表，返回树形结构

#### 获取部门详情
- **请求方式**: `GET`
- **请求路径**: `/system/dept/{deptId}`
- **权限要求**: `system:dept:query`
- **路径参数**: 
  - `deptId`: Long，部门ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定部门详细信息

#### 新增部门
- **请求方式**: `POST`
- **请求路径**: `/system/dept`
- **权限要求**: `system:dept:add`
- **请求体**: `SysDept`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 新增部门信息

#### 修改部门
- **请求方式**: `PUT`
- **请求路径**: `/system/dept`
- **权限要求**: `system:dept:edit`
- **请求体**: `SysDept`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新部门信息

#### 删除部门
- **请求方式**: `DELETE`
- **请求路径**: `/system/dept/{deptId}`
- **权限要求**: `system:dept:remove`
- **路径参数**: 
  - `deptId`: Long，部门ID
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定部门

---

### 1.4 菜单管理接口 (`/system/menu`)

#### 获取菜单列表
- **请求方式**: `GET`
- **请求路径**: `/system/menu/list`
- **权限要求**: `system:menu:list`
- **请求参数**: 
  - `SysMenu`对象（可选查询条件）
- **返回类型**: `AjaxResult`
- **功能说明**: 查询菜单列表，返回树形结构

#### 获取菜单详情
- **请求方式**: `GET`
- **请求路径**: `/system/menu/{menuId}`
- **权限要求**: `system:menu:query`
- **路径参数**: 
  - `menuId`: Long，菜单ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定菜单详细信息

#### 新增菜单
- **请求方式**: `POST`
- **请求路径**: `/system/menu`
- **权限要求**: `system:menu:add`
- **请求体**: `SysMenu`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 新增菜单信息

#### 修改菜单
- **请求方式**: `PUT`
- **请求路径**: `/system/menu`
- **权限要求**: `system:menu:edit`
- **请求体**: `SysMenu`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新菜单信息

#### 删除菜单
- **请求方式**: `DELETE`
- **请求路径**: `/system/menu/{menuId}`
- **权限要求**: `system:menu:remove`
- **路径参数**: 
  - `menuId`: Long，菜单ID
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定菜单

#### 获取菜单下拉树
- **请求方式**: `GET`
- **请求路径**: `/system/menu/treeselect`
- **返回类型**: `AjaxResult`
- **功能说明**: 获取菜单下拉树选择项

#### 加载角色菜单权限
- **请求方式**: `GET`
- **请求路径**: `/system/menu/roleMenuTreeselect/{roleId}`
- **路径参数**: 
  - `roleId`: Long，角色ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取角色拥有的菜单权限树

---

### 1.5 岗位管理接口 (`/system/post`)

#### 获取岗位列表
- **请求方式**: `GET`
- **请求路径**: `/system/post/list`
- **权限要求**: `system:post:list`
- **请求参数**: 
  - `SysPost`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询岗位列表

#### 获取岗位详情
- **请求方式**: `GET`
- **请求路径**: `/system/post/{postId}`
- **权限要求**: `system:post:query`
- **路径参数**: 
  - `postId`: Long，岗位ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定岗位详细信息

#### 新增岗位
- **请求方式**: `POST`
- **请求路径**: `/system/post`
- **权限要求**: `system:post:add`
- **请求体**: `SysPost`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 新增岗位信息

#### 修改岗位
- **请求方式**: `PUT`
- **请求路径**: `/system/post`
- **权限要求**: `system:post:edit`
- **请求体**: `SysPost`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新岗位信息

#### 删除岗位
- **请求方式**: `DELETE`
- **请求路径**: `/system/post/{postIds}`
- **权限要求**: `system:post:remove`
- **路径参数**: 
  - `postIds`: Long[]，岗位ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定岗位

#### 岗位选择框
- **请求方式**: `GET`
- **请求路径**: `/system/post/optionselect`
- **返回类型**: `AjaxResult`
- **功能说明**: 获取岗位下拉选择项

---

### 1.6 字典类型管理接口 (`/system/dict/type`)

#### 获取字典类型列表
- **请求方式**: `GET`
- **请求路径**: `/system/dict/type/list`
- **权限要求**: `system:dict:list`
- **请求参数**: 
  - `SysDictType`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询字典类型列表

#### 获取字典类型详情
- **请求方式**: `GET`
- **请求路径**: `/system/dict/type/{dictId}`
- **权限要求**: `system:dict:query`
- **路径参数**: 
  - `dictId`: Long，字典类型ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定字典类型详细信息

#### 新增字典类型
- **请求方式**: `POST`
- **请求路径**: `/system/dict/type`
- **权限要求**: `system:dict:add`
- **请求体**: `SysDictType`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 新增字典类型信息

#### 修改字典类型
- **请求方式**: `PUT`
- **请求路径**: `/system/dict/type`
- **权限要求**: `system:dict:edit`
- **请求体**: `SysDictType`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新字典类型信息

#### 删除字典类型
- **请求方式**: `DELETE`
- **请求路径**: `/system/dict/type/{dictIds}`
- **权限要求**: `system:dict:remove`
- **路径参数**: 
  - `dictIds`: Long[]，字典类型ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定字典类型

#### 导出字典类型
- **请求方式**: `POST`
- **请求路径**: `/system/dict/type/export`
- **权限要求**: `system:dict:export`
- **请求参数**: 
  - `SysDictType`对象（可选查询条件）
- **返回类型**: `void`（Excel文件下载）
- **功能说明**: 导出字典类型数据为Excel文件

#### 获取字典选择框
- **请求方式**: `GET`
- **请求路径**: `/system/dict/type/optionselect`
- **返回类型**: `AjaxResult`
- **功能说明**: 获取字典类型下拉选择项

---

### 1.7 字典数据管理接口 (`/system/dict/data`)

#### 获取字典数据列表
- **请求方式**: `GET`
- **请求路径**: `/system/dict/data/list`
- **权限要求**: `system:dict:list`
- **请求参数**: 
  - `SysDictData`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询字典数据列表

#### 获取字典数据详情
- **请求方式**: `GET`
- **请求路径**: `/system/dict/data/{dictCode}`
- **权限要求**: `system:dict:query`
- **路径参数**: 
  - `dictCode`: Long，字典数据ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定字典数据详细信息

#### 新增字典数据
- **请求方式**: `POST`
- **请求路径**: `/system/dict/data`
- **权限要求**: `system:dict:add`
- **请求体**: `SysDictData`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 新增字典数据信息

#### 修改字典数据
- **请求方式**: `PUT`
- **请求路径**: `/system/dict/data`
- **权限要求**: `system:dict:edit`
- **请求体**: `SysDictData`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新字典数据信息

#### 删除字典数据
- **请求方式**: `DELETE`
- **请求路径**: `/system/dict/data/{dictCodes}`
- **权限要求**: `system:dict:remove`
- **路径参数**: 
  - `dictCodes`: Long[]，字典数据ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定字典数据

#### 导出字典数据
- **请求方式**: `POST`
- **请求路径**: `/system/dict/data/export`
- **权限要求**: `system:dict:export`
- **请求参数**: 
  - `SysDictData`对象（可选查询条件）
- **返回类型**: `void`（Excel文件下载）
- **功能说明**: 导出字典数据为Excel文件

#### 获取字典数据
- **请求方式**: `GET`
- **请求路径**: `/system/dict/data/type/{dictType}`
- **路径参数**: 
  - `dictType`: String，字典类型
- **返回类型**: `AjaxResult`
- **功能说明**: 根据字典类型获取字典数据列表

---

### 1.8 参数配置管理接口 (`/system/config`)

#### 获取参数配置列表
- **请求方式**: `GET`
- **请求路径**: `/system/config/list`
- **权限要求**: `system:config:list`
- **请求参数**: 
  - `SysConfig`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询参数配置列表

#### 获取参数配置详情
- **请求方式**: `GET`
- **请求路径**: `/system/config/{configId}`
- **权限要求**: `system:config:query`
- **路径参数**: 
  - `configId`: Long，参数配置ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定参数配置详细信息

#### 新增参数配置
- **请求方式**: `POST`
- **请求路径**: `/system/config`
- **权限要求**: `system:config:add`
- **请求体**: `SysConfig`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 新增参数配置信息

#### 修改参数配置
- **请求方式**: `PUT`
- **请求路径**: `/system/config`
- **权限要求**: `system:config:edit`
- **请求体**: `SysConfig`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新参数配置信息

#### 删除参数配置
- **请求方式**: `DELETE`
- **请求路径**: `/system/config/{configIds}`
- **权限要求**: `system:config:remove`
- **路径参数**: 
  - `configIds`: Long[]，参数配置ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定参数配置

#### 导出参数配置
- **请求方式**: `POST`
- **请求路径**: `/system/config/export`
- **权限要求**: `system:config:export`
- **请求参数**: 
  - `SysConfig`对象（可选查询条件）
- **返回类型**: `void`（Excel文件下载）
- **功能说明**: 导出参数配置为Excel文件

#### 根据参数键名查询参数值
- **请求方式**: `GET`
- **请求路径**: `/system/config/configKey/{configKey}`
- **路径参数**: 
  - `configKey`: String，参数键名
- **返回类型**: `AjaxResult`
- **功能说明**: 根据参数键名获取对应的参数值

---

### 1.9 通知公告管理接口 (`/system/notice`)

#### 获取通知公告列表
- **请求方式**: `GET`
- **请求路径**: `/system/notice/list`
- **权限要求**: `system:notice:list`
- **请求参数**: 
  - `SysNotice`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询通知公告列表

#### 获取通知公告详情
- **请求方式**: `GET`
- **请求路径**: `/system/notice/{noticeId}`
- **权限要求**: `system:notice:query`
- **路径参数**: 
  - `noticeId`: Long，通知公告ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定通知公告详细信息

#### 新增通知公告
- **请求方式**: `POST`
- **请求路径**: `/system/notice`
- **权限要求**: `system:notice:add`
- **请求体**: `SysNotice`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 新增通知公告信息

#### 修改通知公告
- **请求方式**: `PUT`
- **请求路径**: `/system/notice`
- **权限要求**: `system:notice:edit`
- **请求体**: `SysNotice`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新通知公告信息

#### 删除通知公告
- **请求方式**: `DELETE`
- **请求路径**: `/system/notice/{noticeIds}`
- **权限要求**: `system:notice:remove`
- **路径参数**: 
  - `noticeIds`: Long[]，通知公告ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定通知公告

---

### 1.10 登录相关接口 (`/system`)

#### 用户登录
- **请求方式**: `POST`
- **请求路径**: `/login`
- **请求体**: `{username: String, password: String, code: String, uuid: String}`
- **返回类型**: `AjaxResult`
- **功能说明**: 用户登录验证，返回令牌

#### 获取用户信息
- **请求方式**: `GET`
- **请求路径**: `/getInfo`
- **返回类型**: `AjaxResult`
- **功能说明**: 获取当前登录用户信息和权限

#### 获取用户菜单信息
- **请求方式**: `GET`
- **请求路径**: `/getRouters`
- **返回类型**: `AjaxResult`
- **功能说明**: 获取当前用户菜单路由信息

#### 用户登出
- **请求方式**: `POST`
- **请求路径**: `/logout`
- **返回类型**: `AjaxResult`
- **功能说明**: 用户登出，清除令牌

#### 用户注册
- **请求方式**: `POST`
- **请求路径**: `/register`
- **请求体**: `{username: String, password: String, email: String, code: String, uuid: String}`
- **返回类型**: `AjaxResult`
- **功能说明**: 用户注册

---

## 二、业务管理模块

### 2.1 AI内容生成接口 (`/business/aigc`)

#### 获取AI生成任务列表
- **请求方式**: `GET`
- **请求路径**: `/business/aigc/task/list`
- **权限要求**: 根据具体业务配置
- **请求参数**: 
  - `AigcTask`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询AI内容生成任务列表

#### 创建AI内容生成任务
- **请求方式**: `POST`
- **请求路径**: `/business/aigc/task/create`
- **权限要求**: 根据具体业务配置
- **请求体**: `AigcTaskCreateDTO`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 创建AI内容生成任务

#### 获取AI生成结果
- **请求方式**: `GET`
- **请求路径**: `/business/aigc/task/{taskId}/result`
- **权限要求**: 根据具体业务配置
- **路径参数**: 
  - `taskId`: Long，任务ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取AI内容生成任务结果

#### 取消AI生成任务
- **请求方式**: `PUT`
- **请求路径**: `/business/aigc/task/{taskId}/cancel`
- **权限要求**: 根据具体业务配置
- **路径参数**: 
  - `taskId`: Long，任务ID
- **返回类型**: `AjaxResult`
- **功能说明**: 取消AI内容生成任务

---

### 2.2 证书管理接口 (`/business/certificate`)

#### 证书模板管理

##### 获取证书模板列表
- **请求方式**: `GET`
- **请求路径**: `/business/certificate/template/list`
- **权限要求**: `certificate:template:list`
- **请求参数**: 
  - `CertificateTemplate`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询证书模板列表

##### 获取证书模板详情
- **请求方式**: `GET`
- **请求路径**: `/business/certificate/template/{templateId}`
- **权限要求**: `certificate:template:query`
- **路径参数**: 
  - `templateId`: Long，模板ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定证书模板详细信息

##### 新增证书模板
- **请求方式**: `POST`
- **请求路径**: `/business/certificate/template`
- **权限要求**: `certificate:template:add`
- **请求体**: `CertificateTemplate`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 新增证书模板信息

##### 修改证书模板
- **请求方式**: `PUT`
- **请求路径**: `/business/certificate/template`
- **权限要求**: `certificate:template:edit`
- **请求体**: `CertificateTemplate`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新证书模板信息

##### 删除证书模板
- **请求方式**: `DELETE`
- **请求路径**: `/business/certificate/template/{templateIds}`
- **权限要求**: `certificate:template:remove`
- **路径参数**: 
  - `templateIds`: Long[]，模板ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定证书模板

#### 证书申请管理

##### 获取证书申请列表
- **请求方式**: `GET`
- **请求路径**: `/business/certificate/application/list`
- **权限要求**: `certificate:application:list`
- **请求参数**: 
  - `CertificateApplication`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询证书申请列表

##### 获取证书申请详情
- **请求方式**: `GET`
- **请求路径**: `/business/certificate/application/{applicationId}`
- **权限要求**: `certificate:application:query`
- **路径参数**: 
  - `applicationId`: Long，申请ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定证书申请详细信息

##### 提交证书申请
- **请求方式**: `POST`
- **请求路径**: `/business/certificate/application/submit`
- **权限要求**: 无（用户可提交）
- **请求体**: `CertificateApplication`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 提交证书申请

##### 修改证书申请
- **请求方式**: `PUT`
- **请求路径**: `/business/certificate/application`
- **权限要求**: `certificate:application:edit`
- **请求体**: `CertificateApplication`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新证书申请信息

##### 删除证书申请
- **请求方式**: `DELETE`
- **请求路径**: `/business/certificate/application/{applicationIds}`
- **权限要求**: `certificate:application:remove`
- **路径参数**: 
  - `applicationIds`: Long[]，申请ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定证书申请

#### 申请审核管理

##### 获取待审核申请列表
- **请求方式**: `GET`
- **请求路径**: `/business/certificate/review/pending-list`
- **权限要求**: `certificate:review:list`
- **请求参数**: 
  - `ApplicationReview`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询待审核申请列表

##### 审核申请
- **请求方式**: `POST`
- **请求路径**: `/business/certificate/review/approve`
- **权限要求**: `certificate:review:approve`
- **请求体**: `{applicationId: Long, status: String, remarks: String}`
- **返回类型**: `AjaxResult`
- **功能说明**: 审核通过证书申请

##### 驳回申请
- **请求方式**: `POST`
- **请求路径**: `/business/certificate/review/reject`
- **权限要求**: `certificate:review:reject`
- **请求体**: `{applicationId: Long, remarks: String}`
- **返回类型**: `AjaxResult`
- **功能说明**: 驳回证书申请

---

### 2.3 日更助手接口 (`/business/dailyassistant`)

#### 文章管理

##### 获取文章列表
- **请求方式**: `GET`
- **请求路径**: `/business/dailyassistant/article/list`
- **权限要求**: `daily:article:list`
- **请求参数**: 
  - `DailyArticle`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询文章列表

##### 获取文章详情
- **请求方式**: `GET`
- **请求路径**: `/business/dailyassistant/article/{articleId}`
- **权限要求**: `daily:article:query`
- **路径参数**: 
  - `articleId`: Long，文章ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定文章详细信息

##### 创建文章
- **请求方式**: `POST`
- **请求路径**: `/business/dailyassistant/article`
- **权限要求**: `daily:article:add`
- **请求体**: `DailyArticle`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 创建新文章

##### 更新文章
- **请求方式**: `PUT`
- **请求路径**: `/business/dailyassistant/article`
- **权限要求**: `daily:article:edit`
- **请求体**: `DailyArticle`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新文章信息

##### 删除文章
- **请求方式**: `DELETE`
- **请求路径**: `/business/dailyassistant/article/{articleIds}`
- **权限要求**: `daily:article:remove`
- **路径参数**: 
  - `articleIds`: Long[]，文章ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定文章

#### 元器智能体配置管理

##### 获取智能体配置列表
- **请求方式**: `GET`
- **请求路径**: `/business/dailyassistant/yuanqi-agent-config/list`
- **权限要求**: `yuanqi:config:list`
- **请求参数**: 
  - `YuanqiAgentConfig`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询元器智能体配置列表

##### 获取智能体配置详情
- **请求方式**: `GET`
- **请求路径**: `/business/dailyassistant/yuanqi-agent-config/{configId}`
- **权限要求**: `yuanqi:config:query`
- **路径参数**: 
  - `configId`: Long，配置ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定智能体配置详细信息

##### 保存智能体配置
- **请求方式**: `POST`
- **请求路径**: `/business/dailyassistant/yuanqi-agent-config`
- **权限要求**: `yuanqi:config:add`
- **请求体**: `YuanqiAgentConfig`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 保存智能体配置信息

##### 更新智能体配置
- **请求方式**: `PUT`
- **请求路径**: `/business/dailyassistant/yuanqi-agent-config`
- **权限要求**: `yuanqi:config:edit`
- **请求体**: `YuanqiAgentConfig`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新智能体配置信息

---

### 2.4 文档解析接口 (`/business/documentparse`)

#### 文档解析

##### 解析文档
- **请求方式**: `POST`
- **请求路径**: `/business/documentparse/parse`
- **权限要求**: `document:parse:create`
- **请求参数**: 
  - `file`: Multipart文件
  - `options`: 解析选项对象
- **返回类型**: `AjaxResult`
- **功能说明**: 解析上传的文档内容

##### 获取解析结果
- **请求方式**: `GET`
- **请求路径**: `/business/documentparse/result/{taskId}`
- **权限要求**: `document:parse:query`
- **路径参数**: 
  - `taskId`: Long，解析任务ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取文档解析任务结果

##### 获取解析历史
- **请求方式**: `GET`
- **请求路径**: `/business/documentparse/history`
- **权限要求**: `document:parse:list`
- **请求参数**: 
  - `DocumentParseHistory`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询文档解析历史记录

---

### 2.5 Gitee相关接口 (`/business/gitee`)

#### Gitee用户分析

##### 获取Gitee用户信息
- **请求方式**: `GET`
- **请求路径**: `/business/gitee/profile`
- **权限要求**: 无（需要Gitee授权）
- **返回类型**: `AjaxResult`
- **功能说明**: 获取当前授权用户的Gitee信息

##### Gitee用户分析
- **请求方式**: `GET`
- **请求路径**: `/business/gitee/analysis`
- **权限要求**: 无（需要Gitee授权）
- **返回类型**: `AjaxResult`
- **功能说明**: 分析Gitee用户的参与情况和能力

#### Gitee登录

##### Gitee登录
- **请求方式**: `GET`
- **请求路径**: `/business/gitee/login`
- **返回类型**: `AjaxResult`
- **功能说明**: 发起Gitee OAuth登录流程

##### Gitee登录回调
- **请求方式**: `GET`
- **请求路径**: `/business/gitee/callback`
- **请求参数**: 
  - `code`: String，OAuth授权码
  - `state`: String，状态参数
- **返回类型**: `AjaxResult`
- **功能说明**: 处理Gitee OAuth回调

---

### 2.6 积分管理接口 (`/business/point`)

#### 积分规则管理

##### 获取积分规则列表
- **请求方式**: `GET`
- **请求路径**: `/business/point/rule/list`
- **权限要求**: `point:rule:list`
- **请求参数**: 
  - `PointsRule`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询积分规则列表

##### 获取积分规则详情
- **请求方式**: `GET`
- **请求路径**: `/business/point/rule/{ruleId}`
- **权限要求**: `point:rule:query`
- **路径参数**: 
  - `ruleId`: Long，规则ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定积分规则详细信息

##### 新增积分规则
- **请求方式**: `POST`
- **请求路径**: `/business/point/rule`
- **权限要求**: `point:rule:add`
- **请求体**: `PointsRule`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 新增积分规则

##### 修改积分规则
- **请求方式**: `PUT`
- **请求路径**: `/business/point/rule`
- **权限要求**: `point:rule:edit`
- **请求体**: `PointsRule`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新积分规则

##### 删除积分规则
- **请求方式**: `DELETE`
- **请求路径**: `/business/point/rule/{ruleIds}`
- **权限要求**: `point:rule:remove`
- **路径参数**: 
  - `ruleIds`: Long[]，规则ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定积分规则

#### 积分管理

##### 获取积分列表
- **请求方式**: `GET`
- **请求路径**: `/business/point/list`
- **权限要求**: `point:points:list`
- **请求参数**: 
  - `Points`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询积分记录列表

##### 获取用户积分余额
- **请求方式**: `GET`
- **请求路径**: `/business/point/balance/{userId}`
- **权限要求**: `point:points:query`
- **路径参数**: 
  - `userId`: Long，用户ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定用户积分余额

##### 积分变动
- **请求方式**: `POST`
- **请求路径**: `/business/point/change`
- **权限要求**: `point:points:edit`
- **请求体**: `{userId: Long, amount: Long, reason: String, type: String}`
- **返回类型**: `AjaxResult`
- **功能说明**: 执行积分变动操作

#### 积分粉丝管理

##### 获取积分粉丝列表
- **请求方式**: `GET`
- **请求路径**: `/business/point/fans/list`
- **权限要求**: `point:fans:list`
- **请求参数**: 
  - `PointsFans`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询积分粉丝列表

---

### 2.7 公众号管理接口 (`/business/officialaccount`)

#### 公众号管理

##### 获取公众号列表
- **请求方式**: `GET`
- **请求路径**: `/business/officialaccount/list`
- **权限要求**: `official:account:list`
- **请求参数**: 
  - `WcOfficeAccount`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询公众号列表

##### 获取公众号详情
- **请求方式**: `GET`
- **请求路径**: `/business/officialaccount/{accountId}`
- **权限要求**: `official:account:query`
- **路径参数**: 
  - `accountId`: Long，公众号ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定公众号详细信息

##### 新增公众号
- **请求方式**: `POST`
- **请求路径**: `/business/officialaccount`
- **权限要求**: `official:account:add`
- **请求体**: `WcOfficeAccount`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 新增公众号配置

##### 修改公众号
- **请求方式**: `PUT`
- **请求路径**: `/business/officialaccount`
- **权限要求**: `official:account:edit`
- **请求体**: `WcOfficeAccount`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 更新公众号配置

#### 草稿发布记录

##### 获取发布记录列表
- **请求方式**: `GET`
- **请求路径**: `/business/officialaccount/publish-record/list`
- **权限要求**: `official:publish:list`
- **请求参数**: 
  - `WcOfficePublishRecord`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询公众号发布记录

---

### 2.8 WebSocket/Engine管理接口 (`/business/websocket`)

#### 引擎管理

##### 获取引擎节点列表
- **请求方式**: `GET`
- **请求路径**: `/business/websocket/engine/list`
- **权限要求**: `websocket:engine:list`
- **请求参数**: 
  - `EngineNode`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询引擎节点列表

##### 获取引擎节点详情
- **请求方式**: `GET`
- **请求路径**: `/business/websocket/engine/{engineId}`
- **权限要求**: `websocket:engine:query`
- **路径参数**: 
  - `engineId`: String，引擎ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定引擎节点详细信息

#### 连接日志管理

##### 获取连接日志列表
- **请求方式**: `GET`
- **请求路径**: `/business/websocket/connection-log/list`
- **权限要求**: `websocket:log:list`
- **请求参数**: 
  - `ConnectionLog`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询连接日志列表

#### 主机白名单管理

##### 获取主机白名单列表
- **请求方式**: `GET`
- **请求路径**: `/business/websocket/host-whitelist/list`
- **权限要求**: `websocket:host:list`
- **请求参数**: 
  - `HostWhitelist`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询主机白名单列表

##### 添加主机到白名单
- **请求方式**: `POST`
- **请求路径**: `/business/websocket/host-whitelist`
- **权限要求**: `websocket:host:add`
- **请求体**: `HostWhitelist`对象
- **返回类型**: `AjaxResult`
- **功能说明**: 添加主机到白名单

#### IP黑名单管理

##### 获取IP黑名单列表
- **请求方式**: `GET`
- **请求路径**: `/business/websocket/ip-blacklist/list`
- **权限要求**: `websocket:ip:list`
- **请求参数**: 
  - `IpBlacklist`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询IP黑名单列表

---

## 三、监控管理模块

### 3.1 缓存监控接口 (`/monitor/cache`)

#### 获取缓存信息
- **请求方式**: `GET`
- **请求路径**: `/monitor/cache`
- **权限要求**: `monitor:cache:view`
- **返回类型**: `AjaxResult`
- **功能说明**: 获取系统缓存统计信息

#### 获取缓存列表
- **请求方式**: `GET`
- **请求路径**: `/monitor/cache/getNames`
- **权限要求**: `monitor:cache:view`
- **返回类型**: `AjaxResult`
- **功能说明**: 获取缓存名称列表

#### 获取缓存键名列表
- **请求方式**: `GET`
- **请求路径**: `/monitor/cache/getKeys/{cacheName}`
- **权限要求**: `monitor:cache:view`
- **路径参数**: 
  - `cacheName`: String，缓存名称
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定缓存的键名列表

#### 获取缓存值
- **请求方式**: `GET`
- **请求路径**: `/monitor/cache/getValue/{cacheName}/{cacheKey}`
- **权限要求**: `monitor:cache:view`
- **路径参数**: 
  - `cacheName`: String，缓存名称
  - `cacheKey`: String，缓存键名
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定缓存键的值

#### 清理缓存
- **请求方式**: `DELETE`
- **请求路径**: `/monitor/cache/clearCacheName/{cacheName}`
- **权限要求**: `monitor:cache:remove`
- **路径参数**: 
  - `cacheName`: String，缓存名称
- **返回类型**: `AjaxResult`
- **功能说明**: 清理指定缓存

#### 清理缓存键
- **请求方式**: `DELETE`
- **请求路径**: `/monitor/cache/clearCacheKey/{cacheKey}`
- **权限要求**: `monitor:cache:remove`
- **路径参数**: 
  - `cacheKey`: String，缓存键名
- **返回类型**: `AjaxResult`
- **功能说明**: 清理指定缓存键

#### 清理全部缓存
- **请求方式**: `DELETE`
- **请求路径**: `/monitor/cache/clearAll`
- **权限要求**: `monitor:cache:remove`
- **返回类型**: `AjaxResult`
- **功能说明**: 清理所有缓存

---

### 3.2 服务器监控接口 (`/monitor/server`)

#### 获取服务器信息
- **请求方式**: `GET`
- **请求路径**: `/monitor/server`
- **权限要求**: `monitor:server:view`
- **返回类型**: `AjaxResult`
- **功能说明**: 获取服务器系统信息、CPU、内存、JVM、磁盘等信息

---

### 3.3 登录日志接口 (`/monitor/logininfor`)

#### 获取登录日志列表
- **请求方式**: `GET`
- **请求路径**: `/monitor/logininfor/list`
- **权限要求**: `monitor:logininfor:list`
- **请求参数**: 
  - `SysLogininfor`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询登录日志列表

#### 删除登录日志
- **请求方式**: `DELETE`
- **请求路径**: `/monitor/logininfor/{infoIds}`
- **权限要求**: `monitor:logininfor:remove`
- **路径参数**: 
  - `infoIds`: Long[]，登录日志ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定登录日志

#### 清空登录日志
- **请求方式**: `DELETE`
- **请求路径**: `/monitor/logininfor/all`
- **权限要求**: `monitor:logininfor:remove`
- **返回类型**: `AjaxResult`
- **功能说明**: 清空所有登录日志

#### 解锁账户
- **请求方式**: `POST`
- **请求路径**: `/monitor/logininfor/unlock/{userName}`
- **权限要求**: `monitor:logininfor:unlock`
- **路径参数**: 
  - `userName`: String，用户名
- **返回类型**: `AjaxResult`
- **功能说明**: 解锁被锁定的用户账户

---

### 3.4 操作日志接口 (`/monitor/operlog`)

#### 获取操作日志列表
- **请求方式**: `GET`
- **请求路径**: `/monitor/operlog/list`
- **权限要求**: `monitor:operlog:list`
- **请求参数**: 
  - `SysOperLog`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询操作日志列表

#### 获取操作日志详情
- **请求方式**: `GET`
- **请求路径**: `/monitor/operlog/{operId}`
- **权限要求**: `monitor:operlog:query`
- **路径参数**: 
  - `operId`: Long，操作日志ID
- **返回类型**: `AjaxResult`
- **功能说明**: 获取指定操作日志详细信息

#### 删除操作日志
- **请求方式**: `DELETE`
- **请求路径**: `/monitor/operlog/{operIds}`
- **权限要求**: `monitor:operlog:remove`
- **路径参数**: 
  - `operIds`: Long[]，操作日志ID数组
- **返回类型**: `AjaxResult`
- **功能说明**: 删除指定操作日志

#### 清空操作日志
- **请求方式**: `DELETE`
- **请求路径**: `/monitor/operlog/all`
- **权限要求**: `monitor:operlog:remove`
- **返回类型**: `AjaxResult`
- **功能说明**: 清空所有操作日志

#### 导出操作日志
- **请求方式**: `POST`
- **请求路径**: `/monitor/operlog/export`
- **权限要求**: `monitor:operlog:export`
- **请求参数**: 
  - `SysOperLog`对象（可选查询条件）
- **返回类型**: `void`（Excel文件下载）
- **功能说明**: 导出操作日志为Excel文件

---

### 3.5 在线用户接口 (`/monitor/online`)

#### 获取在线用户列表
- **请求方式**: `GET`
- **请求路径**: `/monitor/online/list`
- **权限要求**: `monitor:online:list`
- **请求参数**: 
  - `SysUserOnline`对象（可选查询条件）
- **返回类型**: `TableDataInfo`
- **功能说明**: 分页查询在线用户列表

#### 强制退出在线用户
- **请求方式**: `DELETE`
- **请求路径**: `/monitor/online/{tokenId}`
- **权限要求**: `monitor:online:forceLogout`
- **路径参数**: 
  - `tokenId`: String，在线用户令牌
- **返回类型**: `AjaxResult`
- **功能说明**: 强制退出指定在线用户

---

## 四、通用接口模块

### 4.1 通用接口 (`/common`)

#### 上传文件
- **请求方式**: `POST`
- **请求路径**: `/common/upload`
- **请求参数**: 
  - `file`: Multipart文件
- **返回类型**: `AjaxResult`
- **功能说明**: 上传文件到服务器

#### 下载文件
- **请求方式**: `GET`
- **请求路径**: `/common/download`
- **请求参数**: 
  - `fileName`: String，文件名
  - `delete`: boolean，下载后是否删除
- **返回类型**: `void`（文件下载）
- **功能说明**: 下载服务器上的文件

#### 下载文件（通过路径）
- **请求方式**: `GET`
- **请求路径**: `/common/download/resource`
- **请求参数**: 
  - `resource`: String，资源路径
- **返回类型**: `void`（文件下载）
- **功能说明**: 通过资源路径下载文件

---

### 4.2 验证码接口 (`/captchaImage`)

#### 获取验证码
- **请求方式**: `GET`
- **请求路径**: `/captchaImage`
- **返回类型**: `AjaxResult`
- **功能说明**: 获取验证码图片和UUID

---

## 五、工具模块

### 5.1 测试接口 (`/test/user`)

#### 获取用户列表
- **请求方式**: `GET`
- **请求路径**: `/test/user/list`
- **返回类型**: `R<List<UserEntity>>`
- **功能说明**: 获取测试用户列表

#### 获取用户详情
- **请求方式**: `GET`
- **请求路径**: `/test/user/{userId}`
- **路径参数**: 
  - `userId`: Integer，用户ID
- **返回类型**: `R<UserEntity>`
- **功能说明**: 获取指定测试用户详情

#### 保存用户
- **请求方式**: `POST`
- **请求路径**: `/test/user/save`
- **请求体**: `UserEntity`对象
- **返回类型**: `R<String>`
- **功能说明**: 保存测试用户

#### 更新用户
- **请求方式**: `PUT`
- **请求路径**: `/test/user/update`
- **请求体**: `UserEntity`对象
- **返回类型**: `R<String>`
- **功能说明**: 更新测试用户

#### 删除用户
- **请求方式**: `DELETE`
- **请求路径**: `/test/user/{userId}`
- **路径参数**: 
  - `userId`: Integer，用户ID
- **返回类型**: `R<String>`
- **功能说明**: 删除测试用户

---

## 六、认证与授权

### 6.1 认证头
- 所有需要认证的接口都需要在请求头中包含：
  ```
  Authorization: Bearer {token}
  ```

### 6.2 权限控制
- 通过`@PreAuthorize`注解控制接口访问权限
- 权限格式：`module:action:operation`
  - 例如：`system:user:list`表示系统模块的用户列表权限

### 6.3 返回格式
- 成功响应：`AjaxResult.success(data)`
- 失败响应：`AjaxResult.error(message)`
- 分页响应：`TableDataInfo`（包含total, rows, code, msg）

---

## 七、错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 501 | 不支持的方法 |
| 502 | 网关错误 |
| 503 | 服务不可用 |

---

## 八、使用说明

1. **获取访问令牌**：首先调用登录接口获取访问令牌
2. **设置请求头**：在需要认证的请求中设置Authorization头部
3. **权限检查**：确保当前用户具有访问接口所需的权限
4. **参数验证**：按照接口文档要求提供正确的参数
5. **错误处理**：根据返回的错误码进行相应的错误处理

**最后更新**: 2026-01-30