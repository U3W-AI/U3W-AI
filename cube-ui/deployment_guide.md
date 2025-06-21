## cube-ui 部署指南

### 前置要求
请先完成 [公共环境部署](../common_deployment_guide.md) 中的所有步骤，包括 JDK、Maven 的安装和项目仓库克隆。

### 环境要求
- Node.js 16.x 或 18.x
- npm 8.x 或以上
- Windows 10系统及以上

### 部署步骤
1. **安装 Node.js**
   - 访问 [Node.js 官网](https://nodejs.org/)
   - 下载对应版本的 Windows 安装包
   - 运行安装程序，勾选 "Add to PATH" 选项
   - 完成安装向导

2. **验证 Node.js安装**
   - 打开命令提示符(CMD)或 PowerShell
   - 执行命令验证 Node.js 版本: `node -v`
   - 确保输出版本号与要求一致

3. **进入项目目录**
   ```bash
   cd cube-ui
   ```

4. **安装项目依赖**
   ```bash
   npm install --legacy-peer-deps
   ```

5. **启动开发服务器**
   ```bash
   npm run dev
   ```

6. **访问后台**
   - 启动成功后，浏览器会自动打开后台页面

7. **登录后台**
   - 若没有配置服务号appId和appSecret，进入后台时的二维码不会出现，此时则使用账密方式登录
   - 账密登录入口为loginpwd
   - 账号：admin
   - 密码：admin123