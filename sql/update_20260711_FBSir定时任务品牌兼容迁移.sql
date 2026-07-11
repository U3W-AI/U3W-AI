-- FBSir Quartz task brand migration
-- Updated: 2026-07-11
--
-- Safe rollout order:
--   1. Deploy code containing both FBSirTask and the deprecated WxFbsirTask facade.
--   2. Run this idempotent migration.
--   3. Keep the facade for at least one compatibility window.
-- Historical sys_job_log rows are intentionally left unchanged as an audit trail.

UPDATE `sys_job`
SET `invoke_target` = REPLACE(
        REPLACE(`invoke_target`, 'WxFbsirTask.WxFbsir', 'FBSirTask.FBSir'),
        '''WxFbsir''', '''FBSir''')
WHERE `invoke_target` LIKE 'WxFbsirTask.WxFbsir%';

-- Verification: this query must return zero rows after migration.
SELECT `job_id`, `job_name`, `invoke_target`
FROM `sys_job`
WHERE `invoke_target` LIKE 'WxFbsirTask.WxFbsir%';

-- Rollback (run only while the deprecated facade remains deployed):
-- UPDATE `sys_job`
-- SET `invoke_target` = REPLACE(
--         REPLACE(`invoke_target`, 'FBSirTask.FBSir', 'WxFbsirTask.WxFbsir'),
--         '''FBSir''', '''WxFbsir''')
-- WHERE `invoke_target` LIKE 'FBSirTask.FBSir%';
