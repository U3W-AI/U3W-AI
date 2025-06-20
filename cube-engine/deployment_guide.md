## cube-admin 部署指南

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
2. **打包项目**
    - 打包优立方主机端：
    - 进入 cube-engine 目录：`cd cube-engine`
    - 执行打包命令：`mvn clean package -DskipTests`
3. **运行项目**
    - 运行优立方主机端（cube-engine）：`java -jar target/cube-engine.jar`
4. **接口文档**
    - 启动后访问：http://localhost:8083/swagger-ui/index.html
   
