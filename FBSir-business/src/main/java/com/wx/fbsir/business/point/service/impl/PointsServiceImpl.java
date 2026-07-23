package com.wx.fbsir.business.point.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.utils.DateUtils;
import com.wx.fbsir.common.utils.StringUtils;
import com.wx.fbsir.business.point.domain.PointsRecord;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.mapper.PointsMapper;
import com.wx.fbsir.business.point.mapper.PointsRecordMapper;
import com.wx.fbsir.business.point.mapper.PointsRuleMapper;
import com.wx.fbsir.business.point.service.IPointsRuleService;
import com.wx.fbsir.business.point.service.IPointsService;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import com.wx.fbsir.system.service.ISysUserService;

/**
 * 积分Service业务层处理
 * 
 * @author FBSir
 * @date 2025-12-08
 */
@Service
public class PointsServiceImpl implements IPointsService {

    private static final String POINTS_BALANCE_CONCURRENT_CONFLICT = "POINTS_BALANCE_CONCURRENT_CONFLICT";
    private static final String POINTS_BALANCE_OUT_OF_RANGE = "POINTS_BALANCE_OUT_OF_RANGE";
    
    @Autowired
    private PointsMapper pointsMapper;
    
    @Autowired
    private PointsRecordMapper pointsRecordMapper;
    
    @Autowired
    private PointsRuleMapper pointsRuleMapper;
    
    @Autowired
    private IPointsRuleService pointsRuleService;
    
    @Autowired
    private ISysUserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount) {
        // 1. 参数校验
        if (userId == null) {
            return AjaxResult.error("用户ID不能为空");
        }
        if (StringUtils.isEmpty(ruleCode)) {
            return AjaxResult.error("规则编码不能为空");
        }
        
        // 2. 根据规则编码获取积分规则
        PointsRule rule = pointsRuleService.getRuleByCode(ruleCode);
        if (rule == null || !"0".equals(rule.getStatus())) {
            return AjaxResult.error("积分规则未配置或已停用");
        }
        
        // 3. 计算实际变动值
        Integer actualChange = changeAmount != null ? changeAmount : rule.getPointsValue();
        if (actualChange == null || actualChange == 0) {
            return AjaxResult.error("积分变动值无效");
        }
        
        // 4. 查询当前积分余额
        Integer currentPoints = getUserPoints(userId);
        if (currentPoints == null) {
            currentPoints = 0;
        }
        
        // 5. 限频校验（使用规则编码）
        if (!pointsRuleService.checkLimit(userId, ruleCode, rule)) {
            return AjaxResult.error("已达到限频上限，请稍后再试");
        }
        
        // 6. 累计上限校验（使用规则编码）
        if (!pointsRuleService.checkMaxAmount(userId, ruleCode, actualChange, rule)) {
            return AjaxResult.error("已达到累计上限，无法继续发放");
        }
        
        // 7. 余额校验（扣减场景）
        Integer newPoints = calculateNewPoints(currentPoints, actualChange);
        if (newPoints == null) {
            return balanceOutOfRange();
        }
        if (newPoints < 0) {
            return AjaxResult.error("积分余额不足，扣减失败");
        }
        
        // 8. 更新积分余额
        if (pointsMapper.updateUserPointsIfBalance(userId, currentPoints, newPoints) != 1) {
            return balanceConcurrentConflict();
        }
        
        // 9. 插入积分记录（使用规则编码）
        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setRuleCode(ruleCode);
        record.setChangeAmount(actualChange);
        record.setBalanceBefore(currentPoints);
        record.setBalanceAfter(newPoints);
        record.setCreateTime(DateUtils.getNowDate());
        pointsRecordMapper.insertPointsRecord(record);

        // 10. 成功后记录一次限频（避免失败情况下占用额度）
        pointsRuleService.markLimit(userId, ruleCode, rule);
        
        return AjaxResult.success("积分操作成功", newPoints);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount,
                                   Long scenePackId, String usageRecordId) {
        if (StringUtils.isEmpty(ruleCode)) {
            return AjaxResult.success("免费包，无需扣减积分");
        }

        // 1. 参数校验
        if (userId == null) {
            return AjaxResult.error("用户ID不能为空");
        }

        // 2. 根据规则编码获取积分规则
        PointsRule rule = pointsRuleService.getRuleByCode(ruleCode);
        if (rule == null || !"0".equals(rule.getStatus())) {
            return AjaxResult.error("积分规则未配置或已停用");
        }

        // 3. 计算实际变动值
        Integer actualChange = changeAmount != null ? changeAmount : rule.getPointsValue();
        if (actualChange == null || actualChange == 0) {
            return AjaxResult.error("积分变动值无效");
        }

        // 4. 查询当前积分余额
        Integer currentPoints = getUserPoints(userId);
        if (currentPoints == null) {
            currentPoints = 0;
        }

        // 5. 限频校验（使用规则编码）
        if (!pointsRuleService.checkLimit(userId, ruleCode, rule)) {
            return AjaxResult.error("已达到限频上限，请稍后再试");
        }

        // 6. 累计上限校验（使用规则编码）
        if (!pointsRuleService.checkMaxAmount(userId, ruleCode, actualChange, rule)) {
            return AjaxResult.error("已达到累计上限，无法继续发放");
        }

        // 7. 余额校验（扣减场景）
        Integer newPoints = calculateNewPoints(currentPoints, actualChange);
        if (newPoints == null) {
            return balanceOutOfRange();
        }
        if (newPoints < 0) {
            return AjaxResult.error("积分余额不足，扣减失败");
        }

        // 8. 更新积分余额
        if (pointsMapper.updateUserPointsIfBalance(userId, currentPoints, newPoints) != 1) {
            return balanceConcurrentConflict();
        }

        // 9. 插入积分记录（使用规则编码，并写入FBS关联字段）
        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setRuleCode(ruleCode);
        record.setChangeAmount(actualChange);
        record.setBalanceBefore(currentPoints);
        record.setBalanceAfter(newPoints);
        record.setCreateTime(DateUtils.getNowDate());
        record.setScenePackId(scenePackId);
        record.setUsageRecordId(usageRecordId);
        pointsRecordMapper.insertPointsRecord(record);

        // 10. 成功后记录一次限频（避免失败情况下占用额度）
        pointsRuleService.markLimit(userId, ruleCode, rule);

        return AjaxResult.success("积分操作成功", newPoints);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount,
                                   Long scenePackId, String usageRecordId, String eventId) {
        // 免费包：直接返回成功
        if (StringUtils.isEmpty(ruleCode)) {
            return AjaxResult.success("免费包，无需扣减积分");
        }

        // 参数校验
        if (userId == null) {
            return AjaxResult.error("用户ID不能为空");
        }

        // ===== 幂等检查（新增） =====
        // 注意：MySQL UNIQUE 索引允许多个 NULL，eventId=null 不会触发幂等（符合预期）
        // StringUtils.hasText 也排除了空字符串 "" 的情况
        if (StringUtils.hasText(eventId)) {
            PointsRecord existing = pointsRecordMapper.selectByEventId(eventId);
            if (existing != null) {
                // 已处理，返回既有余额（幂等）
                return AjaxResult.success("积分操作成功（幂等）", existing.getBalanceAfter());
            }
        }
        // ============================

        // 获取积分规则
        PointsRule rule = pointsRuleService.getRuleByCode(ruleCode);
        if (rule == null || !"0".equals(rule.getStatus())) {
            return AjaxResult.error("积分规则未配置或已停用");
        }

        // 计算实际变动值
        Integer actualChange = changeAmount != null ? changeAmount : rule.getPointsValue();
        if (actualChange == null || actualChange == 0) {
            return AjaxResult.error("积分变动值无效");
        }

        // 查询当前余额
        Integer currentPoints = getUserPoints(userId);
        if (currentPoints == null) {
            currentPoints = 0;
        }

        // 限频校验（使用规则编码）
        if (!pointsRuleService.checkLimit(userId, ruleCode, rule)) {
            return AjaxResult.error("已达到限频上限，请稍后再试");
        }

        // 累计上限校验
        if (!pointsRuleService.checkMaxAmount(userId, ruleCode, actualChange, rule)) {
            return AjaxResult.error("已达到累计上限，无法继续发放");
        }

        // 余额校验（扣减场景）
        Integer newPoints = calculateNewPoints(currentPoints, actualChange);
        if (newPoints == null) {
            return balanceOutOfRange();
        }
        if (newPoints < 0) {
            return AjaxResult.error("积分余额不足，扣减失败");
        }

        // 更新积分余额
        if (pointsMapper.updateUserPointsIfBalance(userId, currentPoints, newPoints) != 1) {
            return balanceConcurrentConflict();
        }

        // 插入积分记录（包含 event_id）
        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setRuleCode(ruleCode);
        record.setChangeAmount(actualChange);
        record.setBalanceBefore(currentPoints);
        record.setBalanceAfter(newPoints);
        record.setScenePackId(scenePackId);
        record.setUsageRecordId(usageRecordId);
        record.setEventId(eventId);  // ← 新增
        // 注意：create_time 在 XML 中硬编码为 NOW()，此处无需 setCreateTime
        pointsRecordMapper.insertPointsRecord(record);

        // 记录限频
        pointsRuleService.markLimit(userId, ruleCode, rule);

        return AjaxResult.success("积分操作成功", newPoints);
    }

    @Override
    public Integer getUserPoints(Long userId) {
        return pointsMapper.getUserPoints(userId);
    }

    @Override
    public List<PointsRecord> getPointsRecord(PointsRecord pointsRecord) {
        return pointsRecordMapper.selectPointsRecordList(pointsRecord);
    }

    @Override
    public Map<String, Object> getPointsSummary(Long userId) {
        Map<String, Object> summary = new HashMap<>();
        if (userId == null) {
            summary.put("balance", 0);
            summary.put("todayGain", 0);
            summary.put("weekGain", 0);
            summary.put("lastChange", null);
            return summary;
        }

        Integer balance = getUserPoints(userId);
        summary.put("balance", balance == null ? 0 : balance);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        summary.put("todayGain", sumPositivePointsSince(userId, todayStart));

        LocalDateTime weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        summary.put("weekGain", sumPositivePointsSince(userId, weekStart));

        PointsRecord lastChange = pointsRecordMapper.getLastPointChange(userId);
        summary.put("lastChange", lastChange);

        return summary;
    }

    @Override
    public List<Map<String, Object>> getPointsTaskList(Long userId) {
        List<PointsRule> ruleList = pointsRuleMapper.selectEnabledPointsRuleList();
        List<Map<String, Object>> taskList = new ArrayList<>();
        
        for (PointsRule rule : ruleList) {
            Map<String, Object> task = new HashMap<>();
            task.put("ruleCode", rule.getRuleCode());
            task.put("ruleName", rule.getRuleName());
            task.put("taskPoint", rule.getPointsValue());
            task.put("sortOrder", rule.getSortOrder());
            
            // 解析限频配置
            if (StringUtils.isNotEmpty(rule.getLimitType()) && rule.getLimitValue() != null) {
                Map<String, Object> limitConfig = new HashMap<>();
                limitConfig.put("limitType", rule.getLimitType());
                limitConfig.put("limitValue", rule.getLimitValue());
                limitConfig.put("maxAmount", rule.getMaxAmount());
                task.put("limitConfig", limitConfig);
                
                // 生成限频描述
                String limitDesc = getLimitDescription(rule.getLimitType(), rule.getLimitValue());
                task.put("limitDesc", limitDesc);
            }
            
            // 设置任务图标和分类
            task.put("icon", getTaskIcon(rule.getRuleName()));
            task.put("category", getTaskCategory(rule.getRuleName()));
            
            // 如果用户已登录，判断任务完成状态
            if (userId != null) {
                boolean isCompleted = checkTaskCompleted(userId, rule.getRuleCode(), rule);
                task.put("isCompleted", isCompleted);
                Map<String, Object> progress = getTaskProgress(userId, rule.getRuleCode(), rule);
                task.put("progress", progress);
            } else {
                task.put("isCompleted", false);
                task.put("progress", null);
            }
            
            taskList.add(task);
        }
        
        return taskList;
    }
    
    @Override
    public List<SysUser> getPointsFansList(SysUser user) {
        // 必须经用户服务代理进入@DataScope，清除客户端dataScope并应用可信角色范围。
        return userService.selectUserList(user);
    }

    /**
     * 统计自指定时间以来的正向积分
     */
    private int sumPositivePointsSince(Long userId, LocalDateTime startTime) {
        if (userId == null || startTime == null) {
            return 0;
        }
        Integer sum = pointsRecordMapper.sumUserPointsChangeSince(userId, startTime);
        return sum == null ? 0 : sum;
    }

    private AjaxResult balanceConcurrentConflict() {
        return AjaxResult.error(POINTS_BALANCE_CONCURRENT_CONFLICT);
    }

    private AjaxResult balanceOutOfRange() {
        return AjaxResult.error(POINTS_BALANCE_OUT_OF_RANGE);
    }

    private Integer calculateNewPoints(Integer currentPoints, Integer actualChange) {
        long newPoints = (long) currentPoints + actualChange;
        if (newPoints < Integer.MIN_VALUE || newPoints > Integer.MAX_VALUE) {
            return null;
        }
        return (int) newPoints;
    }

    /**
     * 判断任务是否完成
     */
    private boolean checkTaskCompleted(Long userId, String ruleCode, PointsRule rule) {
        int usedCount = getRuleUsedCount(userId, rule, false);
        if (rule == null || rule.getLimitValue() == null || rule.getLimitValue() <= 0) {
            return usedCount > 0;
        }
        return usedCount >= rule.getLimitValue();
    }

    /**
     * 获取任务进度信息
     */
    private Map<String, Object> getTaskProgress(Long userId, String ruleCode, PointsRule rule) {
        Map<String, Object> progress = new HashMap<>();
        int max = (rule != null && rule.getLimitValue() != null) ? rule.getLimitValue() : -1;
        int current = getRuleUsedCount(userId, rule, true);
        progress.put("current", current);
        progress.put("max", max);
        progress.put("remaining", max > 0 ? Math.max(0, max - current) : -1);
        return progress;
    }

    /**
     * 获取限频描述
     */
    private String getLimitDescription(String limitType, Integer limitValue) {
        switch (limitType.toUpperCase()) {
            case "DAILY":
                return "每日最多" + limitValue + "次";
            case "WEEKLY":
                return "每周最多" + limitValue + "次";
            case "MONTHLY":
                return "每月最多" + limitValue + "次";
            case "TOTAL":
                return "总计最多" + limitValue + "次";
            default:
                return "限频：" + limitValue + "次";
        }
    }

    /**
     * 获取任务图标
     */
    private String getTaskIcon(String taskName) {
        if (taskName == null) {
            return "el-icon-star-on";
        }
        if (taskName.contains("登录")) {
            return "el-icon-user";
        } else if (taskName.contains("模板") || taskName.contains("上架")) {
            return "el-icon-document";
        } else if (taskName.contains("购买")) {
            return "el-icon-shopping-cart-full";
        } else if (taskName.contains("评分") || taskName.contains("评价")) {
            return "el-icon-star-on";
        } else if (taskName.contains("咨询")) {
            return "el-icon-chat-line-round";
        } else if (taskName.contains("助手")) {
            return "el-icon-edit";
        } else if (taskName.contains("分成") || taskName.contains("销售")) {
            return "el-icon-money";
        } else {
            return "el-icon-trophy";
        }
    }

    /**
     * 获取任务分类
     */
    private String getTaskCategory(String taskName) {
        if (taskName == null) {
            return "其他";
        }
        if (taskName.contains("登录")) {
            return "每日任务";
        } else if (taskName.contains("模板") || taskName.contains("上架") || taskName.contains("分成")) {
            return "创作任务";
        } else if (taskName.contains("购买")) {
            return "消费任务";
        } else if (taskName.contains("评分") || taskName.contains("评价") || taskName.contains("咨询")) {
            return "互动任务";
        } else if (taskName.contains("助手")) {
            return "使用任务";
        } else {
            return "其他任务";
        }
    }

    /**
     * 统计当前周期内该规则的使用次数
     * @param ignoreLimitValue 当 limitValue 为空时是否直接返回0
     */
    private int getRuleUsedCount(Long userId, PointsRule rule, boolean ignoreLimitValue) {
        if (userId == null || rule == null) {
            return 0;
        }
        String limitType = rule.getLimitType();
        Integer limitValue = rule.getLimitValue();

        if (!ignoreLimitValue && (limitValue == null || limitValue <= 0)) {
            // 未设置限频，返回总次数
            return pointsRecordMapper.checkUserTaskCompleted(userId, rule.getRuleCode());
        }

        LocalDateTime startTime = null;
        if (StringUtils.isNotEmpty(limitType)) {
            switch (limitType.toUpperCase()) {
                case "DAILY":
                    startTime = LocalDate.now().atStartOfDay();
                    break;
                case "WEEKLY":
                    startTime = LocalDate.now()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .atStartOfDay();
                    break;
                case "MONTHLY":
                    startTime = LocalDate.now().withDayOfMonth(1).atStartOfDay();
                    break;
                case "TOTAL":
                default:
                    startTime = null;
                    break;
            }
        }
        return pointsRecordMapper.countUserRuleSince(userId, rule.getRuleCode(), startTime);
    }
}

