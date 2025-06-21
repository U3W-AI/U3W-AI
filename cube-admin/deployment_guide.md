## cube-admin 部署指南

### 前置要求
请先完成 [公共环境部署](../common_deployment_guide.md) 中的所有步骤，包括 JDK、Maven 的安装和项目仓库克隆。

### 环境要求
- Windows 10系统及以上或Linux系统

### 部署步骤
1. 进入cube-admin目录：
   ```bash
   cd cube-admin
   ```
2. 执行打包命令：
   ```bash
   mvn clean package -DskipTests
   ```
3. 运行项目：
   ```bash
   java -jar target/cube-admin.jar
   ```