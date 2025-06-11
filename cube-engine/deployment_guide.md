## 部署环境与步骤
### 环境要求
- JDK 17
- Git
- Maven
- 建议内存：16GB (8GB会有卡顿现象)
1. **安装 JDK 17**
    - 从 [官网](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html) 下载 JDK 17 安装包并完成安装。
    - 配置 `JAVA_HOME` 和 `Path` 环境变量。
    - 在命令提示符（CMD）中输入 `java -version` 和 `javac -version` 验证安装是否成功。
2. **安装 Git**
    - 下载 [Git 安装包](https://git-scm.com/downloads) 并一路点击“下一步”使用默认设置完成安装。
    - 在命令提示符（CMD）中输入 `git --version` 验证安装是否成功。
3. **安装 Maven**
    - 下载 [Maven 安装包](https://maven.apache.org/download.cgi) 并完成安装。
    - 配置 `MAVEN_HOME` 和 `Path` 环境变量。
    - 在命令提示符（CMD）中输入 `mvn -v` 检查安装是否成功。
4. **克隆项目仓库**
    - 在命令提示符（CMD）中输入 `git clone https://github.com/U3W-AI/U3W-AI.git` 拉取项目仓库。
5. **修改配置文件**
    - 克隆仓库到本地后，打开 `src/main/resources/application.yaml` 文件
    - 修改`datadir`地址，此为数据目录，建议单独文件夹存放。例：`datadir: D:\AGI\user-data-dir`
6. **打包项目**
    - 在命令提示符（CMD）中输入 `cd U3W-AI` 进入项目目录。
    - 输入 `mvn clean package -DskipTests` 打包项目为 JAR 文件。
7. **运行项目**
    - 输入 `java -jar target/U3W-AI.jar` 运行项目。
    - 输入 `java -jar target/U3W-AI.jar` 运行项目。
8. **接口文档**
    - 启动后访问：http://localhost:8080/swagger-ui/index.html
   
