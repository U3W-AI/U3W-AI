<p align="center"><img alt="Static Badge" src="https://img.shields.io/badge/MySQL-5.7-blue"> <img alt="Static Badge" src="https://img.shields.io/badge/JDK-17-blue"> <img alt="Static Badge" src="https://img.shields.io/badge/Spring%20Boot-2.5.15-blue"> <img alt="Static Badge" src="https://img.shields.io/badge/Redis-6.0%2B-blue"> <img alt="Static Badge" src="https://img.shields.io/badge/License-AGPL3.0-blue"></p>

# U3W 优立方 AI 主机：人机协同数智驱动的团队未来
版本：V2.5

文档更新日期：2025年9月22日

## 近期重要更新
2025年9月22日：增强部分智能体的稳定性

2025年9月16日：秘塔及知乎直答 MCP 服务上架，目前支持元宝、豆包、百度 AI、DeepSeek、通义、秘塔和知乎直答的MCP服务，并提供通过公众号智能体调用以上MCP服务进行对话的实例。

2025年9月15日：通义 MCP 服务上架。

2025年9月13日：AI主机底座上线OpenAI API兼容接口，供第三方调用主机能力。

2025年9月1日：升级公众号MCP服务，新增图片生成等能力。

## 项目结构

```
U3W-AI/
├── common_deployment_guide.md  [公共环境部署指南](common_deployment_guide.md)
├── cube-mini/            # 优立方AI主机控制台小程序端
├── cube-admin/           # 优立方AI主机控制台后端
├── cube-ui/              # 优立方AI主机控制台前端
├── cube-engine/          # 优立方AI主机核心服务
├── cube-common/          # 公共工具模块
├── cube-framework/       # 框架核心模块
├── sql/                  # 数据库脚本
└── README.md             # 项目说明文档
```
### 部署文档

**推荐使用：** [🚀 优立方完整部署说明](complete_deployment_guide.md) - **全流程一站式部署指南**


## 快速开始

以下是快速部署和运行优立方的步骤。如需详细了解各模块的部署过程，请参考各模块的部署指南。

### 前置要求
- JDK 17
- Maven cube-admin:2.5.15, cube-engine:3.2.5
- Node.js 16.x/18.x 和 npm 8.x+
- MySQL 5.7+ 和 Redis 6.0+
- Windows 10系统及以上（建议内存16GB）

### 环境准备
1. 安装 JDK 17、Maven、Node.js、MySQL 和 Redis
2. 克隆项目仓库到本地

## 配置

### 数据库配置
1. 创建MySQL数据库：
   ```bash
   mysql -u root -p
   CREATE DATABASE IF NOT EXISTS ucube DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. 导入SQL文件：
   ```bash
   mysql -u root -p ucube < sql/ucube.sql
   ```

3. 添加主机ID到白名单表：
   ```bash
   mysql -u root -p ucube
   INSERT INTO sys_host_whitelist (host_id) VALUES ('你的主机ID');  #主机ID建议使用字母+数字组合，例如user01
   ```

### 后端配置
1. 修改 cube-admin 模块的数据库配置（application-druid.yml）：
   ```yaml
   spring:
       datasource:
           druid:
               master:
                   url: jdbc:mysql://[数据库IP]:[端口]/ucube?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8&allowMultiQueries=true
                   username: [数据库用户名]
                   password: [数据库密码]
   ```

2. 修改 cube-admin 模块的Redis配置（application.yml）：
   ```yaml
   spring:
       redis:
           host: [Redis IP]
           port: [Redis端口]
           password: [Redis密码]
   ```

3. 修改应用配置（application.yml）：
   编辑 `src/main/resources/application.yml` 文件，更新文件上传配置：
   ```yaml
   profile: F:/AGI/chatfile #此处可以是电脑上的任意文件夹
   upload:
   #上传文件路径
       url: http://localhost:8081/profile/
   ```
   > 注意：端口默认为8081，如已修改请使用实际端口

4. 修改文件上传路径配置：
   编辑 `../cube-common/src/main/java/com/cube/common/config/RuoYiConfig.java` 文件最底部，更新上传路径：
   ```java
    public static String getUploadPath()
    {
        return "F:/AGI/chatfile";
    }
   ```
5. 修改日志上传路径配置：
   编辑 `src/main/resources/logback.xml` 文件最底部，更新上传路径：
   ```xml
    <!-- 日志存放路径 -->
	<property name="log.path" value="/你的日志存放路径" />

6. 配置 cube-engine 模块的主机ID和数据目录`../cube-engine/src/main/resources/application.yaml`文件 **（MCP相关配置见[部署文档](cube-engine/deployment_guide.md)）**：
   ```yaml
   cube:
     url: http://127.0.0.1:8081/aigc
     wssurl: ws://127.0.0.1:8081/websocket?clientId=play-您的主机ID  #主机ID建议使用字母+数字组合，例如user01，并在数据库sys_host_whitelist中配置主机id
     datadir: F:\AGI\user-data-dir  # 数据目录，建议单独文件夹存放
     uploadurl: http://127.0.0.1:8081/common/upload
   ```

## 运行

### 启动后端服务
1. 在项目根目录安装所有依赖：
   ```bash
   mvn clean install
   ```

2. 打包启动 cube-admin 服务：
   ```bash
   cd cube-admin
   mvn clean package -DskipTests
   java -jar target/cube-admin.jar
   ```

3. 打包启动 cube-engine 服务：
   ```bash
   cd ../cube-engine
   mvn clean package -DskipTests
   java -jar target/U3W.jar
   ```

### 启动前端服务
1. 进入 cube-ui 目录：
   ```bash
   cd ../cube-ui
   ```

2. 安装前端依赖：
   ```bash
   npm install --legacy-peer-deps
   ```

3. 启动前端开发服务器：
   ```bash
   npm run dev
   ```

### 首次登录
- 启动成功后，浏览器会自动打开后台页面
- 账密登录入口为loginpwd
- 账号：admin
- 密码：admin123

### 主机绑定
- 登录后台后，点击右上角名称→个人中心
- 在基本资料的主机ID输入框中填写 `wssurl` 配置项的 `<主机ID>` 部分

### 验证运行
- 登录后台后，点击登录各个AI，成功返回二维码截图并进行登录。
- 登录完成后，点击左侧内容管理→主机，发送提示词，后台成功返回运行截图、结果后，为cube-ui、cube-admin、cube-engine部分部署完成。
- 参考[部署文档](cube-engine/deployment_guide.md)完成元器工作流相关配置后，发布对话智能体并咨询相关问题，发送以"1"开头的问题（确保意图准确识别）来测试整个系统是否正常工作，如果正常，系统将返回相应的回复链接，以确认部署正常完成。

The end
