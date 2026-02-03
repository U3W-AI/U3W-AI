# ❓ 常见问题 (FAQ)

本文档收集了微信福帮手项目的常见问题和解决方案。

---

## 📋 目录
- [部署相关](#-部署相关)
- [开发相关](#-开发相关)
- [元器工作流相关](#-元器工作流相关)

---

## 🚀 部署相关

### Q1: 前端无法访问后端
**问题描述**：前端页面显示"网络错误"或"无法连接到服务器"

**排查步骤**：
1. 检查后端是否启动：`jps | grep WxFbsir`
2. 检查端口是否监听：`netstat -ano | grep 8080`
3. 检查前端配置：查看 `.env.development` 中的 `VITE_APP_BASE_API`
4. 检查跨域配置（后端已配置CORS，一般不会有问题）

### Q2: 数据库连接失败
**问题描述**：后端启动报错 "Unable to connect to database"

**解决方案**：
1. 检查MySQL服务是否启动
2. 检查用户名密码是否正确
3. 检查数据库名称是否存在
4. 检查 `application-druid.yml` 配置
5. 检查防火墙设置

### Q3: 端口被占用
**问题描述**：启动时提示 "Port already in use"

**解决方案**：
```bash
# 查看端口占用（Windows）
netstat -ano | findstr :8080

# 查看端口占用（macOS/Linux）
lsof -i :8080

# 杀死进程
kill -9 <PID>

# 或修改端口
# 后端：修改 application.yml 中的 server.port
# 前端：修改 vite.config.js 中的 server.port
```

### Q4: 前端打包后白屏
**问题描述**：生产环境部署后，访问页面显示白屏

**解决方案**：
1. 检查 `vite.config.js` 中的 `base` 配置
2. 检查 Nginx 的 `try_files` 配置
3. 清除浏览器缓存
4. 查看浏览器控制台错误信息

---

## 💻 开发相关

### Q5: Maven 依赖下载慢
**问题描述**：Maven 下载依赖非常慢或失败

**解决方案**：
配置国内镜像源，编辑 `~/.m2/settings.xml`：
```xml
<mirrors>
    <mirror>
        <id>aliyun</id>
        <mirrorOf>central</mirrorOf>
        <name>Aliyun Maven</name>
        <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
</mirrors>
```

### Q6: npm 安装依赖失败
**问题描述**：`npm install` 报错或很慢

**解决方案**：
```bash
# 使用国内镜像
npm config set registry https://registry.npmmirror.com

# 或使用 pnpm（推荐）
npm install -g pnpm
pnpm install

# 清除缓存
npm cache clean --force
```

### Q7: IDEA 无法识别 Mapper 接口
**问题描述**：Mapper 接口标红，提示找不到对应的 XML

**解决方案**：
1. 安装 MyBatis 插件（MyBatisX 或 Free MyBatis Plugin）
2. 检查 XML 文件路径是否正确
3. 确认 `application.yml` 中的 mapper 配置：
```yaml
mybatis:
  mapper-locations: classpath*:mapper/**/*Mapper.xml
```

### Q8: 前端修改代码不生效
**问题描述**：修改 Vue 文件后，浏览器没有更新

**解决方案**：
```bash
# 1. 清除缓存
rm -rf node_modules/.vite

# 2. 重启开发服务器
npm run dev

# 3. 清除浏览器缓存（Ctrl+Shift+R 强制刷新）
```

---

## 🔄 元器工作流相关

### Q9: 工作流回调失败
**问题描述**：文章一直显示"处理中"，后端没有收到回调

**排查步骤**：
1. 检查内网穿透是否正常：
```bash
curl http://您的公网IP:端口/system/daily-article/saveModelContent
```

2. 检查工作流HTTP节点配置：
   - URL是否正确
   - 是否使用了公网地址
   - 请求方法是否为POST

3. 查看元器工作流执行日志：
   - 登录元器平台
   - 进入智能体 → 工作流 → 执行记录
   - 查看失败原因

4. 检查后端白名单配置：
   - 确认 `SecurityConfig.java` 中已添加回调接口白名单

### Q10: 工作流执行超时
**问题描述**：元器返回504 Gateway Timeout

**说明**：这是正常现象！元器网关超时时间为60秒，但工作流可能需要更长时间执行。即使返回504，工作流仍在后台执行，最终会通过HTTP回调返回结果。

**解决方案**：
- 无需处理，等待回调即可
- 如果长时间（超过5分钟）没有收到回调，检查工作流执行日志

### Q11: 日更助手创建文章后一直显示"处理中"
**问题描述**：创建文章后，状态一直是"处理中"，没有生成内容

**可能原因**：
1. 元器智能体配置错误
2. API 密钥无效
3. 工作流未正确配置回调地址
4. 网络问题导致回调失败

**排查步骤**：
```bash
# 1. 检查后端日志
tail -f logs/wxfbsir.log

# 2. 查看是否有错误信息
grep "ERROR" logs/wxfbsir.log

# 3. 检查数据库记录
SELECT * FROM daily_article WHERE process_status = 0 ORDER BY create_time DESC LIMIT 10;

# 4. 检查元器智能体配置
SELECT * FROM yuanqi_agent_config WHERE is_active = 1;
```

### Q12: 如何配置元器工作流？
**问题描述**：不知道如何配置腾讯元器智能体

**解决方案**：
参考 [部署文档](../部署文档.md) 第三阶段：元器工作流配置，主要步骤：
1. 登录腾讯元器平台
2. 导入工作流文件（`docs/workflows/` 目录）
3. 配置回调地址（使用公网地址）
4. 获取 Agent ID 和 API Key
5. 在系统中配置智能体信息

---

## 📞 仍然无法解决？

如果以上方案无法解决你的问题，请：

1. **查看详细日志**：后端日志 + 浏览器控制台
2. **查看部署文档**：[部署文档](../部署文档.md)
3. **提交 Issue**：附上错误日志和环境信息
4. **查看代码规范**：[代码规范](./代码规范.md)

---

**最后更新**: 2025-12-05
