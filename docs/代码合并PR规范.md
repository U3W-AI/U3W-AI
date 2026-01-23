# 📋 代码合并PR规范

> **目标读者**: 所有需要提交代码和发起Pull Request的开发者  
> **文档用途**: 规范Fork仓库、分支管理、Commit格式、PR提交流程  
> **更新日期**: 2025-12-28

---

## 📋 目录
- [代码提交位置规范](#-代码提交位置规范) - **⭐⭐⭐ 必读：强制规则**
- [Fork与分支管理](#-fork与分支管理)
- [代码提交规范](#-代码提交规范)
- [PR提交规范](#-pr提交规范)
- [数据库变更规范](#-数据库变更规范)
- [代码审查清单](#-代码审查清单)

---

## 📏 代码提交位置规范
（⭐⭐⭐ 必读）
### 1. 后端代码位置

**强制要求**：
```
WxFbsir-business/src/main/java/com/wx/fbsir/business/
```

- ✅ **所有业务代码必须放在 `WxFbsir-business` 模块下**
- ❌ **未经管理员允许，不得在其他位置存放业务代码**

### 2. 前端代码位置

**强制要求**（三级目录对应数据库菜单层级）：

#### 页面位置：
```
WxFbsir-ui/src/views/business/[二级目录]/[三级目录]/

示例：
WxFbsir-ui/src/views/business/content/dailyassistant/
└── business/     # 一级目录（对应一级菜单）
    └── content/  # 二级目录（对应二级菜单：内容管理）
        └── dailyassistant/  # 三级目录（对应三级菜单：日更助手）
```

#### API位置：
```
WxFbsir-ui/src/api/business/[二级目录]/[文件名].js

示例：
WxFbsir-ui/src/api/business/content/dailyassistant.js
└── business/     # 一级目录
    └── content/  # 二级目录（与页面目录对应）
        └── dailyassistant.js  # API文件（与页面目录名对应）
```

**重要规则**：
- ✅ **所有业务页面必须放在 `views/business/` 下的某个二级目录中**
- ✅ **所有业务API必须放在 `api/business/` 下的对应目录中**
- ✅ **目录层级必须与数据库菜单表层级一一对应**
- ✅ API文件名与页面目录名保持一致
- ❌ **未经管理员允许，不得在其他位置存放业务代码**

### 3. 为什么必须遵守目录规范？

1. **模块独立**：各模块代码独立，便于维护和解耦
2. **权限对应**：目录层级与菜单权限一一对应
3. **团队协作**：统一的目录结构降低沟通成本
4. **代码审查**：便于Code Review时快速定位代码位置

### 4. 特殊情况处理

如果你的功能确实不适合放在 `business` 目录下：
1. **必须先与管理员沟通说明原因**
2. 获得批准后在PR中明确注明
3. 更新相关文档说明特殊目录的用途

---

## 🔀 Fork与分支管理

### 分支管理流程

### 1. Fork主仓库

1. 访问主仓库：`https://gitee.com/U3W-AI/U3W-AI`
2. 点击右上角"Fork"按钮，将项目Fork到自己的账号下
3. Clone自己Fork的仓库到本地：
```bash
git clone https://gitee.com/U3W-AI/U3W-AI.git
cd U3W-AI
```

### 2. 添加上游仓库

```bash
# 添加主仓库为上游仓库
git remote add upstream https://gitee.com/U3W-AI/U3W-AI.git

# 查看远程仓库
git remote -v
# origin    https://gitee.com/your-username/U3W-AI.git (fetch)
# origin    https://gitee.com/your-username/U3W-AI.git (push)
# upstream  https://gitee.com/U3W-AI/U3W-AI.git (fetch)
# upstream  https://gitee.com/U3W-AI/U3W-AI.git (push)
```

### 3. 同步主仓库更新

在开始新功能开发前，先同步主仓库的最新代码
(也可以直接在gitee打开自己的fork仓库中点击刷新）：

```bash
# 切换到福帮手分支
git checkout fbsir

# 拉取上游仓库更新
git fetch upstream

# 合并上游更新
git merge upstream/fbsir

# 推送到自己的远程仓库
git push origin fbsir
```

### 4. 创建功能分支

```bash
    # 从最新的主分支创建功能分支
    git checkout -b feature/your-feature-name
    
    # 或创建修复分支
    git checkout -b fix/bug-description
```

**分支命名规范**：
- 功能分支：`feature/功能名称`（如：`feature/daily-assistant`）
- 修复分支：`fix/问题描述`（如：`fix/article-query-bug`）
- 紧急修复：`hotfix/问题描述`（如：`hotfix/login-error`）

---

## 📝 代码提交规范

### 提交消息格式

### 1. 提交消息格式

```
<类型>: <简洁说明> (YYYY-MM-DD)

```

### 2. 类型说明

- `feat`: 新功能
- `fix`: 修复bug
- `docs`: 文档更新
- `style`: 代码格式调整（不影响功能）
- `refactor`: 重构（不是新功能也不是修复bug）
- `perf`: 性能优化
- `test`: 测试相关
- `chore`: 构建过程或辅助工具的变动

### 3. 提交示例

```bash
# ✅ 正确示例
feat: 添加日更助手文章创建功能 (2025-12-05)

fix: 修复文章查询接口分页错误 (2025-12-05)

docs: 更新部署文档添加元器配置说明 (2025-12-05)

refactor: 优化文章生成异步处理逻辑 (2025-12-05)

# ❌ 错误示例
update code
修改了一些东西
fix bug
2025-12-05 更新
```

### 4. 提交注意事项

**必须遵守**：
- ✅ 简洁说明控制在10个字左右
- ✅ 必须包含日期（格式：YYYY-MM-DD）
- ✅ 使用中文描述
- ✅ 一次PR只提交一次分支仓库
- ❌ 不要提交本地运行文件（如：`target/`、`node_modules/`、`.idea/`等）
- ❌ 不要提交缓存文件（如：`.DS_Store`、`Thumbs.db`等）
- ❌ 不要提交配置文件中的敏感信息（如：数据库密码、API密钥等）

### 5. 提交前检查

```bash
# 查看修改的文件
git status

# 查看具体修改内容
git diff

# 添加文件到暂存区
git add <file>

# 提交
git commit -m "feat: 添加日更助手文章创建功能 (2025-12-05)"

# 推送到自己的远程仓库
git push origin feature/your-feature-name
```

---

## 📏 代码提交位置规范（⭐⭐⭐ 必读）

### 1. 后端代码位置

**强制要求**：
```
WxFbsir-business/src/main/java/com/wx/fbsir/business/
```

- ✅ **所有业务代码必须放在 `WxFbsir-business` 模块下**
- ❌ **未经管理员允许，不得在其他位置存放业务代码**

### 2. 前端代码位置

**强制要求**（三级目录对应数据库菜单层级）：

#### 页面位置：
```
WxFbsir-ui/src/views/business/[二级目录]/[三级目录]/

示例：
WxFbsir-ui/src/views/business/content/dailyassistant/
└── business/     # 一级目录（对应一级菜单）
    └── content/  # 二级目录（对应二级菜单：内容管理）
        └── dailyassistant/  # 三级目录（对应三级菜单：日更助手）
```

#### API位置：
```
WxFbsir-ui/src/api/business/[二级目录]/[文件名].js

示例：
WxFbsir-ui/src/api/business/content/dailyassistant.js
└── business/     # 一级目录
    └── content/  # 二级目录（与页面目录对应）
        └── dailyassistant.js  # API文件（与页面目录名对应）
```

**重要规则**：
- ✅ **所有业务页面必须放在 `views/business/` 下的某个二级目录中**
- ✅ **所有业务API必须放在 `api/business/` 下的对应目录中**
- ✅ **目录层级必须与数据库菜单表层级一一对应**
- ✅ API文件名与页面目录名保持一致
- ❌ **未经管理员允许，不得在其他位置存放业务代码**

### 3. 为什么必须遵守目录规范？

1. **模块独立**：各模块代码独立，便于维护和解耦
2. **权限对应**：目录层级与菜单权限一一对应
3. **团队协作**：统一的目录结构降低沟通成本
4. **代码审查**：便于Code Review时快速定位代码位置

### 4. 特殊情况处理

如果你的功能确实不适合放在 `business` 目录下：
1. **必须先与管理员沟通说明原因**
2. 获得批准后在PR中明确注明
3. 更新相关文档说明特殊目录的用途

---

## 🔄 PR提交规范

### Pull Request 提交规范

### 1. 创建Pull Request

1. 推送代码到自己的远程仓库后，访问主仓库页面
2. 点击"Pull Requests" → "新建Pull Request"
3. 选择源分支（你的分支）和目标分支（主仓库的主分支）
4. 填写PR标题和描述

### 2. PR标题格式

```
<类型>: <简洁说明> (YYYY-MM-DD)
```

示例：
```
feat: 添加日更助手文章创建功能 (2025-12-05)
fix: 修复文章查询接口分页错误 (2025-12-05)
docs: 更新部署文档添加元器配置说明 (2025-12-05)
```

### 3. PR描述模板

```markdown
## 变更类型
- [ ] 新功能 (feat)
- [ ] Bug修复 (fix)
- [ ] 文档更新 (docs)
- [ ] 代码重构 (refactor)
- [ ] 性能优化 (perf)
- [ ] 其他 (chore)

## 变更说明
简要描述本次PR的主要变更内容

## 变更文件
- 后端：`WxFbsir-business/src/main/java/...`
- 前端：`WxFbsir-ui/src/views/business/...`
- 数据库：`sql/updates/update_YYYYMMDD.sql`（如有）

## 测试情况
- [ ] 本地测试通过
- [ ] 功能正常运行
- [ ] 无明显bug

## 相关Issue
关联的Issue编号（如有）：#123

## 备注
其他需要说明的内容
```

### 4. PR注意事项

**重要规范**：
- ✅ 一次PR只提交一次分支代码
- ✅ PR标题必须包含类型和日期
- ✅ PR描述简单说明变更内容
- ✅ 确保代码提交后再次拉到本地测试
- ✅ 确保没有提交不必要的文件
- ❌ 不要在PR中包含多个不相关的功能
- ❌ 不要提交未完成的代码
- ❌ 不要在PR描述中署名（Gitee会自动记录提交者）

---

## 🗄️ 数据库变更规范

### 1. 数据库变更原则

**重要**：如果你的代码涉及数据库变更（新建表、修改表结构、添加字段等），必须遵循以下规范：

### 2. 创建数据库更新SQL文件

在 `sql/updates/` 目录下创建新的SQL文件：

```bash
# 文件命名格式：update_YYYYMMDD_功能描述.sql
sql/updates/update_20251205_daily_article.sql
```

### 3. SQL文件内容规范

```sql
-- ============================================
-- 数据库更新脚本
-- 功能：添加日更助手文章表
-- 作者：系统自动记录
-- 日期：2025-12-05
-- ============================================

-- 1. 创建新表
CREATE TABLE IF NOT EXISTS `daily_article` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文章ID',
  `article_title` VARCHAR(500) NOT NULL COMMENT '文章标题',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `process_status` TINYINT(1) DEFAULT 0 COMMENT '处理状态：0-处理中，1-已完成，2-失败',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建者',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新者',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日更助手文章表';

-- 2. 添加字段（如果是修改现有表）
-- ALTER TABLE `daily_article` ADD COLUMN `new_field` VARCHAR(100) DEFAULT NULL COMMENT '新字段';

-- 3. 修改字段（如果需要）
-- ALTER TABLE `daily_article` MODIFY COLUMN `article_title` VARCHAR(1000) NOT NULL COMMENT '文章标题';

-- 4. 添加索引（如果需要）
-- ALTER TABLE `daily_article` ADD INDEX `idx_process_status` (`process_status`);

-- 5. 插入初始数据（如果需要）
-- INSERT INTO `sys_menu` VALUES (...);
```

### 4. 数据库变更审查

**管理员职责**：
1. 审查SQL文件的正确性
2. 确认SQL文件命名规范
3. 在测试环境执行SQL验证
4. 合并PR后在生产环境执行SQL
5. 记录数据库变更日志

**好处**：
- ✅ 直观看到数据库变更内容
- ✅ 方便回滚和版本管理
- ✅ 降低线上数据库更新成本
- ✅ 避免手动执行SQL出错

---

## ✅ 代码审查清单

### 提交PR前必须检查

#### 代码质量
- [ ] 代码符合命名规范
- [ ] 添加了必要的注释（类注释、方法注释、复杂逻辑注释）
- [ ] 异常处理完善
- [ ] 日志记录合理
- [ ] 没有调试代码（`console.log`、`System.out.println`）
- [ ] 没有敏感信息（密码、密钥）

#### 模块划分（⭐⭐⭐ 必检查）
- [ ] 后端业务代码放在 `WxFbsir-business/src/main/java/com/wx/fbsir/business/`
- [ ] 前端页面放在 `WxFbsir-ui/src/views/business/[二级目录]/[三级目录]/`
- [ ] 前端API放在 `WxFbsir-ui/src/api/business/[二级目录]/[文件名].js`
- [ ] 目录层级与数据库菜单表层级一致
- [ ] API文件名与页面目录名保持一致
- [ ] 没有在未经允许的位置存放代码

#### 文件提交
- [ ] 没有提交本地运行文件（`target/`、`node_modules/`、`.idea/`）
- [ ] 没有提交缓存文件（`.DS_Store`、`Thumbs.db`）
- [ ] 没有提交配置文件中的敏感信息
- [ ] 没有提交个人配置文件（`application-dev.yml`等）

#### 数据库变更
- [ ] 如有数据库变更，已创建SQL更新文件
- [ ] SQL文件命名符合规范：`update_YYYYMMDD_功能描述.sql`
- [ ] SQL文件包含完整的注释说明
- [ ] 在PR描述中说明了数据库变更

#### 测试验证
- [ ] 本地测试通过
- [ ] 功能正常运行
- [ ] 无明显bug
- [ ] 不影响现有功能

#### PR规范
- [ ] PR标题符合格式：`<类型>: <简洁说明> (YYYY-MM-DD)`
- [ ] PR描述清晰完整
- [ ] 没有在PR中署名（系统会自动记录）
- [ ] 一次PR只包含一次提交记录


---

## 📚 参考资料

- [Git工作流程](https://www.atlassian.com/git/tutorials/comparing-workflows)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [代码规范](./代码规范.md)
- [文档规范总结](./文档规范总结.md)

---

## 💡 常见问题

### Q1: 如何解决合并冲突？
```bash
# 1. 同步主仓库最新代码
git fetch upstream
git merge upstream/fbsir

# 2. 解决冲突
# 手动编辑冲突文件，保留正确的代码

# 3. 标记冲突已解决
git add <冲突文件>
git commit -m "fix: 解决合并冲突 (2025-12-05)"

# 4. 推送到远程
git push origin feature/your-feature-name
```

### Q2: 如何撤销错误的提交？
```bash
# 撤销最后一次提交（保留修改）
git reset --soft HEAD^

# 撤销最后一次提交（不保留修改）
git reset --hard HEAD^

# 修改最后一次提交消息
git commit --amend -m "新的提交消息"
```

### Q3: 如何删除远程分支？
```bash
# 删除本地分支
git branch -d feature/your-feature-name

# 删除远程分支
git push origin --delete feature/your-feature-name
```

---

---

**最后更新**: 2025-12-28 
**维护者**: WxFbsir Team
