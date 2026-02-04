# 📊 Gitee用户开源相关能力分析

本文档介绍Gitee用户开源能力分析功能的技术实现，供开发人员快速了解功能架构与代码结构。

---

## 📋 目录

- [功能概述](#-功能概述)
- [数据库设计](#-数据库设计)
- [技术实现](#-技术实现)
  - [授权与绑定流程](#-授权与绑定流程)
  - [评测分析流程](#-评测分析流程)
  - [统计报表](#-统计报表)
- [代码文件结构](#-代码文件结构)

---

## 📖 功能概述

对接Gitee OAuth与评测智能体，自动采集用户开源数据并输出能力分析结果，包含以下功能：

- **Gitee授权绑定** - 账号OAuth授权与绑定关系维护
- **数据采集** - 拉取用户资料、仓库、Issue、通知等信息
- **能力评测** - 调用元器智能体进行评分与建议输出
- **评测存档** - 保存评测报告与能力等级
- **运营统计** - 每日生成模块使用统计报表

---

## 🗄️ 数据库设计

### 1. Gitee绑定表 (gitee_bind)

**表位置：** `sql/gitee.sql`

存储系统用户与Gitee账号的绑定关系，用于授权与账号关联。

**主要字段：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `bind_id` | BIGINT | 绑定ID（主键） |
| `user_id` | BIGINT | 用户ID（唯一） |
| `gitee_user_id` | VARCHAR(64) | Gitee用户ID（唯一） |
| `gitee_username` | VARCHAR(100) | Gitee用户名 |
| `gitee_avatar` | VARCHAR(255) | Gitee头像 |
| `bind_time` | DATETIME | 绑定时间 |

**特点：**
- 用户与Gitee账号一一绑定
- 用户删除时自动清理绑定数据

**Mapper位置：**
- Java接口：`GiteeBindMapper.java`
- XML映射：`GiteeBindMapper.xml`

---

### 2. Gitee评测报告表 (gitee_analysis_report)

记录用户评测结果与评分等级，用于历史留存与后台展示。

**主要字段：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `report_id` | BIGINT | 报告ID（主键） |
| `user_id` | BIGINT | 用户ID |
| `profile_score` | INT | 形象评分 |
| `profile_level` | VARCHAR(10) | 形象等级 |
| `community_score` | INT | 社区评分 |
| `community_level` | VARCHAR(10) | 社区等级 |
| `tech_score` | INT | 技术评分 |
| `tech_level` | VARCHAR(10) | 技术等级 |
| `total_score` | INT | 综合评分 |
| `total_level` | VARCHAR(10) | 综合等级 |
| `report_time` | DATETIME | 评测时间 |

**Mapper位置：**
- Java接口：`GiteeAnalysisReportMapper.java`
- XML映射：`GiteeAnalysisReportMapper.xml`

---

### 3. Gitee使用统计报表 (gitee_usage_report)

记录每日新增绑定、评测次数与评分分布，用于运营分析。

**主要字段：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `report_id` | BIGINT | 报表ID（主键） |
| `report_date` | DATE | 统计日期 |
| `new_bind_count` | INT | 当日新增绑定用户数 |
| `daily_evaluation_count` | INT | 当日评测总次数 |
| `daily_active_user_count` | INT | 当日活跃评测用户数 |
| `total_bind_count` | INT | 累计绑定用户数 |
| `score_distribution` | TEXT | 评分区间分布(JSON) |

**Mapper位置：**
- Java接口：`GiteeUsageReportMapper.java`
- XML映射：`GiteeUsageReportMapper.xml`

---

## 🛠️ 技术实现

### 🔐 授权与绑定流程

1. 前端发起授权请求，后端返回Gitee OAuth授权URL。
2. 用户完成授权后回调到后端，换取access token并获取用户信息。
3. 若已绑定账号，直接登录并保存token；未绑定则生成绑定令牌，引导前端完成绑定/注册。

**核心代码位置：**
- 授权与回调：`WxFbsir-business/src/main/java/com/wx/fbsir/business/gitee/controller/GiteeLoginController.java`
- 个人授权入口：`WxFbsir-business/src/main/java/com/wx/fbsir/business/gitee/controller/GiteeProfileController.java`
- OAuth工具：`WxFbsir-business/src/main/java/com/wx/fbsir/business/gitee/util/GiteeOauthUtil.java`
- 缓存Key：`WxFbsir-business/src/main/java/com/wx/fbsir/business/gitee/util/GiteeCacheKeyUtil.java`

---

### 🧠 评测分析流程

1. **积分校验**：调用积分前置校验规则 `GITEE_ANALYSIS`，不足则拦截。
2. **数据采集**：拉取Gitee用户资料、仓库、Issues、通知等信息。
3. **摘要构建**：对原始数据做统计摘要，控制输入规模。
4. **调用智能体**：向元器智能体发送摘要并获取评测JSON。
5. **解析与存档**：解析评分/等级/建议并保存到评测报告表。

**核心代码位置：**
- 评测入口：`WxFbsir-business/src/main/java/com/wx/fbsir/business/gitee/controller/GiteeAnalysisController.java`
- 评测服务：`WxFbsir-business/src/main/java/com/wx/fbsir/business/gitee/service/GiteeAnalysisService.java`

---

### 📈 统计报表

每日凌晨定时生成前一天报表，同时在列表查询时按需补齐当天报表。

**核心代码位置：**
- 统计服务：`WxFbsir-business/src/main/java/com/wx/fbsir/business/gitee/service/GiteeUsageReportService.java`
- 定时任务：`WxFbsir-business/src/main/java/com/wx/fbsir/business/gitee/service/GiteeUsageReportScheduler.java`
- 管理接口：`WxFbsir-business/src/main/java/com/wx/fbsir/business/gitee/controller/GiteeAdminController.java`

---

## 🗂️ 代码文件结构

```
WxFbsir-business/src/main/java/com/wx/fbsir/business/gitee/
├── controller
│   ├── GiteeLoginController.java        # OAuth登录与绑定
│   ├── GiteeProfileController.java      # 授权状态与数据接口
│   ├── GiteeAnalysisController.java     # 评测入口
│   └── GiteeAdminController.java        # 后台统计管理
├── domain
│   ├── GiteeBind.java
│   ├── GiteeAnalysisReport.java
│   ├── GiteeUsageReport.java
│   └── ...
├── mapper
│   ├── GiteeBindMapper.java
│   ├── GiteeAnalysisReportMapper.java
│   └── GiteeUsageReportMapper.java
├── service
│   ├── GiteeAnalysisService.java
│   ├── GiteeUsageReportService.java
│   ├── GiteeUsageReportScheduler.java
│   └── GiteeTokenService.java
└── util
    ├── GiteeOauthUtil.java
    └── GiteeCacheKeyUtil.java
```
