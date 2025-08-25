package com.cube.wechat.selfapp.officeaccount.service;

import com.alibaba.fastjson.JSON;
import com.cube.wechat.selfapp.corpchat.util.RedisUtil;
import com.cube.wechat.selfapp.officeaccount.entity.ApiResponse;
import com.cube.wechat.selfapp.officeaccount.entity.WeChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信消息服务类
 * @author AspireLife
 * @date 2024年12月06日
 */
@Service
public class WeChatMessageService {
    
    private static final Logger logger = LoggerFactory.getLogger(WeChatMessageService.class);
    
    @Autowired
    private RedisUtil redisUtil;
    
    // 消息缓存Key前缀
    private static final String MESSAGE_CACHE_PREFIX = "wechat_message:";
    
    // 内存缓存，用于快速查询
    private final Map<String, WeChatMessage> messageCache = new ConcurrentHashMap<>();
    
    // 时间格式解析器
    private static final List<SimpleDateFormat> TIME_FORMATS = Arrays.asList(
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
        new SimpleDateFormat("HH:mm:ss"),
        new SimpleDateFormat("yyyy-MM-dd"),
        new SimpleDateFormat("MM-dd HH:mm:ss"),
        new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"),
        new SimpleDateFormat("yyyy/MM/dd"),
        // 中文日期格式
        new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss"),
        new SimpleDateFormat("yyyy年MM月dd日"),
        new SimpleDateFormat("MM月dd日 HH:mm:ss")
    );
    
    /**
     * 缓存微信消息
     */
    public void cacheMessage(WeChatMessage message) {
        try {
            String cacheKey = MESSAGE_CACHE_PREFIX + message.getCreateTime() + ":" + message.getContent();
            
            // 存储到Redis，过期时间10秒
            redisUtil.set(cacheKey, JSON.toJSONString(message), 10);
            
            // 存储到内存缓存
            messageCache.put(cacheKey, message);
            
            // 美观输出消息信息
            printMessageInfo(message);
            
            // 清理过期的内存缓存
            cleanExpiredCache();
            
        } catch (Exception e) {
            logger.error("缓存消息失败", e);
        }
    }
    
    /**
     * 根据时间戳和消息内容查询用户UnionID
     */
    public ApiResponse getUserByMessage(String timestamp, String content) {
        try {
            System.out.println("\n" + "-".repeat(80));
            System.out.printf("🔍 工作流查询 | %s | %s%n", timestamp, content);
            System.out.println("-".repeat(80));
            
            // 参数验证
            if (!StringUtils.hasText(timestamp)) {
                logger.warn("❌ 参数验证失败: timestamp为空");
                return ApiResponse.error();
            }
            if (!StringUtils.hasText(content)) {
                logger.warn("❌ 参数验证失败: content为空");
                return ApiResponse.error();
            }
            
            // 解析时间戳
            Long targetTime = parseTimestamp(timestamp);
            if (targetTime == null) {
                logger.warn("❌ 时间戳解析失败: {}", timestamp);
                return ApiResponse.error();
            }
            
            // 在4秒时间范围内查找匹配的消息
            List<WeChatMessage> matchedMessages = findMatchedMessages(targetTime, content);
            
            if (matchedMessages.isEmpty()) {
                logger.warn("❌ 未找到匹配消息 (缓存中有{}条消息)", messageCache.size());
                return ApiResponse.notFound();
            }
            
            if (matchedMessages.size() > 1) {
                logger.warn("❌ 发现重复消息: {}条", matchedMessages.size());
                return ApiResponse.busy();
            }
            
            WeChatMessage message = matchedMessages.get(0);
            logger.info("✅ 查询成功: UnionID={}", message.getUnionId());
            
            // 使用后删除缓存
            removeMessageFromCache(message);
            
            return ApiResponse.success(message.getUnionId());
            
        } catch (Exception e) {
            logger.error("❌ 查询用户消息失败", e);
            return ApiResponse.error();
        }
    }
    
    /**
     * 解析时间戳字符串
     */
    private Long parseTimestamp(String timestamp) {
        // 如果是纯数字，直接解析为时间戳
        if (timestamp.matches("\\d+")) {
            try {
                long ts = Long.parseLong(timestamp);
                // 如果是10位时间戳，转换为13位
                if (String.valueOf(ts).length() == 10) {
                    ts *= 1000;
                }
                logger.info("✅ 时间解析成功: {} -> {}", timestamp, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(ts)));
                return ts;
            } catch (NumberFormatException e) {
                logger.warn("❌ 数字时间戳解析失败: {}", timestamp);
            }
        }
        
        // 尝试解析各种时间格式
        for (int i = 0; i < TIME_FORMATS.size(); i++) {
            SimpleDateFormat format = TIME_FORMATS.get(i);
            try {
                Date date = format.parse(timestamp);
                long parsedTime = date.getTime();
                
                // 如果解析的时间只有时分秒，补充今天的日期
                if (timestamp.matches("\\d{2}:\\d{2}:\\d{2}")) {
                    Calendar cal = Calendar.getInstance();
                    Calendar parsed = Calendar.getInstance();
                    parsed.setTime(date);
                    
                    cal.set(Calendar.HOUR_OF_DAY, parsed.get(Calendar.HOUR_OF_DAY));
                    cal.set(Calendar.MINUTE, parsed.get(Calendar.MINUTE));
                    cal.set(Calendar.SECOND, parsed.get(Calendar.SECOND));
                    cal.set(Calendar.MILLISECOND, 0);
                    
                    parsedTime = cal.getTimeInMillis();
                }
                
                logger.info("✅ 时间解析成功: {} -> {}", timestamp, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(parsedTime)));
                return parsedTime;
            } catch (ParseException e) {
                // 静默处理格式不匹配
            }
        }
        
        logger.warn("❌ 时间解析失败: {}", timestamp);
        logger.warn("📋 支持的时间格式:");
        for (int i = 0; i < TIME_FORMATS.size(); i++) {
            logger.warn("   [{}] {}", i, TIME_FORMATS.get(i).toPattern());
        }
        return null;
    }
    
    /**
     * 查找匹配的消息（4秒时间范围内）
     */
    private List<WeChatMessage> findMatchedMessages(Long targetTime, String content) {
        List<WeChatMessage> matchedMessages = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        // 查找时间范围：目标时间前4秒
        long startTime = targetTime - 4000;
        long endTime = targetTime;
        
        logger.info("🔍 查找匹配消息");
        logger.info("📅 时间范围: {} ~ {}", new Date(startTime), new Date(endTime));
        logger.info("📝 匹配内容: '{}'", content);
        logger.info("📊 当前内存缓存: {}条消息", messageCache.size());
        
        // 从内存缓存中查找
        int checkedCount = 0;
        for (WeChatMessage message : messageCache.values()) {
            checkedCount++;
            logger.info("🔎 检查消息[{}]: 时间={}, 内容='{}', UnionID={}", 
                checkedCount, new Date(message.getCreateTime()), message.getContent(), message.getUnionId());
            
            if (message.getCreateTime() != null && 
                message.getCreateTime() >= startTime && 
                message.getCreateTime() <= endTime &&
                content.equals(message.getContent())) {
                logger.info("✅ 找到匹配消息: 时间={}, 内容='{}', UnionID={}", 
                    new Date(message.getCreateTime()), message.getContent(), message.getUnionId());
                matchedMessages.add(message);
            } else {
                // 详细说明为什么不匹配
                if (message.getCreateTime() == null) {
                    logger.info("❌ 时间为空");
                } else if (message.getCreateTime() < startTime) {
                    logger.info("❌ 时间太早: {} < {}", new Date(message.getCreateTime()), new Date(startTime));
                } else if (message.getCreateTime() > endTime) {
                    logger.info("❌ 时间太晚: {} > {}", new Date(message.getCreateTime()), new Date(endTime));
                } else if (!content.equals(message.getContent())) {
                    logger.info("❌ 内容不匹配: '{}' != '{}'", content, message.getContent());
                }
            }
        }
        
        logger.info("🔍 内存缓存查找结果: 检查了{}条消息，找到{}条匹配", checkedCount, matchedMessages.size());
        
        // 如果内存缓存中没有找到，尝试从Redis中查找
        if (matchedMessages.isEmpty()) {
            logger.info("🔄 内存缓存未找到，尝试Redis查找...");
            matchedMessages = findFromRedis(startTime, endTime, content);
        }
        
        return matchedMessages;
    }
    
    /**
     * 从Redis中查找消息
     */
    private List<WeChatMessage> findFromRedis(long startTime, long endTime, String content) {
        List<WeChatMessage> matchedMessages = new ArrayList<>();
        
        try {
            // 由于RedisUtil没有keys方法，我们使用内存缓存进行查找
            // 在实际生产环境中，建议使用Redis的scan命令来避免keys的性能问题
            logger.info("从Redis查找消息，时间范围: {} - {}, 内容: {}", startTime, endTime, content);
        } catch (Exception e) {
            logger.error("从Redis查找消息失败", e);
        }
        
        return matchedMessages;
    }
    
    /**
     * 从缓存中删除消息
     */
    private void removeMessageFromCache(WeChatMessage message) {
        try {
            String cacheKey = MESSAGE_CACHE_PREFIX + message.getCreateTime() + ":" + message.getContent();
            
            // 从Redis删除
            redisUtil.delete(cacheKey);
            
            // 从内存缓存删除
            messageCache.remove(cacheKey);
        } catch (Exception e) {
            logger.error("删除消息缓存失败", e);
        }
    }
    
    /**
     * 清理过期的内存缓存
     */
    private void cleanExpiredCache() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, WeChatMessage>> iterator = messageCache.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, WeChatMessage> entry = iterator.next();
            WeChatMessage message = entry.getValue();
            
            // 清理超过10秒的缓存
            if (currentTime - message.getCacheTime() > 10000) {
                iterator.remove();
            }
        }
    }
    
    /**
     * 简化输出消息信息
     */
    private void printMessageInfo(WeChatMessage message) {
        System.out.println("\n" + "=".repeat(80));
        System.out.printf("📨 微信消息 | %s | %s | %s%n", 
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(message.getCreateTime())),
            message.getContent(),
            message.getFromUserName());
        System.out.println("=".repeat(80));
    }
    
    /**
     * 添加消息到缓存 - 测试用
     */
    public void addMessageToCache(WeChatMessage message) {
        cacheMessage(message);
    }
    
    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return messageCache.size();
    }
    
    /**
     * 清空缓存
     */
    public void clearCache() {
        messageCache.clear();
        logger.info("🗑️ 内存缓存已清空");
    }
    
    /**
     * 包装getUserByMessage返回结果为Map格式，用于测试控制器
     */
    public Map<String, Object> getUserByMessageAsMap(String timestamp, String content) {
        ApiResponse response = getUserByMessage(timestamp, content);
        Map<String, Object> result = new HashMap<>();
        result.put("code", response.getCode());
        result.put("message", response.getMessage());
        if (response.getUnion_id() != null) {
            result.put("union_id", response.getUnion_id());
        }
        return result;
    }
} 