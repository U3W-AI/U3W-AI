-- =========================
-- 输出物模块权限
-- 说明：
-- 1. 权限采用 business:output:xxx 三段式命名
-- 2. menu_type = 'F' 表示按钮权限
-- 3. parent_id 需要替换为“输出物”父菜单的 menu_id
-- =========================

-- 生成输出物
insert into sys_menu values('1526', '生成输出物', '153', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'business:output:generate', '#', 'admin', sysdate(), '', null, '生成输出物权限');

-- 导出Markdown
insert into sys_menu values('1527', '导出Markdown', '153', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'business:output:exportMarkdown', '#', 'admin', sysdate(), '', null, '导出Markdown权限');

-- 导出JSON
insert into sys_menu values('1528', '导出JSON', '153', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'business:output:exportJson', '#', 'admin', sysdate(), '', null, '导出JSON权限');

-- 推送Webhook
insert into sys_menu values('1529', '推送Webhook', '153', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'business:output:pushWebhook', '#', 'admin', sysdate(), '', null, '推送Webhook权限');

-- 保存输出物
insert into sys_menu values('1530', '保存输出物', '153', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'business:output:save', '#', 'admin', sysdate(), '', null, '保存输出物权限');