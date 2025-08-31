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
            // 简化的单行日志
            logger.info("🔍 查询消息 | {} | {}", timestamp, content);
            
            // 参数验证
            if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(content)) {
                String defaultUnionId = "ovZrQ673x1GGaP6cX5XUnfzu7TmE";
                logger.info("📤 返回结果 | 错误码: 10020 | union_id: {}", defaultUnionId);
                return ApiResponse.success(defaultUnionId);
            }
            
            // 解析时间戳
            Long targetTime = parseTimestamp(timestamp);
            if (targetTime == null) {
                String defaultUnionId = "ovZrQ673x1GGaP6cX5XUnfzu7TmE";
                logger.info("📤 返回结果 | 错误码: 10020 | union_id: {}", defaultUnionId);
                return ApiResponse.success(defaultUnionId);
            }
            
            // 在4秒时间范围内查找匹配的消息
            List<WeChatMessage> matchedMessages = findMatchedMessages(targetTime, content);
            
            if (matchedMessages.isEmpty()) {
                String defaultUnionId = "ovZrQ673x1GGaP6cX5XUnfzu7TmE";
                logger.info("📤 返回结果 | 错误码: 10010 | union_id: {}", defaultUnionId);
                return ApiResponse.success(defaultUnionId);
            }
            
            if (matchedMessages.size() > 1) {
                String defaultUnionId = "ovZrQ673x1GGaP6cX5XUnfzu7TmE";
                logger.info("📤 返回结果 | 错误码: 10100 | union_id: {}", defaultUnionId);
                return ApiResponse.success(defaultUnionId);
            }
            
            WeChatMessage message = matchedMessages.get(0);
            
            // 使用后删除缓存
            removeMessageFromCache(message);
            String unionId = message.getUnionId();
            if(unionId == null) {
                unionId = "ovZrQ673x1GGaP6cX5XUnfzu7TmE"; // 默认值
            }
            logger.info("📤 返回结果 | 错误码: 200 | union_id: {}", unionId);
            return ApiResponse.success(unionId);
            
        } catch (Exception e) {
            String defaultUnionId = "ovZrQ673x1GGaP6cX5XUnfzu7TmE";
            logger.info("📤 返回结果 | 错误码: 10020 | union_id: {}", defaultUnionId);
            return ApiResponse.success(defaultUnionId);
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
                return ts;
            } catch (NumberFormatException e) {
                // 静默处理
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
                
                return parsedTime;
            } catch (ParseException e) {
                // 静默处理格式不匹配
            }
        }
        
        return null;
    }
    
    /**
     * 查找匹配的消息（4秒时间范围内）
     */
    private List<WeChatMessage> findMatchedMessages(Long targetTime, String content) {
        List<WeChatMessage> matchedMessages = new ArrayList<>();
        
        // 查找时间范围：目标时间前4秒
        long startTime = targetTime - 4000;
        long endTime = targetTime;
        
        // 从内存缓存中查找
        for (WeChatMessage message : messageCache.values()) {
            if (message.getCreateTime() != null && 
                message.getCreateTime() >= startTime && 
                message.getCreateTime() <= endTime &&
                content.equals(message.getContent())) {
                matchedMessages.add(message);
            }
        }
        
        // 如果内存缓存中没有找到，尝试从Redis中查找
        if (matchedMessages.isEmpty()) {
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
        } catch (Exception e) {
            // 静默处理异常
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