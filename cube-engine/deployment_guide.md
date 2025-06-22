## cube-engine 部署指南

## 前置要求
请先完成 [公共环境部署](../common_deployment_guide.md) 中的所有步骤，包括 JDK、Maven 的安装和项目仓库克隆。

### 环境要求
- JDK 17
- Git
- Maven
- Windows 10系统及以上
- 建议内存：16GB (8GB会有卡顿现象)

1. **修改配置文件**
    - 克隆仓库到本地后，打开 `src/main/resources/application.yaml` 文件
    - 修改`datadir`地址，此为数据目录，建议单独文件夹存放。例：`datadir: D:\AGI\user-data-dir`
    - 修改`wssurl`行结尾的`clientId=play-<主机ID>`，其中`<主机ID>`需替换为您的唯一主机标识符（建议使用字母、数字和连字符，如`play-office01`），此ID用于区分不同主机实例，例：
      ```bash
      cube:
      wssurl: ws://127.0.0.1:8083/websocket?clientId=play-office01
      ```
      #### 注意：clientId值中`play-`前缀固定，后续部分可自定义但需保证唯一性
      - 使用字母、数字，如`office01`
      - 不要使用特殊字符，如空格、下划线等
      - 不要使用过长的ID，会影响性能
      - 不要与其他主机实例重复

2. **打包项目**
    - 打包优立方主机端：
    - 进入 cube-engine 目录：
      ```bash
      cd cube-engine
      ```
    - 执行打包命令：
      ```bash
      mvn clean package -DskipTests
      ```
3. **运行项目**
    - 运行优立方主机端（cube-engine）：
      ```bash
      java -jar target/cube-engine.jar
      ```
4. **接口文档**
    - 启动后访问：http://localhost:8083/swagger-ui/index.html
   
