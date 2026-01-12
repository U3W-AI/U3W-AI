-- ----------------------------
-- 创建数据库
-- ----------------------------
CREATE DATABASE IF NOT EXISTS `wxfbsir` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `wxfbsir`;
-- ----------------------------
-- 1、部门表
-- ----------------------------
drop table if exists sys_dept;
create table sys_dept (
  dept_id           bigint(20)      not null auto_increment    comment '部门id',
  parent_id         bigint(20)      default 0                  comment '父部门id',
  ancestors         varchar(50)     default ''                 comment '祖级列表',
  dept_name         varchar(30)     default ''                 comment '部门名称',
  order_num         int(4)          default 0                  comment '显示顺序',
  leader            varchar(20)     default null               comment '负责人',
  phone             varchar(11)     default null               comment '联系电话',
  email             varchar(50)     default null               comment '邮箱',
  status            char(1)         default '0'                comment '部门状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (dept_id)
) engine=innodb auto_increment=200 comment = '部门表';

-- ----------------------------
-- 初始化-部门表数据
-- ----------------------------
insert into sys_dept values(100,  0,   '0',          '总公司',     0, '', '', '', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(101,  100, '0,100',      '技术部',     1, '', '', '', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(102,  100, '0,100',      '运营部',     2, '', '', '', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(103,  101, '0,100,101',  '研发部门',   1, '', '', '', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(104,  101, '0,100,101',  '测试部门',   2, '', '', '', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(105,  101, '0,100,101',  '运维部门',   3, '', '', '', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(106,  102, '0,100,102',  '市场部门',   1, '', '', '', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(107,  102, '0,100,102',  '财务部门',   2, '', '', '', '0', '0', 'admin', sysdate(), '', null);


-- ----------------------------
-- 2、用户信息表
-- ----------------------------
drop table if exists sys_user;
create table sys_user (
  user_id           bigint(20)      not null auto_increment    comment '用户ID',
  dept_id           bigint(20)      default null               comment '部门ID',
  user_name         varchar(30)     not null                   comment '用户账号',
  nick_name         varchar(30)     not null                   comment '用户昵称',
  user_type         varchar(2)      default '00'               comment '用户类型（00系统用户）',
  email             varchar(50)     default ''                 comment '用户邮箱',
  phonenumber       varchar(11)     default ''                 comment '手机号码',
  sex               char(1)         default '0'                comment '用户性别（0男 1女 2未知）',
  avatar            varchar(100)    default ''                 comment '头像地址',
  password          varchar(100)    default ''                 comment '密码',
  status            char(1)         default '0'                comment '账号状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  login_ip          varchar(128)    default ''                 comment '最后登录IP',
  login_date        datetime                                   comment '最后登录时间',
  pwd_update_date   datetime                                   comment '密码最后更新时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  points            int(10)         default 0                  comment '积分',
  primary key (user_id)
) engine=innodb auto_increment=100 comment = '用户信息表';

-- ----------------------------
-- 初始化-用户信息表数据
-- ----------------------------
insert into sys_user values(1,  103, 'admin', '管理员', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '超级管理员',0);


-- ----------------------------
-- 3、岗位信息表
-- ----------------------------
drop table if exists sys_post;
create table sys_post
(
  post_id       bigint(20)      not null auto_increment    comment '岗位ID',
  post_code     varchar(64)     not null                   comment '岗位编码',
  post_name     varchar(50)     not null                   comment '岗位名称',
  post_sort     int(4)          not null                   comment '显示顺序',
  status        char(1)         not null                   comment '状态（0正常 1停用）',
  create_by     varchar(64)     default ''                 comment '创建者',
  create_time   datetime                                   comment '创建时间',
  update_by     varchar(64)     default ''			       comment '更新者',
  update_time   datetime                                   comment '更新时间',
  remark        varchar(500)    default null               comment '备注',
  primary key (post_id)
) engine=innodb comment = '岗位信息表';

-- ----------------------------
-- 初始化-岗位信息表数据
-- ----------------------------
insert into sys_post values(1, 'ceo',  '董事长',    1, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(2, 'se',   '项目经理',  2, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(3, 'hr',   '人力资源',  3, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(4, 'dev',  '开发人员',  4, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(5, 'ops',  '运维人员',  5, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(6, 'user', '普通员工',  6, '0', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 4、角色信息表
-- ----------------------------
drop table if exists sys_role;
create table sys_role (
  role_id              bigint(20)      not null auto_increment    comment '角色ID',
  role_name            varchar(30)     not null                   comment '角色名称',
  role_key             varchar(100)    not null                   comment '角色权限字符串',
  role_sort            int(4)          not null                   comment '显示顺序',
  data_scope           char(1)         default '1'                comment '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  menu_check_strictly  tinyint(1)      default 1                  comment '菜单树选择项是否关联显示',
  dept_check_strictly  tinyint(1)      default 1                  comment '部门树选择项是否关联显示',
  status               char(1)         not null                   comment '角色状态（0正常 1停用）',
  del_flag             char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by            varchar(64)     default ''                 comment '创建者',
  create_time          datetime                                   comment '创建时间',
  update_by            varchar(64)     default ''                 comment '更新者',
  update_time          datetime                                   comment '更新时间',
  remark               varchar(500)    default null               comment '备注',
  primary key (role_id)
) engine=innodb auto_increment=100 comment = '角色信息表';

-- ----------------------------
-- 初始化-角色信息表数据
-- ----------------------------
insert into sys_role values('1', '超级管理员',  'admin',    1, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '超级管理员');
insert into sys_role values('2', '管理员',      'manager',  2, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_role values('3', '只读权限',    'readonly', 3, 4, 1, 1, '0', '0', 'admin', sysdate(), '', null, '只读权限角色');
insert into sys_role values('10', '普通用户',   'user',     4, 2, 1, 1, '0', '0', 'admin', sysdate(), '', null, '普通用户，默认拥有内容管理权限');

-- ----------------------------
-- 5、菜单权限表
-- ----------------------------
drop table if exists sys_menu;
create table sys_menu (
  menu_id           bigint(20)      not null auto_increment    comment '菜单ID',
  menu_name         varchar(50)     not null                   comment '菜单名称',
  parent_id         bigint(20)      default 0                  comment '父菜单ID',
  order_num         int(4)          default 0                  comment '显示顺序',
  path              varchar(200)    default ''                 comment '路由地址',
  component         varchar(255)    default null               comment '组件路径',
  query             varchar(255)    default null               comment '路由参数',
  route_name        varchar(50)     default ''                 comment '路由名称',
  is_frame          int(1)          default 1                  comment '是否为外链（0是 1否）',
  is_cache          int(1)          default 0                  comment '是否缓存（0缓存 1不缓存）',
  menu_type         char(1)         default ''                 comment '菜单类型（M目录 C菜单 F按钮）',
  visible           char(1)         default 0                  comment '菜单状态（0显示 1隐藏）',
  status            char(1)         default 0                  comment '菜单状态（0正常 1停用）',
  perms             varchar(100)    default null               comment '权限标识',
  icon              varchar(100)    default '#'                comment '菜单图标',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default ''                 comment '备注',
  primary key (menu_id)
) engine=innodb auto_increment=2000 comment = '菜单权限表';

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
insert into sys_menu values('1', '内容管理', '0', '1', 'content',          null, '', '', 1, 0, 'M', '0', '0', '', 'edit',     'admin', sysdate(), '', null, '内容管理目录');
insert into sys_menu values('2', '系统管理', '0', '2', 'system',           null, '', '', 1, 0, 'M', '0', '0', '', 'system',   'admin', sysdate(), '', null, '系统管理目录');
insert into sys_menu values('3', '系统监控', '0', '3', 'monitor',          null, '', '', 1, 0, 'M', '0', '0', '', 'monitor',  'admin', sysdate(), '', null, '系统监控目录');
insert into sys_menu values('4', '系统工具', '0', '4', 'tool',             null, '', '', 1, 0, 'M', '0', '0', '', 'tool',     'admin', sysdate(), '', null, '系统工具目录');
insert into sys_menu values('6', '积分管理', '0', '6', 'points', null, '', '', 1, 0, 'M', '0', '0', '', 'money', 'admin', sysdate(), '', null, '积分管理目录');
insert into sys_menu values('7', '主机管理', '0', '7', 'host', null, '', '', 1, 0, 'M', '0', '0', '', 'server', 'admin', sysdate(), '', null, '主机管理目录');
insert into sys_menu values('8', 'gitee管理', '0', '8', 'gitee', null, '', '', 1, 0, 'M', '0', '0', '', 'gitee', 'admin', sysdate(), '', null, 'gitee管理目录');
-- 二级菜单（ID范围：100-499）
-- 内容管理子菜单（parent_id=1，业务功能从118开始）
insert into sys_menu values('118',  '日更助手', '1',   '1', 'daily-assistant', 'business/content/dailyassistant/index', '', '', 1, 0, 'C', '0', '0', 'business:daily:view',     'edit',          'admin', sysdate(), '', null, '日更助手菜单');
insert into sys_menu values('119',  '发布记录', '1',   '2', 'publish-record',  'business/content/publishrecord/index',  '', '', 1, 0, 'C', '0', '0', 'business:publish:list',   'documentation', 'admin', sysdate(), '', null, '公众号发布记录菜单');
insert into sys_menu values('130',  '文档解析助手', '1',   '3', 'document-parse', 'business/content/documentparse/index', '', '', 1, 0, 'C', '0', '0', 'business:document:view',   'documentation',      'admin', sysdate(), '', null, '文档解析助手菜单');
-- 系统管理子菜单（parent_id=2）
insert into sys_menu values('100',  '用户管理', '2',   '1', 'user',       'system/user/index',        '', '', 1, 0, 'C', '0', '0', 'system:user:list',        'user',          'admin', sysdate(), '', null, '用户管理菜单');
insert into sys_menu values('101',  '角色管理', '2',   '2', 'role',       'system/role/index',        '', '', 1, 0, 'C', '0', '0', 'system:role:list',        'peoples',       'admin', sysdate(), '', null, '角色管理菜单');
insert into sys_menu values('102',  '菜单管理', '2',   '3', 'menu',       'system/menu/index',        '', '', 1, 0, 'C', '0', '0', 'system:menu:list',        'tree-table',    'admin', sysdate(), '', null, '菜单管理菜单');
insert into sys_menu values('103',  '部门管理', '2',   '4', 'dept',       'system/dept/index',        '', '', 1, 0, 'C', '0', '0', 'system:dept:list',        'tree',          'admin', sysdate(), '', null, '部门管理菜单');
insert into sys_menu values('104',  '岗位管理', '2',   '5', 'post',       'system/post/index',        '', '', 1, 0, 'C', '0', '0', 'system:post:list',        'post',          'admin', sysdate(), '', null, '岗位管理菜单');
insert into sys_menu values('105',  '字典管理', '2',   '6', 'dict',       'system/dict/index',        '', '', 1, 0, 'C', '0', '0', 'system:dict:list',        'dict',          'admin', sysdate(), '', null, '字典管理菜单');
insert into sys_menu values('106',  '参数设置', '2',   '7', 'config',     'system/config/index',      '', '', 1, 0, 'C', '0', '0', 'system:config:list',      'edit',          'admin', sysdate(), '', null, '参数设置菜单');
insert into sys_menu values('107',  '通知公告', '2',   '8', 'notice',     'system/notice/index',      '', '', 1, 0, 'C', '0', '0', 'system:notice:list',      'message',       'admin', sysdate(), '', null, '通知公告菜单');
insert into sys_menu values('108',  '日志管理', '2',   '9', 'log',        '',                         '', '', 1, 0, 'M', '0', '0', '',                        'log',           'admin', sysdate(), '', null, '日志管理菜单');
-- 系统监控子菜单（parent_id=3）
insert into sys_menu values('109',  '在线用户', '3',   '1', 'online',     'monitor/online/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:online:list',     'online',        'admin', sysdate(), '', null, '在线用户菜单');
insert into sys_menu values('110',  '定时任务', '3',   '2', 'job',        'monitor/job/index',        '', '', 1, 0, 'C', '0', '0', 'monitor:job:list',        'job',           'admin', sysdate(), '', null, '定时任务菜单');
insert into sys_menu values('111',  '数据监控', '3',   '3', 'druid',      'monitor/druid/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:druid:list',      'druid',         'admin', sysdate(), '', null, '数据监控菜单');
insert into sys_menu values('112',  '服务监控', '3',   '4', 'server',     'monitor/server/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:server:list',     'server',        'admin', sysdate(), '', null, '服务监控菜单');
insert into sys_menu values('113',  '缓存监控', '3',   '5', 'cache',      'monitor/cache/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis',         'admin', sysdate(), '', null, '缓存监控菜单');
insert into sys_menu values('114',  '缓存列表', '3',   '6', 'cacheList',  'monitor/cache/list',       '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis-list',    'admin', sysdate(), '', null, '缓存列表菜单');
-- 系统工具子菜单（parent_id=4）
insert into sys_menu values('115',  '表单构建', '4',   '1', 'build',      'tool/build/index',         '', '', 1, 0, 'C', '0', '0', 'tool:build:list',         'build',         'admin', sysdate(), '', null, '表单构建菜单');
insert into sys_menu values('116',  '代码生成', '4',   '2', 'gen',        'tool/gen/index',           '', '', 1, 0, 'C', '0', '0', 'tool:gen:list',           'code',          'admin', sysdate(), '', null, '代码生成菜单');
insert into sys_menu values('117',  '系统接口', '4',   '3', 'swagger',    'tool/swagger/index',       '', '', 1, 0, 'C', '0', '0', 'tool:swagger:list',       'swagger',       'admin', sysdate(), '', null, '系统接口菜单');
-- 积分管理子菜单（parent_id=6）
insert into sys_menu values('120',   '积分总览', '6',   '1', 'points-overview', 'business/points/overview/index', '', '', 1, 0, 'C', '0', '0', 'business:points:view', 'money', 'admin', sysdate(), '', null, '积分总览菜单');
insert into sys_menu values('121',   '积分规则配置', '6', '2', 'points-rule', 'system/points/rule/index', '', '', 1, 0, 'C', '0', '0', 'points:rule:list', 'edit', 'admin', sysdate(), '', null, '积分规则配置菜单');
insert into sys_menu values('122',   '粉丝管理', '6', '3', 'points-fans', 'business/points/fans/index', '', '', 1, 0, 'C', '0', '0', 'points:fans:list', 'user', 'admin', sysdate(), '', null, '粉丝管理菜单');
-- 主机管理子菜单（parent_id=7）
insert into sys_menu values('123', '主机ID白名单', '7', '1', 'whitelist', 'business/host/whitelist/index', '', '', 1, 0, 'C', '0', '0', 'business:host:whitelist:view', 'list', 'admin', sysdate(), '', null, '主机ID白名单管理菜单');
insert into sys_menu values('124', 'IP黑名单', '7', '2', 'blacklist', 'business/host/blacklist/index', '', '', 1, 0, 'C', '0', '0', 'business:host:blacklist:view', 'lock', 'admin', sysdate(), '', null, 'IP黑名单管理菜单');
insert into sys_menu values('125', '连接记录与在线', '7', '3', 'connection', 'business/host/connection/index', '', '', 1, 0, 'C', '0', '0', 'business:host:connection:view', 'monitor', 'admin', sysdate(), '', null, '主机连接记录与在线列表管理菜单');
insert into sys_menu values('126', 'WebSocket调试', '7', '4', 'debug', 'business/debug/index', '', '', 1, 0, 'C', '0', '0', 'business:debug:view', 'bug', 'admin', sysdate(), '', null, 'WebSocket调试工具，用于开发测试');
-- gitee管理子菜单（parent_id=8）
insert into sys_menu values('127',  '使用统计', '8',   '1', 'usage-report', 'business/gitee/giteeUsageReport', '', '', 1, 0, 'C', '0', '0', 'business:gitee:usage:list', 'chart', 'admin', sysdate(), '', null, 'Gitee模块使用统计菜单');
insert into sys_menu values('128',  'gitee分析', '8',  '2', 'gitee-analysis', 'business/gitee/giteeAnalysis', '', '', 1, 0, 'C', '0', '0', 'business:gitee:analysis:view', 'chart', 'admin', sysdate(), '', null, 'Gitee分析菜单');
-- 三级菜单（ID范围：500-999）
insert into sys_menu values('500',  '操作日志', '108', '1', 'operlog',    'monitor/operlog/index',    '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list',    'form',          'admin', sysdate(), '', null, '操作日志菜单');
insert into sys_menu values('501',  '登录日志', '108', '2', 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor',    'admin', sysdate(), '', null, '登录日志菜单');
-- 按钮权限（ID范围：1000+）
-- 用户管理按钮
insert into sys_menu values('1000', '用户查询', '100', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1001', '用户新增', '100', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1002', '用户修改', '100', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1003', '用户删除', '100', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1004', '用户导出', '100', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1005', '用户导入', '100', '6',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1006', '重置密码', '100', '7',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd',       '#', 'admin', sysdate(), '', null, '');
-- 角色管理按钮
insert into sys_menu values('1007', '角色查询', '101', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1008', '角色新增', '101', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1009', '角色修改', '101', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1010', '角色删除', '101', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1011', '角色导出', '101', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export',         '#', 'admin', sysdate(), '', null, '');
-- 菜单管理按钮
insert into sys_menu values('1012', '菜单查询', '102', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1013', '菜单新增', '102', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1014', '菜单修改', '102', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1015', '菜单删除', '102', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove',         '#', 'admin', sysdate(), '', null, '');
-- 部门管理按钮
insert into sys_menu values('1016', '部门查询', '103', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1017', '部门新增', '103', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1018', '部门修改', '103', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1019', '部门删除', '103', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove',         '#', 'admin', sysdate(), '', null, '');
-- 岗位管理按钮
insert into sys_menu values('1020', '岗位查询', '104', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1021', '岗位新增', '104', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1022', '岗位修改', '104', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1023', '岗位删除', '104', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1024', '岗位导出', '104', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export',         '#', 'admin', sysdate(), '', null, '');
-- 字典管理按钮
insert into sys_menu values('1025', '字典查询', '105', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1026', '字典新增', '105', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1027', '字典修改', '105', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1028', '字典删除', '105', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1029', '字典导出', '105', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export',         '#', 'admin', sysdate(), '', null, '');
-- 参数设置按钮
insert into sys_menu values('1030', '参数查询', '106', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1031', '参数新增', '106', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1032', '参数修改', '106', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1033', '参数删除', '106', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1034', '参数导出', '106', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export',       '#', 'admin', sysdate(), '', null, '');
-- 通知公告按钮
insert into sys_menu values('1035', '公告查询', '107', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1036', '公告新增', '107', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1037', '公告修改', '107', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1038', '公告删除', '107', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove',       '#', 'admin', sysdate(), '', null, '');
-- 操作日志按钮
insert into sys_menu values('1039', '操作查询', '500', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1040', '操作删除', '500', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1041', '日志导出', '500', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export',     '#', 'admin', sysdate(), '', null, '');
-- 登录日志按钮
insert into sys_menu values('1042', '登录查询', '501', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1043', '登录删除', '501', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1044', '日志导出', '501', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1045', '账户解锁', '501', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock',  '#', 'admin', sysdate(), '', null, '');
-- 在线用户按钮
insert into sys_menu values('1046', '在线查询', '109', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1047', '批量强退', '109', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1048', '单条强退', '109', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', sysdate(), '', null, '');
-- 定时任务按钮
insert into sys_menu values('1049', '任务查询', '110', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1050', '任务新增', '110', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1051', '任务修改', '110', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1052', '任务删除', '110', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1053', '状态修改', '110', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1054', '任务导出', '110', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export',         '#', 'admin', sysdate(), '', null, '');
-- 代码生成按钮
insert into sys_menu values('1055', '生成查询', '116', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query',             '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1056', '生成修改', '116', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit',              '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1057', '生成删除', '116', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1058', '导入代码', '116', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1059', '预览代码', '116', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1060', '生成代码', '116', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code',              '#', 'admin', sysdate(), '', null, '');
-- 日更助手按钮（parent_id=118）
insert into sys_menu values('1061', '文章查询', '118', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:daily:query',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1062', '文章新增', '118', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:daily:add',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1063', '文章删除', '118', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:daily:remove',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1064', '智能体配置', '118', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:daily:config',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1065', '智能排版', '118', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:daily:layout',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1066', '发布公众号', '118', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:daily:publish',     '#', 'admin', sysdate(), '', null, '');
-- 发布记录管理按钮（parent_id=119）
insert into sys_menu values('1067', '记录查询', '119', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:publish:list',      '#', 'admin', sysdate(), '', null, '查询发布记录列表');
insert into sys_menu values('1068', '记录详情', '119', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:publish:query',     '#', 'admin', sysdate(), '', null, '查看发布记录详情');
-- 文档解析助手按钮（parent_id=130）
insert into sys_menu values('1097', '解析查询', '130', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:document:query',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1098', '解析新增', '130', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:document:add',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1099', '解析删除', '130', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:document:remove',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1069', '记录删除', '119', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:publish:remove',    '#', 'admin', sysdate(), '', null, '删除发布记录');
-- 积分总览按钮(parent_id=120)
insert into sys_menu values('1070', '积分查询', '120', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'business:points:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1071', '明细查询', '120', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'business:points:record:query', '#', 'admin', sysdate(), '', null, '');
-- 积分规则配置按钮(parent_id=121)
insert into sys_menu values('1072', '规则查询', '121', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'points:rule:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1073', '规则新增', '121', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'points:rule:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1074', '规则修改', '121', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'points:rule:edit', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1075', '规则删除', '121', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'points:rule:remove', '#', 'admin', sysdate(), '', null, '');
-- 粉丝管理按钮(parent_id=122)
insert into sys_menu values('1076', '粉丝列表', '122', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'points:fans:list', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1077', '发放积分', '122', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'points:fans:grant', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1078', '查看明细', '122', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'points:fans:detail', '#', 'admin', sysdate(), '', null, '');
-- 主机ID白名单按钮(parent_id=123)
insert into sys_menu values('1079', '白名单查询', '123', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:whitelist:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1080', '白名单新增', '123', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:whitelist:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1081', '白名单修改', '123', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:whitelist:edit', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1082', '白名单删除', '123', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:whitelist:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1083', '白名单导出', '123', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:whitelist:export', '#', 'admin', sysdate(), '', null, '');
-- IP黑名单按钮(parent_id=124)
insert into sys_menu values('1084', '黑名单查询', '124', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:blacklist:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1085', '黑名单新增', '124', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:blacklist:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1086', '黑名单修改', '124', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:blacklist:edit', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1087', '黑名单删除', '124', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:blacklist:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1088', '黑名单导出', '124', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:blacklist:export', '#', 'admin', sysdate(), '', null, '');
-- 连接记录与在线列表按钮(parent_id=125)
insert into sys_menu values('1089', '连接记录查询', '125', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:connection:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1090', '连接记录删除', '125', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:connection:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1091', '在线主机查询', '125', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:online:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1092', '在线主机下线', '125', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'business:host:online:offline', '#', 'admin', sysdate(), '', null, '');
-- WebSocket调试按钮(parent_id=126)
insert into sys_menu values('1093', '调试工具查看', '126', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:debug:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1094', '发送消息', '126', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:debug:send', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1095', '清空消息', '126', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:debug:clear', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1096', '导出日志', '126', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:debug:export', '#', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 6、用户和角色关联表  用户N-1角色
-- ----------------------------
drop table if exists sys_user_role;
create table sys_user_role (
  user_id   bigint(20) not null comment '用户ID',
  role_id   bigint(20) not null comment '角色ID',
  primary key(user_id, role_id)
) engine=innodb comment = '用户和角色关联表';

-- ----------------------------
-- 初始化-用户和角色关联表数据
-- ----------------------------
insert into sys_user_role values ('1', '1');

-- ----------------------------
-- 注意：所有用户默认拥有日更助手访问权限
-- 如需限制特定用户访问，请在角色管理中调整
-- ----------------------------


-- ----------------------------
-- 7、角色和菜单关联表  角色1-N菜单
-- ----------------------------
drop table if exists sys_role_menu;
create table sys_role_menu (
  role_id   bigint(20) not null comment '角色ID',
  menu_id   bigint(20) not null comment '菜单ID',
  primary key(role_id, menu_id)
) engine=innodb comment = '角色和菜单关联表';

-- ----------------------------
-- 初始化-角色和菜单关联表数据
-- ----------------------------
-- 管理员角色（ID=2）拥有全部权限
-- 一级菜单
insert into sys_role_menu values ('2', '1');    -- 内容管理
insert into sys_role_menu values ('2', '2');    -- 系统管理
insert into sys_role_menu values ('2', '3');    -- 系统监控
insert into sys_role_menu values ('2', '4');    -- 系统工具
insert into sys_role_menu values ('2', '6');    -- 积分管理
insert into sys_role_menu values ('2', '7');    -- 主机管理
-- 二级菜单-内容管理
insert into sys_role_menu values ('2', '118');  -- 日更助手
insert into sys_role_menu values ('2', '119');  -- 发布记录
insert into sys_role_menu values ('2', '130');  -- 文档解析助手
-- 二级菜单-系统管理
insert into sys_role_menu values ('2', '100');  -- 用户管理
insert into sys_role_menu values ('2', '101');  -- 角色管理
insert into sys_role_menu values ('2', '102');  -- 菜单管理
insert into sys_role_menu values ('2', '103');  -- 部门管理
insert into sys_role_menu values ('2', '104');  -- 岗位管理
insert into sys_role_menu values ('2', '105');  -- 字典管理
insert into sys_role_menu values ('2', '106');  -- 参数设置
insert into sys_role_menu values ('2', '107');  -- 通知公告
insert into sys_role_menu values ('2', '108');  -- 日志管理
-- 二级菜单-系统监控
insert into sys_role_menu values ('2', '109');  -- 在线用户
insert into sys_role_menu values ('2', '110');  -- 定时任务
insert into sys_role_menu values ('2', '111');  -- 数据监控
insert into sys_role_menu values ('2', '112');  -- 服务监控
insert into sys_role_menu values ('2', '113');  -- 缓存监控
insert into sys_role_menu values ('2', '114');  -- 缓存列表
-- 二级菜单-系统工具
insert into sys_role_menu values ('2', '115');  -- 表单构建
insert into sys_role_menu values ('2', '116');  -- 代码生成
insert into sys_role_menu values ('2', '117');  -- 系统接口
-- 二级菜单-积分管理
insert into sys_role_menu values ('2', '120');  -- 积分总览
insert into sys_role_menu values ('2', '121');  -- 积分规则配置
-- 二级菜单-主机管理
insert into sys_role_menu values ('2', '123');  -- 主机ID白名单
insert into sys_role_menu values ('2', '124');  -- IP黑名单
insert into sys_role_menu values ('2', '125');  -- 连接记录与在线
-- 三级菜单-日志管理
insert into sys_role_menu values ('2', '500');  -- 操作日志
insert into sys_role_menu values ('2', '501');  -- 登录日志
-- 按钮权限-用户管理
insert into sys_role_menu values ('2', '1000'); -- 用户查询
insert into sys_role_menu values ('2', '1001'); -- 用户新增
insert into sys_role_menu values ('2', '1002'); -- 用户修改
insert into sys_role_menu values ('2', '1003'); -- 用户删除
insert into sys_role_menu values ('2', '1004'); -- 用户导出
insert into sys_role_menu values ('2', '1005'); -- 用户导入
insert into sys_role_menu values ('2', '1006'); -- 重置密码
-- 按钮权限-角色管理
insert into sys_role_menu values ('2', '1007'); -- 角色查询
insert into sys_role_menu values ('2', '1008'); -- 角色新增
insert into sys_role_menu values ('2', '1009'); -- 角色修改
insert into sys_role_menu values ('2', '1010'); -- 角色删除
insert into sys_role_menu values ('2', '1011'); -- 角色导出
-- 按钮权限-菜单管理
insert into sys_role_menu values ('2', '1012'); -- 菜单查询
insert into sys_role_menu values ('2', '1013'); -- 菜单新增
insert into sys_role_menu values ('2', '1014'); -- 菜单修改
insert into sys_role_menu values ('2', '1015'); -- 菜单删除
-- 按钮权限-部门管理
insert into sys_role_menu values ('2', '1016'); -- 部门查询
insert into sys_role_menu values ('2', '1017'); -- 部门新增
insert into sys_role_menu values ('2', '1018'); -- 部门修改
insert into sys_role_menu values ('2', '1019'); -- 部门删除
-- 按钮权限-岗位管理
insert into sys_role_menu values ('2', '1020'); -- 岗位查询
insert into sys_role_menu values ('2', '1021'); -- 岗位新增
insert into sys_role_menu values ('2', '1022'); -- 岗位修改
insert into sys_role_menu values ('2', '1023'); -- 岗位删除
insert into sys_role_menu values ('2', '1024'); -- 岗位导出
-- 按钮权限-字典管理
insert into sys_role_menu values ('2', '1025'); -- 字典查询
insert into sys_role_menu values ('2', '1026'); -- 字典新增
insert into sys_role_menu values ('2', '1027'); -- 字典修改
insert into sys_role_menu values ('2', '1028'); -- 字典删除
insert into sys_role_menu values ('2', '1029'); -- 字典导出
-- 按钮权限-参数设置
insert into sys_role_menu values ('2', '1030'); -- 参数查询
insert into sys_role_menu values ('2', '1031'); -- 参数新增
insert into sys_role_menu values ('2', '1032'); -- 参数修改
insert into sys_role_menu values ('2', '1033'); -- 参数删除
insert into sys_role_menu values ('2', '1034'); -- 参数导出
-- 按钮权限-通知公告
insert into sys_role_menu values ('2', '1035'); -- 公告查询
insert into sys_role_menu values ('2', '1036'); -- 公告新增
insert into sys_role_menu values ('2', '1037'); -- 公告修改
insert into sys_role_menu values ('2', '1038'); -- 公告删除
-- 按钮权限-操作日志
insert into sys_role_menu values ('2', '1039'); -- 操作查询
insert into sys_role_menu values ('2', '1040'); -- 操作删除
insert into sys_role_menu values ('2', '1041'); -- 日志导出
-- 按钮权限-登录日志
insert into sys_role_menu values ('2', '1042'); -- 登录查询
insert into sys_role_menu values ('2', '1043'); -- 登录删除
insert into sys_role_menu values ('2', '1044'); -- 日志导出
insert into sys_role_menu values ('2', '1045'); -- 账户解锁
-- 按钮权限-在线用户
insert into sys_role_menu values ('2', '1046'); -- 在线查询
insert into sys_role_menu values ('2', '1047'); -- 批量强退
insert into sys_role_menu values ('2', '1048'); -- 单条强退
-- 按钮权限-定时任务
insert into sys_role_menu values ('2', '1049'); -- 任务查询
insert into sys_role_menu values ('2', '1050'); -- 任务新增
insert into sys_role_menu values ('2', '1051'); -- 任务修改
insert into sys_role_menu values ('2', '1052'); -- 任务删除
insert into sys_role_menu values ('2', '1053'); -- 状态修改
insert into sys_role_menu values ('2', '1054'); -- 任务导出
-- 按钮权限-代码生成
insert into sys_role_menu values ('2', '1055'); -- 生成查询
insert into sys_role_menu values ('2', '1056'); -- 生成修改
insert into sys_role_menu values ('2', '1057'); -- 生成删除
insert into sys_role_menu values ('2', '1058'); -- 导入代码
insert into sys_role_menu values ('2', '1059'); -- 预览代码
insert into sys_role_menu values ('2', '1060'); -- 生成代码
-- 按钮权限-日更助手
insert into sys_role_menu values ('2', '1061'); -- 文章查询
insert into sys_role_menu values ('2', '1062'); -- 文章新增
insert into sys_role_menu values ('2', '1063'); -- 文章删除
insert into sys_role_menu values ('2', '1064'); -- 智能体配置
insert into sys_role_menu values ('2', '1065'); -- 智能排版
insert into sys_role_menu values ('2', '1066'); -- 发布公众号
-- 按钮权限-发布记录
insert into sys_role_menu values ('2', '1067'); -- 记录查询
insert into sys_role_menu values ('2', '1068'); -- 记录详情
insert into sys_role_menu values ('2', '1069'); -- 记录删除
-- 按钮权限-积分管理
insert into sys_role_menu values ('2', '1070');  -- 积分查询
insert into sys_role_menu values ('2', '1071');  -- 明细查询
insert into sys_role_menu values ('2', '1072');  -- 规则查询
insert into sys_role_menu values ('2', '1073');  -- 规则新增
insert into sys_role_menu values ('2', '1074');  -- 规则修改
insert into sys_role_menu values ('2', '1075');  -- 规则删除
insert into sys_role_menu values ('2', '122');   -- 粉丝管理
insert into sys_role_menu values ('2', '1076');  -- 粉丝列表
insert into sys_role_menu values ('2', '1077');  -- 发放积分
insert into sys_role_menu values ('2', '1078');  -- 查看明细

-- 按钮权限-主机ID白名单
insert into sys_role_menu values ('2', '1079'); -- 白名单查询
insert into sys_role_menu values ('2', '1080'); -- 白名单新增
insert into sys_role_menu values ('2', '1081'); -- 白名单修改
insert into sys_role_menu values ('2', '1082'); -- 白名单删除
insert into sys_role_menu values ('2', '1083'); -- 白名单导出
-- 按钮权限-IP黑名单
insert into sys_role_menu values ('2', '1084'); -- 黑名单查询
insert into sys_role_menu values ('2', '1085'); -- 黑名单新增
insert into sys_role_menu values ('2', '1086'); -- 黑名单修改
insert into sys_role_menu values ('2', '1087'); -- 黑名单删除
insert into sys_role_menu values ('2', '1088'); -- 黑名单导出
-- 按钮权限-连接记录与在线列表
insert into sys_role_menu values ('2', '1089'); -- 连接记录查询
insert into sys_role_menu values ('2', '1090'); -- 连接记录删除
insert into sys_role_menu values ('2', '1091'); -- 在线主机查询
insert into sys_role_menu values ('2', '1092'); -- 在线主机下线
-- 按钮权限-WebSocket调试
insert into sys_role_menu values ('2', '126');  -- WebSocket调试
insert into sys_role_menu values ('2', '1093'); -- 调试工具查看
insert into sys_role_menu values ('2', '1094'); -- 发送消息
insert into sys_role_menu values ('2', '1095'); -- 清空消息
insert into sys_role_menu values ('2', '1096'); -- 导出日志
-- 只读权限角色（ID=3）拥有内容管理的全部权限，系统管理等模块只有查询权限
-- 一级菜单
insert into sys_role_menu values ('3', '1');    -- 内容管理
insert into sys_role_menu values ('3', '2');    -- 系统管理
insert into sys_role_menu values ('3', '3');    -- 系统监控
insert into sys_role_menu values ('3', '4');    -- 系统工具
insert into sys_role_menu values ('3', '6');    -- 积分管理
-- 二级菜单-内容管理
insert into sys_role_menu values ('3', '118');  -- 日更助手
insert into sys_role_menu values ('3', '119');  -- 发布记录
insert into sys_role_menu values ('3', '130');  -- 文档解析助手
-- 二级菜单-系统管理
insert into sys_role_menu values ('3', '100');  -- 用户管理
insert into sys_role_menu values ('3', '101');  -- 角色管理
insert into sys_role_menu values ('3', '102');  -- 菜单管理
insert into sys_role_menu values ('3', '103');  -- 部门管理
insert into sys_role_menu values ('3', '104');  -- 岗位管理
insert into sys_role_menu values ('3', '105');  -- 字典管理
insert into sys_role_menu values ('3', '106');  -- 参数设置
insert into sys_role_menu values ('3', '107');  -- 通知公告
insert into sys_role_menu values ('3', '108');  -- 日志管理
-- 二级菜单-系统监控
insert into sys_role_menu values ('3', '109');  -- 在线用户
insert into sys_role_menu values ('3', '110');  -- 定时任务
insert into sys_role_menu values ('3', '111');  -- 数据监控
insert into sys_role_menu values ('3', '112');  -- 服务监控
insert into sys_role_menu values ('3', '113');  -- 缓存监控
insert into sys_role_menu values ('3', '114');  -- 缓存列表
-- 二级菜单-系统工具
insert into sys_role_menu values ('3', '115');  -- 表单构建
insert into sys_role_menu values ('3', '116');  -- 代码生成
insert into sys_role_menu values ('3', '117');  -- 系统接口
-- 二级菜单-积分管理
insert into sys_role_menu values ('3', '120');  -- 积分总览
insert into sys_role_menu values ('3', '121');  -- 积分规则配置
-- 三级菜单-日志管理
insert into sys_role_menu values ('3', '500');  -- 操作日志
insert into sys_role_menu values ('3', '501');  -- 登录日志
-- 按钮权限-用户管理（只读）
insert into sys_role_menu values ('3', '1000'); -- 用户查询
-- 按钮权限-角色管理（只读）
insert into sys_role_menu values ('3', '1007'); -- 角色查询
-- 按钮权限-菜单管理（只读）
insert into sys_role_menu values ('3', '1012'); -- 菜单查询
-- 按钮权限-部门管理（只读）
insert into sys_role_menu values ('3', '1016'); -- 部门查询
-- 按钮权限-岗位管理（只读）
insert into sys_role_menu values ('3', '1020'); -- 岗位查询
-- 按钮权限-字典管理（只读）
insert into sys_role_menu values ('3', '1025'); -- 字典查询
-- 按钮权限-参数设置（只读）
insert into sys_role_menu values ('3', '1030'); -- 参数查询
-- 按钮权限-通知公告（只读）
insert into sys_role_menu values ('3', '1035'); -- 公告查询
-- 按钮权限-操作日志（只读）
insert into sys_role_menu values ('3', '1039'); -- 操作查询
-- 按钮权限-登录日志（只读）
insert into sys_role_menu values ('3', '1042'); -- 登录查询
-- 按钮权限-在线用户（只读）
insert into sys_role_menu values ('3', '1046'); -- 在线查询
-- 按钮权限-定时任务（只读）
insert into sys_role_menu values ('3', '1049'); -- 任务查询
-- 按钮权限-代码生成（只读）
insert into sys_role_menu values ('3', '1055'); -- 生成查询
-- 按钮权限-日更助手（全部权限）
insert into sys_role_menu values ('3', '1061'); -- 文章查询
insert into sys_role_menu values ('3', '1062'); -- 文章新增
insert into sys_role_menu values ('3', '1063'); -- 文章删除
insert into sys_role_menu values ('3', '1064'); -- 智能体配置
insert into sys_role_menu values ('3', '1065'); -- 智能排版
insert into sys_role_menu values ('3', '1066'); -- 发布公众号
-- 按钮权限-发布记录（全部权限）
insert into sys_role_menu values ('3', '1067'); -- 记录查询
insert into sys_role_menu values ('3', '1068'); -- 记录详情
insert into sys_role_menu values ('3', '1069'); -- 记录删除
-- 按钮权限-积分管理(只读)
insert into sys_role_menu values ('3', '1070');  -- 积分查询
insert into sys_role_menu values ('3', '1071');  -- 明细查询
insert into sys_role_menu values ('3', '1072');  -- 规则查询
insert into sys_role_menu values ('3', '122');   -- 粉丝管理
insert into sys_role_menu values ('3', '1076');  -- 粉丝列表
insert into sys_role_menu values ('3', '1078');  -- 查看明细
-- 普通用户角色（ID=10）只有内容管理和积分浏览权限
insert into sys_role_menu values ('10', '1');    -- 内容管理目录
insert into sys_role_menu values ('10', '118');   -- 日更助手菜单
insert into sys_role_menu values ('10', '119');   -- 发布记录菜单
insert into sys_role_menu values ('10', '130');   -- 文档解析助手菜单
insert into sys_role_menu values ('10', '1061'); -- 日更助手-文章查询
insert into sys_role_menu values ('10', '1062'); -- 日更助手-文章新增
insert into sys_role_menu values ('10', '1063'); -- 日更助手-文章删除
insert into sys_role_menu values ('10', '1064'); -- 日更助手-智能体配置
insert into sys_role_menu values ('10', '1065'); -- 日更助手-智能排版
insert into sys_role_menu values ('10', '1066'); -- 日更助手-发布公众号
insert into sys_role_menu values ('10', '1067'); -- 发布记录-记录查询
insert into sys_role_menu values ('10', '1068'); -- 发布记录-记录详情
insert into sys_role_menu values ('10', '1097'); -- 文档解析助手-解析查询
insert into sys_role_menu values ('10', '1098'); -- 文档解析助手-解析新增
insert into sys_role_menu values ('10', '1099'); -- 文档解析助手-解析删除
insert into sys_role_menu values ('10', '1069'); -- 发布记录-记录删除
insert into sys_role_menu values ('10', '6');    -- 积分管理
insert into sys_role_menu values ('10', '120');  -- 积分总览
insert into sys_role_menu values ('10', '1070');  -- 积分查询
insert into sys_role_menu values ('10', '1071');  -- 明细查询
insert into sys_role_menu values ('10', '1072');  -- 规则查询
insert into sys_role_menu values ('10', '8');    -- gitee管理
insert into sys_role_menu values ('10', '128');  -- gitee分析

-- ----------------------------
-- 8、角色和部门关联表  角色1-N部门
-- ----------------------------
drop table if exists sys_role_dept;
create table sys_role_dept (
  role_id   bigint(20) not null comment '角色ID',
  dept_id   bigint(20) not null comment '部门ID',
  primary key(role_id, dept_id)
) engine=innodb comment = '角色和部门关联表';

-- ----------------------------
-- 初始化-角色和部门关联表数据
-- ----------------------------
-- 管理员角色关联所有部门
insert into sys_role_dept values ('2', '100');
insert into sys_role_dept values ('2', '101');
insert into sys_role_dept values ('2', '105');
insert into sys_role_dept values ('2', '106');
insert into sys_role_dept values ('2', '107');


-- ----------------------------
-- 9、用户与岗位关联表  用户1-N岗位
-- ----------------------------
drop table if exists sys_user_post;
create table sys_user_post
(
  user_id   bigint(20) not null comment '用户ID',
  post_id   bigint(20) not null comment '岗位ID',
  primary key (user_id, post_id)
) engine=innodb comment = '用户与岗位关联表';

-- ----------------------------
-- 初始化-用户与岗位关联表数据
-- ----------------------------
insert into sys_user_post values ('1', '1');


-- ----------------------------
-- 10、操作日志记录
-- ----------------------------
drop table if exists sys_oper_log;
create table sys_oper_log (
  oper_id           bigint(20)      not null auto_increment    comment '日志主键',
  title             varchar(50)     default ''                 comment '模块标题',
  business_type     int(2)          default 0                  comment '业务类型（0其它 1新增 2修改 3删除）',
  method            varchar(200)    default ''                 comment '方法名称',
  request_method    varchar(10)     default ''                 comment '请求方式',
  operator_type     int(1)          default 0                  comment '操作类别（0其它 1后台用户 2手机端用户）',
  oper_name         varchar(50)     default ''                 comment '操作人员',
  dept_name         varchar(50)     default ''                 comment '部门名称',
  oper_url          varchar(255)    default ''                 comment '请求URL',
  oper_ip           varchar(128)    default ''                 comment '主机地址',
  oper_location     varchar(255)    default ''                 comment '操作地点',
  oper_param        varchar(2000)   default ''                 comment '请求参数',
  json_result       varchar(2000)   default ''                 comment '返回参数',
  status            int(1)          default 0                  comment '操作状态（0正常 1异常）',
  error_msg         varchar(2000)   default ''                 comment '错误消息',
  oper_time         datetime                                   comment '操作时间',
  cost_time         bigint(20)      default 0                  comment '消耗时间',
  primary key (oper_id),
  key idx_sys_oper_log_bt (business_type),
  key idx_sys_oper_log_s  (status),
  key idx_sys_oper_log_ot (oper_time)
) engine=innodb auto_increment=100 comment = '操作日志记录';


-- ----------------------------
-- 11、字典类型表
-- ----------------------------
drop table if exists sys_dict_type;
create table sys_dict_type
(
  dict_id          bigint(20)      not null auto_increment    comment '字典主键',
  dict_name        varchar(100)    default ''                 comment '字典名称',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_id),
  unique (dict_type)
) engine=innodb auto_increment=100 comment = '字典类型表';

insert into sys_dict_type values(1,  '用户性别', 'sys_user_sex',        '0', 'admin', sysdate(), '', null, '用户性别列表');
insert into sys_dict_type values(2,  '菜单状态', 'sys_show_hide',       '0', 'admin', sysdate(), '', null, '菜单状态列表');
insert into sys_dict_type values(3,  '系统开关', 'sys_normal_disable',  '0', 'admin', sysdate(), '', null, '系统开关列表');
insert into sys_dict_type values(4,  '任务状态', 'sys_job_status',      '0', 'admin', sysdate(), '', null, '任务状态列表');
insert into sys_dict_type values(5,  '任务分组', 'sys_job_group',       '0', 'admin', sysdate(), '', null, '任务分组列表');
insert into sys_dict_type values(6,  '系统是否', 'sys_yes_no',          '0', 'admin', sysdate(), '', null, '系统是否列表');
insert into sys_dict_type values(7,  '通知类型', 'sys_notice_type',     '0', 'admin', sysdate(), '', null, '通知类型列表');
insert into sys_dict_type values(8,  '通知状态', 'sys_notice_status',   '0', 'admin', sysdate(), '', null, '通知状态列表');
insert into sys_dict_type values(9,  '操作类型', 'sys_oper_type',       '0', 'admin', sysdate(), '', null, '操作类型列表');
insert into sys_dict_type values(10, '系统状态', 'sys_common_status',   '0', 'admin', sysdate(), '', null, '登录状态列表');


-- ----------------------------
-- 12、字典数据表
-- ----------------------------
drop table if exists sys_dict_data;
create table sys_dict_data
(
  dict_code        bigint(20)      not null auto_increment    comment '字典编码',
  dict_sort        int(4)          default 0                  comment '字典排序',
  dict_label       varchar(100)    default ''                 comment '字典标签',
  dict_value       varchar(100)    default ''                 comment '字典键值',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  css_class        varchar(100)    default null               comment '样式属性（其他样式扩展）',
  list_class       varchar(100)    default null               comment '表格回显样式',
  is_default       char(1)         default 'N'                comment '是否默认（Y是 N否）',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_code)
) engine=innodb auto_increment=100 comment = '字典数据表';

insert into sys_dict_data values(1,  1,  '男',       '0',       'sys_user_sex',        '',   '',        'Y', '0', 'admin', sysdate(), '', null, '性别男');
insert into sys_dict_data values(2,  2,  '女',       '1',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别女');
insert into sys_dict_data values(3,  3,  '未知',     '2',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别未知');
insert into sys_dict_data values(4,  1,  '显示',     '0',       'sys_show_hide',       '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '显示菜单');
insert into sys_dict_data values(5,  2,  '隐藏',     '1',       'sys_show_hide',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '隐藏菜单');
insert into sys_dict_data values(6,  1,  '正常',     '0',       'sys_normal_disable',  '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(7,  2,  '停用',     '1',       'sys_normal_disable',  '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(8,  1,  '正常',     '0',       'sys_job_status',      '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(9,  2,  '暂停',     '1',       'sys_job_status',      '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(10, 1,  '默认',     'DEFAULT', 'sys_job_group',       '',   '',        'Y', '0', 'admin', sysdate(), '', null, '默认分组');
insert into sys_dict_data values(11, 2,  '系统',     'SYSTEM',  'sys_job_group',       '',   '',        'N', '0', 'admin', sysdate(), '', null, '系统分组');
insert into sys_dict_data values(12, 1,  '是',       'Y',       'sys_yes_no',          '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '系统默认是');
insert into sys_dict_data values(13, 2,  '否',       'N',       'sys_yes_no',          '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '系统默认否');
insert into sys_dict_data values(14, 1,  '通知',     '1',       'sys_notice_type',     '',   'warning', 'Y', '0', 'admin', sysdate(), '', null, '通知');
insert into sys_dict_data values(15, 2,  '公告',     '2',       'sys_notice_type',     '',   'success', 'N', '0', 'admin', sysdate(), '', null, '公告');
insert into sys_dict_data values(16, 1,  '正常',     '0',       'sys_notice_status',   '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(17, 2,  '关闭',     '1',       'sys_notice_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '关闭状态');
insert into sys_dict_data values(18, 99, '其他',     '0',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '其他操作');
insert into sys_dict_data values(19, 1,  '新增',     '1',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '新增操作');
insert into sys_dict_data values(20, 2,  '修改',     '2',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '修改操作');
insert into sys_dict_data values(21, 3,  '删除',     '3',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '删除操作');
insert into sys_dict_data values(22, 4,  '授权',     '4',       'sys_oper_type',       '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '授权操作');
insert into sys_dict_data values(23, 5,  '导出',     '5',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导出操作');
insert into sys_dict_data values(24, 6,  '导入',     '6',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导入操作');
insert into sys_dict_data values(25, 7,  '强退',     '7',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '强退操作');
insert into sys_dict_data values(26, 8,  '生成代码', '8',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '生成操作');
insert into sys_dict_data values(27, 9,  '清空数据', '9',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '清空操作');
insert into sys_dict_data values(28, 1,  '成功',     '0',       'sys_common_status',   '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(29, 2,  '失败',     '1',       'sys_common_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');


-- ----------------------------
-- 13、参数配置表
-- ----------------------------
drop table if exists sys_config;
create table sys_config (
  config_id         int(5)          not null auto_increment    comment '参数主键',
  config_name       varchar(100)    default ''                 comment '参数名称',
  config_key        varchar(100)    default ''                 comment '参数键名',
  config_value      varchar(500)    default ''                 comment '参数键值',
  config_type       char(1)         default 'N'                comment '系统内置（Y是 N否）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (config_id)
) engine=innodb auto_increment=100 comment = '参数配置表';

insert into sys_config values(1, '主框架页-默认皮肤样式名称',     'sys.index.skinName',               'skin-blue',     'Y', 'admin', sysdate(), '', null, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow' );
insert into sys_config values(2, '用户管理-账号初始密码',         'sys.user.initPassword',            '123456',        'Y', 'admin', sysdate(), '', null, '初始化密码 123456' );
insert into sys_config values(3, '主框架页-侧边栏主题',           'sys.index.sideTheme',              'theme-dark',    'Y', 'admin', sysdate(), '', null, '深色主题theme-dark，浅色主题theme-light' );
insert into sys_config values(4, '账号自助-验证码开关',           'sys.account.captchaEnabled',       'false',          'Y', 'admin', sysdate(), '', null, '是否开启验证码功能（true开启，false关闭）');
insert into sys_config values(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser',         'true',         'Y', 'admin', sysdate(), '', null, '是否开启注册用户功能（true开启，false关闭）');
insert into sys_config values(6, '用户登录-黑名单列表',           'sys.login.blackIPList',            '',              'Y', 'admin', sysdate(), '', null, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');
insert into sys_config values(7, '用户管理-初始密码修改策略',     'sys.account.initPasswordModify',   '1',             'Y', 'admin', sysdate(), '', null, '0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框');
insert into sys_config values(8, '用户管理-账号密码更新周期',     'sys.account.passwordValidateDays', '0',             'Y', 'admin', sysdate(), '', null, '密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');


-- ----------------------------
-- 14、系统访问记录
-- ----------------------------
drop table if exists sys_logininfor;
create table sys_logininfor (
  info_id        bigint(20)     not null auto_increment   comment '访问ID',
  user_name      varchar(50)    default ''                comment '用户账号',
  ipaddr         varchar(128)   default ''                comment '登录IP地址',
  login_location varchar(255)   default ''                comment '登录地点',
  browser        varchar(50)    default ''                comment '浏览器类型',
  os             varchar(50)    default ''                comment '操作系统',
  status         char(1)        default '0'               comment '登录状态（0成功 1失败）',
  msg            varchar(255)   default ''                comment '提示消息',
  login_time     datetime                                 comment '访问时间',
  primary key (info_id),
  key idx_sys_logininfor_s  (status),
  key idx_sys_logininfor_lt (login_time)
) engine=innodb auto_increment=100 comment = '系统访问记录';


-- ----------------------------
-- 15、定时任务调度表
-- ----------------------------
drop table if exists sys_job;
create table sys_job (
  job_id              bigint(20)    not null auto_increment    comment '任务ID',
  job_name            varchar(64)   default ''                 comment '任务名称',
  job_group           varchar(64)   default 'DEFAULT'          comment '任务组名',
  invoke_target       varchar(500)  not null                   comment '调用目标字符串',
  cron_expression     varchar(255)  default ''                 comment 'cron执行表达式',
  misfire_policy      varchar(20)   default '3'                comment '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  concurrent          char(1)       default '1'                comment '是否并发执行（0允许 1禁止）',
  status              char(1)       default '0'                comment '状态（0正常 1暂停）',
  create_by           varchar(64)   default ''                 comment '创建者',
  create_time         datetime                                 comment '创建时间',
  update_by           varchar(64)   default ''                 comment '更新者',
  update_time         datetime                                 comment '更新时间',
  remark              varchar(500)  default ''                 comment '备注信息',
  primary key (job_id, job_name, job_group)
) engine=innodb auto_increment=100 comment = '定时任务调度表';

insert into sys_job values(1, '系统默认（无参）', 'DEFAULT', 'WxFbsirTask.WxFbsirNoParams',        '0/10 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(2, '系统默认（有参）', 'DEFAULT', 'WxFbsirTask.WxFbsirParams(\'WxFbsir\')',  '0/15 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(3, '系统默认（多参）', 'DEFAULT', 'WxFbsirTask.WxFbsirMultipleParams(\'WxFbsir\', true, 2000L, 316.50D, 100)',  '0/20 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 16、定时任务调度日志表
-- ----------------------------
drop table if exists sys_job_log;
create table sys_job_log (
  job_log_id          bigint(20)     not null auto_increment    comment '任务日志ID',
  job_name            varchar(64)    not null                   comment '任务名称',
  job_group           varchar(64)    not null                   comment '任务组名',
  invoke_target       varchar(500)   not null                   comment '调用目标字符串',
  job_message         varchar(500)                              comment '日志信息',
  status              char(1)        default '0'                comment '执行状态（0正常 1失败）',
  exception_info      varchar(2000)  default ''                 comment '异常信息',
  create_time         datetime                                  comment '创建时间',
  primary key (job_log_id)
) engine=innodb comment = '定时任务调度日志表';


-- ----------------------------
-- 17、通知公告表
-- ----------------------------
drop table if exists sys_notice;
create table sys_notice (
  notice_id         int(4)          not null auto_increment    comment '公告ID',
  notice_title      varchar(50)     not null                   comment '公告标题',
  notice_type       char(1)         not null                   comment '公告类型（1通知 2公告）',
  notice_content    longblob        default null               comment '公告内容',
  status            char(1)         default '0'                comment '公告状态（0正常 1关闭）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(255)    default null               comment '备注',
  primary key (notice_id)
) engine=innodb auto_increment=10 comment = '通知公告表';

-- ----------------------------
-- 初始化-公告信息表数据
-- ----------------------------
insert into sys_notice values('1', '系统公告：欢迎使用管理系统', '2', '欢迎使用本管理系统', '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('2', '维护通知：系统定期维护通知', '1', '系统将定期进行维护更新',   '0', 'admin', sysdate(), '', null, '管理员');


-- ----------------------------
-- 18、代码生成业务表
-- ----------------------------
drop table if exists gen_table;
create table gen_table (
  table_id          bigint(20)      not null auto_increment    comment '编号',
  table_name        varchar(200)    default ''                 comment '表名称',
  table_comment     varchar(500)    default ''                 comment '表描述',
  sub_table_name    varchar(64)     default null               comment '关联子表的表名',
  sub_table_fk_name varchar(64)     default null               comment '子表关联的外键名',
  class_name        varchar(100)    default ''                 comment '实体类名称',
  tpl_category      varchar(200)    default 'crud'             comment '使用的模板（crud单表操作 tree树表操作）',
  tpl_web_type      varchar(30)     default ''                 comment '前端模板类型（element-ui模版 element-plus模版）',
  package_name      varchar(100)                               comment '生成包路径',
  module_name       varchar(30)                                comment '生成模块名',
  business_name     varchar(30)                                comment '生成业务名',
  function_name     varchar(50)                                comment '生成功能名',
  function_author   varchar(50)                                comment '生成功能作者',
  gen_type          char(1)         default '0'                comment '生成代码方式（0zip压缩包 1自定义路径）',
  gen_path          varchar(200)    default '/'                comment '生成路径（不填默认项目路径）',
  options           varchar(1000)                              comment '其它生成选项',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (table_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表';


-- ----------------------------
-- 19、代码生成业务表字段
-- ----------------------------
drop table if exists gen_table_column;
create table gen_table_column (
  column_id         bigint(20)      not null auto_increment    comment '编号',
  table_id          bigint(20)                                 comment '归属表编号',
  column_name       varchar(200)                               comment '列名称',
  column_comment    varchar(500)                               comment '列描述',
  column_type       varchar(100)                               comment '列类型',
  java_type         varchar(500)                               comment 'JAVA类型',
  java_field        varchar(200)                               comment 'JAVA字段名',
  is_pk             char(1)                                    comment '是否主键（1是）',
  is_increment      char(1)                                    comment '是否自增（1是）',
  is_required       char(1)                                    comment '是否必填（1是）',
  is_insert         char(1)                                    comment '是否为插入字段（1是）',
  is_edit           char(1)                                    comment '是否编辑字段（1是）',
  is_list           char(1)                                    comment '是否列表字段（1是）',
  is_query          char(1)                                    comment '是否查询字段（1是）',
  query_type        varchar(200)    default 'EQ'               comment '查询方式（等于、不等于、大于、小于、范围）',
  html_type         varchar(200)                               comment '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  dict_type         varchar(200)    default ''                 comment '字典类型',
  sort              int                                        comment '排序',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (column_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表字段';


-- =============================================
-- 业务模块：日更助手
-- =============================================

-- ----------------------------
-- 20、日更助手文章表
-- ----------------------------
DROP TABLE IF EXISTS `daily_article`;
CREATE TABLE `daily_article` (
  `id`  bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文章ID',
  `user_id`  bigint(20) NOT NULL COMMENT '用户ID',
  `article_title`  varchar(500) NOT NULL COMMENT '原始文章标题',
  `optimized_content`  longtext COMMENT '优化后的文章内容（来自腾讯元器智能体）',
  `model1_content`  longtext COMMENT '大模型1未优化的文章内容',
  `model2_content`  longtext COMMENT '大模型2未优化的文章内容',
  `model3_content`  longtext COMMENT '大模型3未优化的文章内容',
  `model1_name`  varchar(100) DEFAULT NULL COMMENT '大模型1名称',
  `model2_name`  varchar(100) DEFAULT NULL COMMENT '大模型2名称',
  `model3_name`  varchar(100) DEFAULT NULL COMMENT '大模型3名称',
  `agent_task_id`  varchar(200) DEFAULT NULL COMMENT '腾讯元器智能体任务ID',
  `process_status`  tinyint(1) NOT NULL DEFAULT 0 COMMENT '处理状态：0-处理中，1-已完成，2-失败',
  `error_message`  varchar(1000) DEFAULT NULL COMMENT '错误信息',
  `selected_models`  varchar(50) DEFAULT '1,2,3' COMMENT '已选择的模型，格式如"1,2,3"',
  `publish_count`  int(11) DEFAULT 0 COMMENT '发布次数',
  `create_by`  varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time`  datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`  varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time`  datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`  varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE COMMENT '用户ID索引',
  KEY `idx_create_time` (`create_time`) USING BTREE COMMENT '创建时间索引',
  KEY `idx_process_status` (`process_status`) USING BTREE COMMENT '处理状态索引',
  KEY `idx_agent_task_id` (`agent_task_id`) USING BTREE COMMENT '智能体任务ID索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日更助手文章表';


-- ----------------------------
-- 21、文档解析表
-- ----------------------------
DROP TABLE IF EXISTS `document_parse`;
CREATE TABLE `document_parse` (
  `id`  bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文档解析ID',
  `user_id`  bigint(20) NOT NULL COMMENT '用户ID',
  `document_id`  varchar(200) NOT NULL COMMENT '文档ID（自动生成）',
  `document_name`  varchar(500) DEFAULT NULL COMMENT '文档名称',
  `prompt`  text COMMENT '提示词',
  `parsed_content`  longtext COMMENT '解析后的内容（来自腾讯元器智能体）',
  `agent_task_id`  varchar(200) DEFAULT NULL COMMENT '腾讯元器智能体任务ID',
  `process_status`  tinyint(1) NOT NULL DEFAULT 0 COMMENT '处理状态：0-处理中，1-已完成，2-失败',
  `error_message`  varchar(1000) DEFAULT NULL COMMENT '错误信息',
  `create_by`  varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time`  datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`  varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time`  datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`  varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE COMMENT '用户ID索引',
  KEY `idx_document_id` (`document_id`) USING BTREE COMMENT '文档ID索引',
  KEY `idx_create_time` (`create_time`) USING BTREE COMMENT '创建时间索引',
  KEY `idx_process_status` (`process_status`) USING BTREE COMMENT '处理状态索引',
  KEY `idx_agent_task_id` (`agent_task_id`) USING BTREE COMMENT '智能体任务ID索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档解析表';


-- ----------------------------
-- 22、腾讯元器智能体配置表
-- ----------------------------
DROP TABLE IF EXISTS `yuanqi_agent_config`;
CREATE TABLE `yuanqi_agent_config` (
  `id`  bigint(20) NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `user_id`  bigint(20) NOT NULL COMMENT '用户ID',
  `agent_id`  varchar(200) NOT NULL COMMENT '腾讯元器智能体ID',
  `agent_name`  varchar(100) DEFAULT NULL COMMENT '智能体名称',
  `api_key`  varchar(500) DEFAULT NULL COMMENT 'API密钥（加密存储）',
  `api_endpoint`  varchar(500) DEFAULT NULL COMMENT 'API端点URL',
  `is_active`  tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
  `config_json`  json DEFAULT NULL COMMENT '其他配置（JSON格式）',
  `create_by`  varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time`  datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`  varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time`  datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`  varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE COMMENT '用户ID索引',
  KEY `idx_agent_id` (`agent_id`) USING BTREE COMMENT '智能体ID索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='腾讯元器智能体配置表';

-- ----------------------------
-- 22、微信公众号配置表
-- ----------------------------
DROP TABLE IF EXISTS `wc_office_account`;
CREATE TABLE `wc_office_account` (
                                     `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '配置ID',
                                     `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                                     `app_id` varchar(500) DEFAULT NULL COMMENT '开发者ID（加密存储）',
                                     `app_secret` varchar(500) DEFAULT NULL COMMENT '开发者密钥（加密存储）',
                                     `author_name` varchar(100) DEFAULT NULL COMMENT '作者名称（发布文章时显示的作者）',
                                     `pic_url` varchar(500) DEFAULT NULL COMMENT '素材封面图URL（必填，用于文章封面）',
                                     `media_id` varchar(100) DEFAULT NULL COMMENT '素材ID（保存配置时上传封面图获取，必填）',
                                     `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
                                     `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
                                     `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
                                     `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                     PRIMARY KEY (`id`) USING BTREE,
                                     UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE COMMENT '用户ID唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信公众号配置表';


-- ----------------------------
-- 23、公众号文章发布记录表
-- ----------------------------
DROP TABLE IF EXISTS `wc_office_publish_record`;
CREATE TABLE `wc_office_publish_record` (
                                            `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '记录ID',
                                            `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                                            `article_id` bigint(20) NOT NULL COMMENT '关联的日更助手文章ID',
                                            `office_account_id` bigint(20) NOT NULL COMMENT '公众号配置ID',
                                            `content_type` varchar(50) DEFAULT NULL COMMENT '发布的内容类型：optimized/model1/model2/model3/layout',
                                            `publish_status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '发布状态：0-发布中，1-成功，2-失败',
                                            `media_id` varchar(200) DEFAULT NULL COMMENT '微信素材ID',
                                            `error_message` varchar(1000) DEFAULT NULL COMMENT '失败原因',
                                            `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
                                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                            `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
                                            `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                            `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                            `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
                                            PRIMARY KEY (`id`) USING BTREE,
                                            KEY `idx_user_id` (`user_id`) USING BTREE COMMENT '用户ID索引',
                                            KEY `idx_article_id` (`article_id`) USING BTREE COMMENT '文章ID索引',
                                            KEY `idx_office_account_id` (`office_account_id`) USING BTREE COMMENT '公众号配置ID索引',
                                            KEY `idx_create_time` (`create_time`) USING BTREE COMMENT '创建时间索引',
                                            KEY `idx_del_flag` (`del_flag`) USING BTREE COMMENT '删除标志索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公众号文章发布记录表';

-- ----------------------------
-- 24、积分规则配置表
-- ----------------------------
DROP TABLE IF EXISTS `wx_points_rule`;
CREATE TABLE `wx_points_rule` (
                                            `rule_id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '规则ID',
                                            `rule_code` VARCHAR(50) NOT NULL COMMENT '规则编码（唯一标识，用于业务索引）',
                                            `rule_name` VARCHAR(100) NOT NULL COMMENT '规则名称（用于显示，可修改）',
                                            `points_value` INT(11) NOT NULL COMMENT '积分值（正数为奖励，负数为扣减）',
                                            `limit_type` VARCHAR(20) DEFAULT NULL COMMENT '限频类型：DAILY/WEEKLY/MONTHLY/TOTAL',
                                            `limit_value` INT(11) DEFAULT NULL COMMENT '限频次数',
                                            `max_amount` INT(11) DEFAULT NULL COMMENT '累计上限',
                                            `status` CHAR(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
                                            `sort_order` INT(11) DEFAULT 0 COMMENT '排序',
                                            `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
                                            `create_by` VARCHAR(64) DEFAULT '' COMMENT '创建者',
                                            `create_time` DATETIME NOT NULL COMMENT '创建时间',
                                            `update_by` VARCHAR(64) DEFAULT '' COMMENT '更新者',
                                            `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
                                            PRIMARY KEY (`rule_id`),
                                            UNIQUE KEY `uk_rule_code` (`rule_code`),
                                            KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分规则配置表';

-- ----------------------------
-- 25、积分明细记录表
-- ----------------------------
DROP TABLE IF EXISTS `wx_points_record`;
CREATE TABLE `wx_points_record` (
  `record_id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `rule_code` VARCHAR(50) NOT NULL COMMENT '规则编码（关联wx_points_rule.rule_code）',
  `change_amount` INT(11) NOT NULL COMMENT '变动金额（正数为增加，负数为扣减）',
  `balance_before` INT(11) NOT NULL COMMENT '变动前余额',
  `balance_after` INT(11) NOT NULL COMMENT '变动后余额',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注说明',
  `create_by` VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_by` VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`record_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_rule_code` (`rule_code`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  CONSTRAINT `fk_points_record_rule` FOREIGN KEY (`rule_code`) REFERENCES `wx_points_rule` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分明细记录表';

-- ----------------------------
-- 26、积分统计表
-- ----------------------------
DROP TABLE IF EXISTS `wx_points_statistics`;
CREATE TABLE `wx_points_statistics` (
  `stat_id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '统计ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `stat_date` DATE NOT NULL COMMENT '统计日期',
  `points_gain` INT(11) DEFAULT 0 COMMENT '当日获得积分',
  `points_used` INT(11) DEFAULT 0 COMMENT '当日使用积分',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`stat_id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `stat_date`),
  KEY `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分统计表';

-- ----------------------------
-- 初始化积分规则数据
-- ----------------------------
INSERT INTO `wx_points_rule` (`rule_code`, `rule_name`, `points_value`, `limit_type`, `limit_value`, `max_amount`, `status`, `sort_order`, `remark`, `create_by`, `create_time`) VALUES
('FIRST_LOGIN_BONUS', '首次登录奖励', 5000, NULL, NULL, NULL, '0', 2, '用户首次登录奖励', 'admin', NOW()),
('DAILY_LOGIN', '每日登录', 10, 'DAILY', 1, NULL, '0', 1, '用户每日首次登录奖励', 'admin', NOW()),
('USE_DAILY_ASSISTANT', '使用日更助手', -1, NULL, NULL, NULL, '0', 2, '使用日更助手生成文章时扣减', 'admin', NOW()),
('ARTICLE_LAYOUT', '智能排版', -1, NULL, NULL, NULL, '0', 3, '使用智能排版功能时扣减', 'admin', NOW()),
('TEMPLATE_PUBLISH', '模板上架', 50, NULL, NULL, NULL, '0', 4, '用户上架模板到市场奖励', 'admin', NOW()),
('TEMPLATE_BUY', '模板购买', 0, NULL, NULL, NULL, '0', 5, '购买模板时扣减', 'admin', NOW()),
('TEMPLATE_REWARD', '模板分成', 0, NULL, NULL, NULL, '0', 6, '模板被购买时的作者分成', 'admin', NOW()),
('ADMIN_GRANT', '管理员发放', 0, NULL, NULL, NULL, '0', 7, '管理员给用户发放积分', 'admin', NOW()),
('GITEE_ANALYSIS', 'gitee分析', -1, '2', NULL, NULL, '0', 2, 'gitee分析', 'admin', NOW());

-- =============================================
-- 业务模块：Gitee管理
-- =============================================

-- ----------------------------
-- 27、Gitee绑定表
-- ----------------------------
drop table if exists gitee_bind;
create table gitee_bind (
  bind_id           bigint(20)      not null auto_increment    comment '绑定ID',
  user_id           bigint(20)      not null                   comment '用户ID',
  gitee_user_id     varchar(64)     not null                   comment 'Gitee用户ID',
  gitee_username    varchar(100)    not null                   comment 'Gitee用户名',
  gitee_avatar      varchar(255)    default ''                 comment 'Gitee头像',
  bind_time         datetime                                   comment '绑定时间',
  primary key (bind_id),
  unique key uk_gitee_bind_user (user_id),
  unique key uk_gitee_bind_gitee (gitee_user_id),
  key idx_gitee_bind_user (user_id),
  constraint fk_gitee_bind_user foreign key (user_id) references sys_user (user_id) on delete cascade
) engine=innodb comment = 'Gitee绑定表';

-- 删除用户时同步清理Gitee绑定（软删场景）
-- 注意：触发器由应用层处理，避免 MySQL 权限问题
-- 在 Java 代码中删除用户时，同时执行：DELETE FROM gitee_bind WHERE user_id = ?

-- ----------------------------
-- 28、Gitee评测报告表
-- ----------------------------
drop table if exists gitee_analysis_report;
create table gitee_analysis_report (
  report_id         bigint(20)      not null auto_increment    comment '报告ID',
  user_id           bigint(20)      not null                   comment '用户ID',
  profile_score     int(10)         default null               comment '形象评分',
  profile_level     varchar(10)     default ''                 comment '形象等级',
  community_score   int(10)         default null               comment '社区评分',
  community_level   varchar(10)     default ''                 comment '社区等级',
  tech_score        int(10)         default null               comment '技术评分',
  tech_level        varchar(10)     default ''                 comment '技术等级',
  total_score       int(10)         default null               comment '综合评分',
  total_level       varchar(10)     default ''                 comment '综合等级',
  report_time       datetime                                   comment '评测时间',
  primary key (report_id),
  key idx_gitee_report_user (user_id),
  constraint fk_gitee_report_user foreign key (user_id) references sys_user (user_id)
) engine=innodb comment = 'Gitee评测报告表';

-- ----------------------------
-- 29、Gitee模块使用统计报表
-- ----------------------------
drop table if exists gitee_usage_report;
create table gitee_usage_report (
  report_id               bigint(20)      not null auto_increment    comment '报表ID',
  report_date             date            not null                   comment '统计日期',
  new_bind_count          int(10)         default 0                  comment '当日新增绑定用户数',
  daily_evaluation_count  int(10)         default 0                  comment '当日评测总次数',
  daily_active_user_count int(10)         default 0                  comment '当日活跃评测用户数',
  total_bind_count        int(10)         default 0                  comment '累计绑定用户数',
  score_distribution      text                                      comment '评分区间分布(JSON)',
  create_time             datetime                                  comment '创建时间',
  update_time             datetime                                  comment '更新时间',
  primary key (report_id),
  unique key uk_gitee_usage_date (report_date),
  key idx_gitee_usage_date (report_date)
) engine=innodb comment = 'Gitee模块使用统计报表';

-- ========================================
-- Engine WebSocket 通信相关数据库表
-- 版本: 1.2.0
-- 创建日期: 2025-12-15
-- ========================================

SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 30、主机白名单表
-- ----------------------------
DROP TABLE IF EXISTS `ws_host_whitelist`;
CREATE TABLE `ws_host_whitelist` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `host_id` varchar(50) NOT NULL COMMENT '主机ID（用户申请后由管理员分配）',
    `host_name` varchar(100) DEFAULT NULL COMMENT '主机名称/描述',
    `owner_name` varchar(50) DEFAULT NULL COMMENT '负责人姓名',
    `owner_contact` varchar(100) DEFAULT NULL COMMENT '负责人联系方式',
    `is_team` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否团队主机：0-个人，1-团队（仅用于统计分类）',
    `team_name` varchar(100) DEFAULT NULL COMMENT '团队名称（is_team=1时填写）',
    `allowed_ips` varchar(500) DEFAULT NULL COMMENT '允许的IP地址列表（逗号分隔，为空表示不限制）',
    `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `expire_time` datetime DEFAULT NULL COMMENT '过期时间（为空表示永不过期）',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `del_flag` tinyint(4) NOT NULL DEFAULT 0 COMMENT '删除标志：0-正常，1-已删除',
    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_host_id` (`host_id`),
    INDEX `idx_is_team` (`is_team`),
    INDEX `idx_status` (`status`),
    INDEX `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WebSocket主机白名单表';

-- 插入示例数据
INSERT INTO `ws_host_whitelist` (`host_id`, `host_name`, `owner_name`, `is_team`, `team_name`, `status`, `remark`) VALUES
('engine-001', '默认Engine节点', '管理员', 0, NULL, 1, '默认配置的Engine节点，对应application.yml中的host-id'),
('engine-dev-001', '开发测试节点1', '张三', 0, NULL, 1, '开发环境测试用'),
('engine-prod-001', '生产节点-运维组', '运维组', 1, '运维团队', 1, '生产环境主节点');

-- ----------------------------
-- 31、IP黑名单表
-- ----------------------------
DROP TABLE IF EXISTS `ws_ip_blacklist`;
CREATE TABLE `ws_ip_blacklist` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ip_address` varchar(50) NOT NULL COMMENT 'IP地址',
    `block_reason` varchar(255) DEFAULT NULL COMMENT '封禁原因',
    `block_type` tinyint DEFAULT 1 COMMENT '封禁类型：1-临时封禁（有过期时间），2-永久封禁',
    `expire_time` datetime DEFAULT NULL COMMENT '解封时间（block_type=1时有效，为空表示永久封禁）',
    `hit_count` int DEFAULT 0 COMMENT '命中次数（该IP尝试连接被拒绝的次数）',
    `last_hit_time` datetime DEFAULT NULL COMMENT '最后命中时间',
    `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1-生效，0-已解除',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `del_flag` tinyint(4) NOT NULL DEFAULT 0 COMMENT '删除标志：0-正常，1-已删除',
    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_ip_address` (`ip_address`),
    INDEX `idx_status` (`status`),
    INDEX `idx_expire_time` (`expire_time`),
    INDEX `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WebSocket IP黑名单表';

-- ----------------------------
-- 32、WebSocket连接记录表
-- ----------------------------
DROP TABLE IF EXISTS `ws_connection_log`;
CREATE TABLE `ws_connection_log` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id` varchar(64) NOT NULL COMMENT 'WebSocket会话ID',
    `host_id` varchar(50) DEFAULT NULL COMMENT '主机ID（客户端声称的，注册前可能为空）',
    `device_id` varchar(128) DEFAULT NULL COMMENT '设备指纹ID（基于硬件信息生成，注册时上报）',
    `engine_version` varchar(20) DEFAULT NULL COMMENT 'Engine版本号（注册时上报）',
    `remote_ip` varchar(50) NOT NULL COMMENT '客户端IP地址',
    `remote_port` int DEFAULT NULL COMMENT '客户端端口',
    `request_uri` varchar(255) DEFAULT NULL COMMENT '请求URI路径',
    `os_name` varchar(50) DEFAULT NULL COMMENT '操作系统名称（注册时上报）',
    `os_version` varchar(50) DEFAULT NULL COMMENT '操作系统版本（注册时上报）',
    `java_version` varchar(30) DEFAULT NULL COMMENT 'Java版本（注册时上报）',
    `hostname` varchar(100) DEFAULT NULL COMMENT '客户端主机名（注册时上报）',
    `mac_address` varchar(50) DEFAULT NULL COMMENT 'MAC地址（注册时上报）',
    `connect_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '连接时间（TCP握手成功时间）',
    `register_time` datetime DEFAULT NULL COMMENT '注册成功时间（验证通过时间）',
    `disconnect_time` datetime DEFAULT NULL COMMENT '断开时间',
    `duration_seconds` bigint DEFAULT NULL COMMENT '连接持续时间（秒，断开时计算）',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-连接中，1-已注册，2-正常断开，3-异常断开，4-被拒绝（白名单），5-被拒绝（黑名单），6-被拒绝（重复连接），7-被管理员断开，8-主节点重启导致断开',
    `reject_reason` varchar(255) DEFAULT NULL COMMENT '拒绝/断开原因',
    `error_code` varchar(10) DEFAULT NULL COMMENT '错误码（E1001-E9999：E1xxx认证错误，E2xxx授权错误，E3xxx连接错误，E4xxx系统错误）',
    `close_code` int DEFAULT NULL COMMENT 'WebSocket关闭状态码',
    `close_reason` varchar(255) DEFAULT NULL COMMENT '关闭原因',
    `message_sent` bigint DEFAULT 0 COMMENT '发送消息数（断开时统计）',
    `message_received` bigint DEFAULT 0 COMMENT '接收消息数（断开时统计）',
    `heartbeat_count` int DEFAULT 0 COMMENT '心跳次数（断开时统计）',
    `error_count` int DEFAULT 0 COMMENT '错误次数（断开时统计）',
    `last_error` varchar(500) DEFAULT NULL COMMENT '最后一次错误信息',
    `del_flag` tinyint(4) NOT NULL DEFAULT 0 COMMENT '删除标志：0-正常，1-已删除',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_host_id` (`host_id`),
    INDEX `idx_device_id` (`device_id`),
    INDEX `idx_remote_ip` (`remote_ip`),
    INDEX `idx_connect_time` (`connect_time`),
    INDEX `idx_status` (`status`),
    INDEX `idx_error_code` (`error_code`),
    INDEX `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WebSocket连接记录表';

-- ----------------------------
-- 33、连接统计视图
-- ----------------------------
DROP VIEW IF EXISTS `v_ws_connection_stats`;
CREATE VIEW `v_ws_connection_stats` AS
SELECT 
    DATE(connect_time) AS stat_date,
    COUNT(*) AS total_connections,
    SUM(CASE WHEN status IN (1, 2) THEN 1 ELSE 0 END) AS success_connections,
    SUM(CASE WHEN status IN (4, 5, 6) THEN 1 ELSE 0 END) AS rejected_connections,
    SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS abnormal_disconnections,
    SUM(CASE WHEN status = 7 THEN 1 ELSE 0 END) AS admin_disconnections,
    COUNT(DISTINCT remote_ip) AS unique_ips,
    COUNT(DISTINCT host_id) AS unique_hosts,
    COUNT(DISTINCT device_id) AS unique_devices
FROM `ws_connection_log`
WHERE del_flag = 0
GROUP BY DATE(connect_time);

-- ----------------------------
-- 34、可疑连接视图
-- ----------------------------
DROP VIEW IF EXISTS `v_ws_suspicious_connections`;
CREATE VIEW `v_ws_suspicious_connections` AS
SELECT 
    device_id,
    GROUP_CONCAT(DISTINCT host_id) AS used_host_ids,
    COUNT(DISTINCT host_id) AS host_id_count,
    GROUP_CONCAT(DISTINCT remote_ip) AS used_ips,
    COUNT(*) AS connection_count,
    MAX(connect_time) AS last_connect_time
FROM `ws_connection_log`
WHERE device_id IS NOT NULL AND del_flag = 0
GROUP BY device_id
HAVING COUNT(DISTINCT host_id) > 1;

-- ----------------------------
-- 35、团队主机统计视图
-- ----------------------------
DROP VIEW IF EXISTS `v_ws_team_stats`;
CREATE VIEW `v_ws_team_stats` AS
SELECT 
    COALESCE(w.team_name, '个人') AS team_name,
    w.is_team,
    COUNT(DISTINCT w.host_id) AS host_count,
    SUM(CASE WHEN w.status = 1 THEN 1 ELSE 0 END) AS enabled_count,
    SUM(CASE WHEN w.status = 0 THEN 1 ELSE 0 END) AS disabled_count
FROM `ws_host_whitelist` w
WHERE w.del_flag = 0
GROUP BY w.team_name, w.is_team;

SET FOREIGN_KEY_CHECKS = 1;

