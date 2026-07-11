package com.wx.fbsir.business.point.service.impl;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import com.wx.fbsir.common.core.redis.RedisCache;
import com.wx.fbsir.common.utils.StringUtils;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.mapper.PointsRuleMapper;
import com.wx.fbsir.business.point.service.IPointsRuleService;

/**
 * 积分规则Service业务层处理
 * 
 * @author FBSir
 * @date 2025-12-08
 */
@Service
public class PointsRuleServiceImpl implements IPointsRuleService {
    
    private static final String LIMIT_KEY_PREFIX = "points:limit";
    private static final String MAX_AMOUNT_KEY_PREFIX = "points:max";
    
    @Autowired
    private PointsRuleMapper pointsRuleMapper;
    
    @Autowired
    private RedisCache redisCache;

    @Override
    public PointsRule selectPointsRuleByRuleId(Long ruleId) {
        return pointsRuleMapper.selectPointsRuleByRuleId(ruleId);
    }

    @Override
    public PointsRule getRuleByCode(String ruleCode) {
        return pointsRuleMapper.selectPointsRuleByRuleCode(ruleCode);
    }

    @Override
    public List<PointsRule> selectPointsRuleList(PointsRule pointsRule) {
        return pointsRuleMapper.selectPointsRuleList(pointsRule);
    }

    @Override
    public List<PointsRule> selectEnabledPointsRuleList() {
        return pointsRuleMapper.selectEnabledPointsRuleList();
    }

    @Override
    public int insertPointsRule(PointsRule pointsRule) {
        return pointsRuleMapper.insertPointsRule(pointsRule);
    }

    @Override
    public int updatePointsRule(PointsRule pointsRule) {
        return pointsRuleMapper.updatePointsRule(pointsRule);
    }

    @Override
    public int deletePointsRuleByRuleIds(Long[] ruleIds) {
        return pointsRuleMapper.deletePointsRuleByRuleIds(ruleIds);
    }

    @Override
    public int deletePointsRuleByRuleId(Long ruleId) {
        return pointsRuleMapper.deletePointsRuleByRuleId(ruleId);
    }

    @Override
    public boolean checkLimit(Long userId, String ruleCode, PointsRule rule) {
        if (rule == null || StringUtils.isEmpty(rule.getLimitType()) 
            || rule.getLimitValue() == null || rule.getLimitValue() <= 0) {
            return true; // 无限频配置，允许通过
        }
        
        // 使用规则编码构建Redis key
        String limitKey = buildLimitKey(userId, ruleCode, rule.getLimitType());
        Number cacheVal = redisCache.getCacheObject(limitKey);
        Long current = cacheVal == null ? 0L : cacheVal.longValue();
        
        // 仅校验，不在此处自增，避免失败占用额度
        return current < rule.getLimitValue();
    }

    @Override
    public boolean checkMaxAmount(Long userId, String ruleCode, Integer amount, PointsRule rule) {
        if (rule == null || amount == null || amount <= 0) {
            return true;
        }
        Integer maxAmount = rule.getMaxAmount();
        if (maxAmount == null || maxAmount <= 0) {
            return true;
        }
        
        String key = MAX_AMOUNT_KEY_PREFIX + ":" + ruleCode + ":" + userId;
        Long current = redisCache.getCacheObject(key);
        if (current == null) {
            current = 0L;
        }
        
        if (current + amount > maxAmount) {
            return false;
        }
        
        // 更新累计值
        redisCache.redisTemplate.opsForValue().increment(key, amount);
        return true;
    }

    @Override
    public void markLimit(Long userId, String ruleCode, PointsRule rule) {
        if (rule == null || StringUtils.isEmpty(rule.getLimitType()) 
            || rule.getLimitValue() == null || rule.getLimitValue() <= 0) {
            return;
        }
        String limitKey = buildLimitKey(userId, ruleCode, rule.getLimitType());
        Long newVal = redisCache.redisTemplate.opsForValue().increment(limitKey, 1);
        if (newVal != null && newVal == 1) {
            long ttl = calculateTTLSeconds(rule.getLimitType());
            if (ttl > 0) {
                redisCache.expire(limitKey, ttl);
            }
        }
    }

    /**
     * 构建限频Redis key
     * 格式：points:limit:{ruleCode}:{userId}:{periodKey}
     */
    private String buildLimitKey(Long userId, String ruleCode, String limitType) {
        String periodKey = "total";
        LocalDate today = LocalDate.now();
        switch (limitType) {
            case "DAILY":
                periodKey = today.toString();
                break;
            case "WEEKLY":
                int week = today.get(WeekFields.ISO.weekOfWeekBasedYear());
                periodKey = today.getYear() + "-W" + week;
                break;
            case "MONTHLY":
                periodKey = today.getYear() + "-" + today.getMonthValue();
                break;
            default:
                break;
        }
        return LIMIT_KEY_PREFIX + ":" + ruleCode + ":" + userId + ":" + periodKey;
    }

    /**
     * 计算TTL秒数
     */
    private long calculateTTLSeconds(String limitType) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime;
        switch (limitType) {
            case "DAILY":
                expireTime = now.toLocalDate().plusDays(1).atStartOfDay();
                break;
            case "WEEKLY":
                expireTime = now.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay();
                break;
            case "MONTHLY":
                expireTime = now.with(TemporalAdjusters.firstDayOfNextMonth()).toLocalDate().atStartOfDay();
                break;
            default:
                return -1;
        }
        long seconds = Duration.between(now, expireTime).getSeconds();
        return Math.max(seconds, 1);
    }
}

