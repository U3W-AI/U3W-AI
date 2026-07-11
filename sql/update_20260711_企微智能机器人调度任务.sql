-- ============================================================
-- 福帮手企微智能机器人调度任务
-- 变更日期：2026-07-11
-- 说明：幂等启用控制面内部任务与人工批准后的 Webhook 投递任务。
-- ============================================================

INSERT INTO `sys_job`
    (`job_name`, `job_group`, `invoke_target`, `cron_expression`,
     `misfire_policy`, `concurrent`, `status`, `create_by`, `create_time`, `remark`)
SELECT '企微智能机器人内部编排', 'SMARTBOT',
       'smartBotInternalDispatcherTask.dispatchAvailable()', '0/5 * * * * ?',
       '3', '1', '0', 'admin', SYSDATE(), '2026-07-11：处理智能机器人内部编排 Outbox'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_job`
    WHERE `invoke_target` = 'smartBotInternalDispatcherTask.dispatchAvailable()'
);

INSERT INTO `sys_job`
    (`job_name`, `job_group`, `invoke_target`, `cron_expression`,
     `misfire_policy`, `concurrent`, `status`, `create_by`, `create_time`, `remark`)
SELECT '企微智能机器人Webhook投递', 'SMARTBOT',
       'smartBotWebhookDispatcherTask.dispatchAvailable()', '0/5 * * * * ?',
       '3', '1', '0', 'admin', SYSDATE(), '2026-07-11：处理人工批准后的 Webhook Hub Outbox'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_job`
    WHERE `invoke_target` = 'smartBotWebhookDispatcherTask.dispatchAvailable()'
);
