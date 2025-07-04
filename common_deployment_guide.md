## 公共环境部署指南

### 环境要求
以下是运行项目所需的环境要求：

- **JDK 17**：确保安装JDK 17版本
- **Git**：用于代码版本控制
- **Maven**：用于项目构建和依赖管理
- **MySQL 5.7+**：关系型数据库
- **Redis 6.0+**：缓存服务
- **操作系统**：Windows 10系统及以上或Linux系统
- **占用内存**：建议至少 1GB

### 安装步骤

#### 2.1 安装MySQL 5.7

1. 下载MySQL 5.7安装包：<mcurl name="MySQL官方下载" url="https://dev.mysql.com/downloads/installer/"></mcurl>
2. 运行安装程序，选择"Developer Default"安装类型
3. 按照安装向导完成安装，设置root用户密码（建议设置为`qwe#123`以匹配默认配置）
4. 配置MySQL服务开机自启
5. 验证安装：

```bash
mysql -u root -p
Enter password: 输入设置的密码
```

如果成功进入MySQL命令行界面，则安装成功

#### 2.2 安装Redis 6.0+

1. 下载Redis 6.0或更高版本：<mcurl name="Redis官方下载" url="https://github.com/tporadowski/redis/releases"></mcurl>
2. 解压到指定目录（例如：`C:\Program Files\Redis`）
3. 配置Redis：
   - 编辑`redis.windows.conf`文件
   - 设置密码：找到`requirepass`配置项，修改为`requirepass qwe#123`
   - 设置端口：确保`port 26379`（与application.yml中的配置一致）
4. 安装Redis服务：

```bash
cd C:\Program Files\Redis
redis-server --service-install redis.windows.conf --loglevel verbose
```
5. 启动Redis服务：

```bash
redis-server --service-start
```
6. 验证安装：

```bash
redis-cli -p 26379 -a qwe#123
ping
```

如果返回`PONG`，则表示Redis安装成功

#### 2.3 安装JDK 17

1. 从 [官网](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html) 下载 JDK 17 安装包并完成安装
2. 配置 `JAVA_HOME` 和 `Path` 环境变量
3. 在命令提示符（CMD）中输入以下命令验证安装是否成功：

```bash
java -version
javac -version
```

#### 2.4 安装 Git

1. 下载 [Git 安装包](https://git-scm.com/downloads) 并使用默认设置完成安装
2. 在命令提示符（CMD）中输入以下命令验证安装是否成功：

```bash
git --version
```

#### 2.5 安装 Maven

1. 下载 [Maven 安装包](https://maven.apache.org/download.cgi) 并完成安装
2. 配置 `MAVEN_HOME` 和 `Path` 环境变量
3. 在命令提示符（CMD）中输入以下命令验证安装是否成功：

```bash
mvn -v
```

#### 2.6 克隆项目仓库

在命令提示符（CMD）中输入以下命令拉取项目仓库：

```bash
git clone https://github.com/U3W-AI/U3W-AI.git
```