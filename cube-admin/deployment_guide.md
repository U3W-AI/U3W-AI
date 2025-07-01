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
2. 修改主库数据源配置（application-druid.yml）：
   编辑 `src/main/resources/application-druid.yml` 文件，更新主库数据源URL、用户名和密码：
   ```yaml
   spring:
       datasource:
           druid:
               master:
                   url: jdbc:mysql://[自己的数据库IP]:[端口]/ucube?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8&allowMultiQueries=true
                   username: [数据库用户名]
                   password: [数据库密码]
   ```
3. 修改Redis配置（application.yml）：
   编辑 `src/main/resources/application.yml` 文件，更新Redis连接信息：
   ```yaml
   spring:
       redis:
           host: [自己的Redis IP]
           port: [Redis端口]
           password: [Redis密码]
   ```
4. 执行打包命令：
   ```bash
   mvn clean package -DskipTests
   ```
5. 运行项目：
   ```bash
   java -jar target/cube-admin.jar
   ```