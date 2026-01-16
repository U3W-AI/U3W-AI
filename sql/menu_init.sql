-- ----------------------------
-- 节点编辑管理和策略管理菜单初始化
-- 用于在主机管理目录下添加"工作流节点编辑"和"策略管理"菜单
-- 同时创建策略参数映射表并初始化数据
-- 
-- 执行说明：
-- 1. 在数据库中执行此SQL脚本
-- 2. 菜单ID使用133（工作流节点编辑）、134（策略管理）
-- 3. 按钮权限ID使用1097（节点编辑查看）、1098-1101（策略管理增删改查）
-- 4. 执行该文件即可完成所有菜单和策略表的初始化
-- ----------------------------

-- ========================================
-- 第一部分：策略参数映射表创建
-- ========================================

-- 策略参数映射表
CREATE TABLE IF NOT EXISTS strategy_param_mapping (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  strategy_name VARCHAR(64) NOT NULL UNIQUE,
  model_name VARCHAR(128) NOT NULL,
  temperature DECIMAL(4,2) NOT NULL,
  top_p DECIMAL(4,2) NOT NULL,
  max_tokens INT NOT NULL,
  prompt TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- 第二部分：菜单配置
-- ========================================

-- 1. 添加工作流节点编辑菜单（parent_id=7 表示属于主机管理目录）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (133, '工作流节点编辑', 7, 5, 'apps', 'business/host/apps/index', '', '', 1, 0, 'C', '0', '0', 'business:host:apps:view', 'component', 'admin', NOW(), '', NULL, '工作流节点编辑工具，支持编辑和发布元器工作流节点')
ON DUPLICATE KEY UPDATE 
  menu_name = '工作流节点编辑',
  parent_id = 7,
  order_num = 5,
  path = 'apps',
  component = 'business/host/apps/index',
  icon = 'component',
  remark = '工作流节点编辑工具，支持编辑和发布元器工作流节点';

-- 为工作流节点编辑菜单添加图标（如果还没有图标）
UPDATE sys_menu SET icon = 'component' WHERE menu_id = 133 AND (icon IS NULL OR icon = '' OR icon = '#');

-- 2. 添加工作流节点编辑按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (1097, '工作流节点编辑查看', 133, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:apps:query', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE menu_name = '工作流节点编辑查看';

-- 3. 添加策略管理菜单（parent_id=7 表示属于主机管理目录）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (134, '策略管理', 7, 6, 'strategy', 'business/host/apps/strategy', '', '', 1, 0, 'C', '0', '0', 'system:strategy:view', 'build', 'admin', NOW(), '', NULL, '策略参数映射管理，支持成本优先、质量优先、最大回复Token等策略配置')
ON DUPLICATE KEY UPDATE 
  menu_name = '策略管理',
  parent_id = 7,
  order_num = 6,
  path = 'strategy',
  component = 'business/host/apps/strategy',
  icon = 'build',
  remark = '策略参数映射管理，支持成本优先、质量优先、最大回复Token等策略配置';

-- 4. 添加策略管理按钮权限
-- 查看
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (1098, '策略管理查看', 134, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:strategy:query', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE menu_name = '策略管理查看';

-- 新增
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (1099, '策略管理新增', 134, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:strategy:add', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE menu_name = '策略管理新增';

-- 修改
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (1100, '策略管理修改', 134, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:strategy:edit', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE menu_name = '策略管理修改';

-- 删除
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (1101, '策略管理删除', 134, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:strategy:remove', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE menu_name = '策略管理删除';

-- ========================================
-- 第三部分：角色权限分配
-- ========================================

-- 为管理员角色分配工作流节点编辑菜单权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 133) ON DUPLICATE KEY UPDATE role_id = role_id;
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 1097) ON DUPLICATE KEY UPDATE role_id = role_id;

-- 为管理员角色分配策略管理菜单权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 134) ON DUPLICATE KEY UPDATE role_id = role_id;
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 1098) ON DUPLICATE KEY UPDATE role_id = role_id;
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 1099) ON DUPLICATE KEY UPDATE role_id = role_id;
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 1100) ON DUPLICATE KEY UPDATE role_id = role_id;
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 1101) ON DUPLICATE KEY UPDATE role_id = role_id;

-- ========================================
-- 第四部分：策略参数映射初始数据
-- ========================================

-- 成本优先
INSERT INTO strategy_param_mapping
  (strategy_name, model_name, temperature, top_p, max_tokens, prompt)
VALUES
  ('成本优先', '混元大模型长文本版', 0.30, 0.70, 3000,
   '请以降低成本为核心目标，输出解决方案或内容时优先考虑资源节省、简化流程、复用已有资产。结构清晰，列出关键步骤与资源预算，避免过度冗长与高成本操作。')
ON DUPLICATE KEY UPDATE
  model_name = '混元大模型长文本版',
  temperature = 0.30,
  top_p = 0.70,
  max_tokens = 3000,
  prompt = '请以降低成本为核心目标，输出解决方案或内容时优先考虑资源节省、简化流程、复用已有资产。结构清晰，列出关键步骤与资源预算，避免过度冗长与高成本操作。';

-- 质量优先
INSERT INTO strategy_param_mapping
  (strategy_name, model_name, temperature, top_p, max_tokens, prompt)
VALUES
  ('质量优先', '混元大模型长文本版', 0.60, 0.90, 4000,
   '请以输出质量为首要目标，确保内容准确、结构清晰、论据充分。必要时给出示例或细节支撑，语言自然流畅，避免敷衍与缺漏。')
ON DUPLICATE KEY UPDATE
  model_name = '混元大模型长文本版',
  temperature = 0.60,
  top_p = 0.90,
  max_tokens = 4000,
  prompt = '请以输出质量为首要目标，确保内容准确、结构清晰、论据充分。必要时给出示例或细节支撑，语言自然流畅，避免敷衍与缺漏。';

-- 最大回复Token
INSERT INTO strategy_param_mapping
  (strategy_name, model_name, temperature, top_p, max_tokens, prompt)
VALUES
  ('最大回复Token', '混元大模型长文本版', 0.50, 0.85, 6000,
   '请生成尽量长且信息全面的内容，覆盖背景、分析、方案、风险与总结，段落清晰，适度列出要点与补充细节，不要省略关键步骤。')
ON DUPLICATE KEY UPDATE
  model_name = '混元大模型长文本版',
  temperature = 0.50,
  top_p = 0.85,
  max_tokens = 6000,
  prompt = '请生成尽量长且信息全面的内容，覆盖背景、分析、方案、风险与总结，段落清晰，适度列出要点与补充细节，不要省略关键步骤。';

