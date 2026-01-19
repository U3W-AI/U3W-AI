-- ----------------------------
-- 初始化-菜单信息表数据
-- ----------------------------
-- 菜单ID规划说明（统一规划，便于扩展和维护）：
-- ┌─────────────┬──────────┬─────────────────────────────────────┐
-- │ 菜单层级    │ ID范围   │ 说明                                │
-- ├─────────────┼──────────┼─────────────────────────────────────┤
-- │ 一级菜单    │ 1-99     │ 顶级目录（如：内容管理、系统管理）  │
-- │ 二级菜单    │ 100-499  │ 功能页面（系统100-117，业务118起）  │
-- │ 三级菜单    │ 500-999  │ 子页面/子功能                       │
-- │ 按钮权限    │ 1000+    │ 按钮级操作（系统1000-1060，业务1061起）│
-- └─────────────┴──────────┴─────────────────────────────────────┘
--
-- 业务模块ID分配：
--   二级菜单：118=日更助手, 119=发布记录, 120-199预留
--   按钮权限：1061-1080=日更助手, 1081-1100=发布记录, 1101+预留
--
-- 权限标识命名规范：模块:功能:操作
--   业务模块：business:daily:*, business:publish:*, business:wechat:*
--   系统模块：system:*, monitor:*, tool:*
-- ----------------------------
-- 一级菜单（ID: 1-99）
insert into sys_menu values('9', '认证申请', '0', '9', 'certificate', NULL, '', '', 1, 0, 'M', '0', '0', '', 'clipboard', 'admin', sysdate(), '', null, '认证申请目录');
-- 二级菜单（ID范围：100-499）
-- 认证申请管理子菜单（parent_id=9）
insert into sys_menu values ('133', '证书模板', '9', '1', 'template', 'business/certificate/template/index', '', '', 1, 0, 'C', '0', '0', 'business:certificate:template:list', 'form', 'admin', sysdate(), '', null, '证书模板菜单');
insert into sys_menu values ('134', '认证申请', '9', '2', 'application', 'business/certificate/application/index', '', '', 1, 0, 'C', '0', '0', 'business:certificate:application:list', 'edit', 'admin', sysdate(), '', null, '证书申请菜单');
insert into sys_menu values ('135', '证书管理', '9', '3', 'issuance', 'business/certificate/certificateManagement/index', '', '', 1, 0, 'C', '0', '0', 'business:certificate:issuance:list', 'excel', 'admin', sysdate(), '', null, '证书管理菜单');
insert into sys_menu values ('136', '申请审核', '9', '4', 'application-review', 'business/certificate/applicationReview/index', '', '', 1, 0, 'C', '0', '0', 'business:certificate:review:list', 'guide', 'admin', sysdate(), '', null, '证书申请审核菜单”');
insert into sys_menu values ('137', '我的申请', '9', '5', 'my-applications', 'business/certificate/myApplications/index', '', '', 1, 0, 'C', '0', '0', 'business:certificate:my:application:list', 'user', 'admin', sysdate(), '', null, '我的证书菜单');

-- 三级菜单（ID范围：500-999）
-- 证书模板按钮权限（parent_id=133）
insert into sys_menu values ('1104', '模板查询', '133', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:template:query', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1105', '模板新增', '133', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:template:add', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1106', '模板修改', '133', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:template:edit', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1107', '模板删除', '133', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:template:remove', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1108', '模板上下架','133', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:template:export', '#', 'admin', sysdate(), '', NULL, '');
-- 申请认证按钮权限（parent_id=134）
insert into sys_menu values ('1109', '申请查询', '134', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:application:query', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1110', '申请新增', '134', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:application:add', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1111', '申请修改', '134', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:application:edit', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1112', '申请删除', '134', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:application:remove', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1113', '申请提交', '134', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:application:submit', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1114', '申请撤回', '134', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:application:withdraw', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1115', '申请审核', '134', '7', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:application:review', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1116', '申请导出', '134', '8', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:application:export', '#', 'admin', sysdate(), '', NULL, '');
-- 证书管理按钮权限（parent_id=135）
insert into sys_menu values ('1117', '证书查询', '135', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:issuance:query', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1118', '证书新增', '135', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:issuance:add', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1119', '证书修改', '135', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:issuance:edit', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1120', '证书删除', '135', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:issuance:remove', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1121', '证书导出', '135', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:issuance:export', '#', 'admin', sysdate(), '', NULL, '');
-- 申请审核按钮权限（parent_id=136）
insert into sys_menu values ('1122', '申请审核查询', '136', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:review:query', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1123', '申请审核新增', '136', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:review:add', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1124', '申请审核修改', '136', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:review:edit', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1125', '申请审核删除', '136', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:review:remove', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1126', '申请审核导出', '136', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:review:export', '#', 'admin', sysdate(), '', NULL, '');
-- 我的申请按钮权限（parent_id=137）
insert into sys_menu values ('1127', '我的申请记录查询', '137', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:my:application:query', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1128', '我的申请记录新增', '137', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:my:application:add', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1129', '我的申请记录修改', '137', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:my:application:edit', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1130', '我的申请记录删除', '137', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:my:application:remove', '#', 'admin', sysdate(), '', NULL, '');
insert into sys_menu values ('1131', '我的申请记录撤回', '137', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'business:certificate:my:application:withdraw', '#', 'admin', sysdate(), '', NULL, '');



-- ----------------------------
-- 初始化-角色和菜单关联表数据
-- ----------------------------
-- 管理员角色（ID=2）拥有全部权限
-- 一级菜单
insert into sys_role_menu values ('2', '9');    -- 申请认证管理
-- 二级菜单-申请认证管理
insert into sys_role_menu values ('2', '133');  -- 证书模板
insert into sys_role_menu values ('2', '134');  -- 认证申请
insert into sys_role_menu values ('2', '135');  -- 证书管理
insert into sys_role_menu values ('2', '136');  -- 申请审核
insert into sys_role_menu values ('2', '137');  -- 我的申请
-- 按钮权限-证书模板
insert into sys_role_menu values ('2', '1104'); -- 模板查询
insert into sys_role_menu values ('2', '1105'); -- 模板新增
insert into sys_role_menu values ('2', '1106'); -- 模板修改
insert into sys_role_menu values ('2', '1107'); -- 模板删除
insert into sys_role_menu values ('2', '1108'); -- 模板导出
-- 按钮权限-认证申请
insert into sys_role_menu values ('2', '1109'); -- 申请查询
insert into sys_role_menu values ('2', '1110'); -- 申请新增
insert into sys_role_menu values ('2', '1111'); -- 申请修改
insert into sys_role_menu values ('2', '1112'); -- 申请删除
insert into sys_role_menu values ('2', '1113'); -- 申请提交
insert into sys_role_menu values ('2', '1114'); -- 申请撤回
insert into sys_role_menu values ('2', '1115'); -- 申请审核
insert into sys_role_menu values ('2', '1116'); -- 申请导出
-- 按钮权限-证书管理
insert into sys_role_menu values ('2', '1117'); -- 证书查询
insert into sys_role_menu values ('2', '1118'); -- 证书新增
insert into sys_role_menu values ('2', '1119'); -- 证书修改
insert into sys_role_menu values ('2', '1120'); -- 证书删除
insert into sys_role_menu values ('2', '1121'); -- 证书导出
-- 按钮权限-申请审核
insert into sys_role_menu values ('2', '1122'); -- 申请审核查询
insert into sys_role_menu values ('2', '1123'); -- 申请审核新增
insert into sys_role_menu values ('2', '1124'); -- 申请审核修改
insert into sys_role_menu values ('2', '1125'); -- 申请审核删除
insert into sys_role_menu values ('2', '1126'); -- 申请审核导出
-- 按钮权限-我的申请
insert into sys_role_menu values ('2', '1127'); -- 我的申请记录查询
insert into sys_role_menu values ('2', '1128'); -- 我的申请记录新增
insert into sys_role_menu values ('2', '1129'); -- 我的申请记录修改
insert into sys_role_menu values ('2', '1130'); -- 我的申请记录删除
insert into sys_role_menu values ('2', '1131'); -- 我的申请记录撤回
-- 只读权限角色（ID=3）拥有内容管理的全部权限，系统管理等模块只有查询权限
-- 一级菜单
insert into sys_role_menu values ('3', '9');      -- 申请认证管理
-- 二级菜单-申请认证
insert into sys_role_menu values ('3', '133');  -- 证书模板
insert into sys_role_menu values ('3', '134');  -- 认证申请
insert into sys_role_menu values ('3', '135');  -- 证书管理
insert into sys_role_menu values ('3', '136');  -- 申请审核
insert into sys_role_menu values ('3', '137');  -- 我的申请
-- 按钮权限-申请认证（只读）
insert into sys_role_menu values ('3', '1104');  -- 模板查询
insert into sys_role_menu values ('3', '1112');  -- 申请删除
insert into sys_role_menu values ('3', '1120');  -- 证书删除


-- 普通用户角色（ID=10）只有内容管理和积分浏览权限
insert into sys_role_menu values ('10', '9');    -- 认证申请目录
insert into sys_role_menu values ('10', '1109'); -- 申请查询
insert into sys_role_menu values ('10', '1110'); -- 申请新增
insert into sys_role_menu values ('10', '1111'); -- 申请修改
insert into sys_role_menu values ('10', '1112'); -- 申请删除
insert into sys_role_menu values ('10', '1113'); -- 申请提交
insert into sys_role_menu values ('10', '1114'); -- 申请撤回
insert into sys_role_menu values ('10', '1115'); -- 申请审核
insert into sys_role_menu values ('10', '1116'); -- 申请导出
insert into sys_role_menu values ('10', '1127'); -- 我的申请记录查询
insert into sys_role_menu values ('10', '1128'); -- 我的申请记录新增
insert into sys_role_menu values ('10', '1129'); -- 我的申请记录修改
insert into sys_role_menu values ('10', '1130'); -- 我的申请记录删除
insert into sys_role_menu values ('10', '1131'); -- 我的申请记录撤回



-- ----------------------------
-- 初始化积分规则数据
-- ----------------------------
INSERT INTO `wx_points_rule` (`rule_code`, `rule_name`, `points_value`, `limit_type`, `limit_value`, `max_amount`, `status`, `sort_order`, `remark`, `create_by`, `create_time`) VALUES
('SHELF_CERTIFICATE_TEMPLATE', '新增证书模板', -1, NULL, NULL, NULL, '0', 8, '新增证书扣减', 'admin', NOW()),
('ISSUE_CERTIFICATES', '审核通过发放证书', -1, NULL, NULL, NULL, '0', 9, '审核通过发放证书扣减积分', 'admin', NOW()),
('RECEIVE_CERTIFICATE', '领取证书', -1, NULL, NULL, NULL, '0', 10, '领取证书扣减积分', 'admin', NOW()),
('APPLY_CERTIFICATE', '申请证书', -1, NULL, NULL, NULL, '0', 11, '申请证书扣减积分','admin', NOW());
