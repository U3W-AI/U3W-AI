package com.wx.fbsir.engine.playwright.util;

import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 剪贴板管理器
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 核心职责
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 提供统一的剪贴板读写接口
 * 2. 支持通过 Playwright Page 操作浏览器剪贴板
 * 3. 支持系统剪贴板操作（需要有头模式）
 * 4. 处理剪贴板操作的异常情况
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 使用方式
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * ```java
 * @Autowired
 * private ClipboardManager clipboardManager;
 * 
 * // 写入剪贴板
 * clipboardManager.write(page, "Hello World");
 * 
 * // 读取剪贴板
 * String content = clipboardManager.read(page);
 * 
 * // 粘贴到页面元素
 * clipboardManager.pasteToElement(page, "#input-field", "content");
 * ```
 * 
 * @author wxfbsir
 * @date 2025-12-16
 */
@Component
public class ClipboardManager {

    private static final Logger log = LoggerFactory.getLogger(ClipboardManager.class);
    
    /**
     * 每个页面的剪贴板操作锁（解决多线程剪贴板冲突）
     */
    private final ConcurrentHashMap<String, ReentrantLock> pageLocks = new ConcurrentHashMap<>();
    
    /**
     * 全局剪贴板操作锁（用于系统剪贴板操作）
     */
    private final ReentrantLock globalLock = new ReentrantLock();
    
    /**
     * 锁等待超时时间（秒）
     */
    private static final int LOCK_TIMEOUT_SECONDS = 10;

    /**
     * 写入剪贴板（通过浏览器 API，线程安全）
     * 
     * @param page Playwright 页面
     * @param text 要写入的文本
     * @return true 如果写入成功
     */
    public boolean write(Page page, String text) {
        if (page == null) {
            log.error("[剪贴板] 写入失败 - Page 为 null");
            return false;
        }
        if (text == null) {
            log.error("[剪贴板] 写入失败 - 文本为 null");
            return false;
        }
        
        String pageId = getPageId(page);
        ReentrantLock lock = getPageLock(pageId);
        
        try {
            if (!globalLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.error("[剪贴板] 写入失败 - 获取全局锁超时 ({}s)", LOCK_TIMEOUT_SECONDS);
                return false;
            }
            if (!lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.error("[剪贴板] 写入失败 - 获取锁超时 ({}s)，可能存在剪贴板冲突", LOCK_TIMEOUT_SECONDS);
                globalLock.unlock();
                return false;
            }
            
            try {
                page.evaluate("text => navigator.clipboard.writeText(text)", text);
                log.debug("[剪贴板] 写入成功 - 页面: {}, 长度: {} 字符", pageId, text.length());
                return true;
            } catch (com.microsoft.playwright.TimeoutError e) {
                log.error("[剪贴板] 写入超时 - 页面: {}, 错误: {}", pageId, e.getMessage());
                return false;
            } catch (Exception e) {
                log.warn("[剪贴板] 浏览器API写入失败 - 页面: {}, 错误类型: {}, 错误: {}", 
                    pageId, e.getClass().getSimpleName(), e.getMessage());
                // 降级方案：使用 document.execCommand
                return writeWithExecCommand(page, text, pageId);
            } finally {
                lock.unlock();
                globalLock.unlock();
            }
        } catch (InterruptedException e) {
            if (globalLock.isHeldByCurrentThread()) {
                globalLock.unlock();
            }
            Thread.currentThread().interrupt();
            log.error("[剪贴板] 写入被中断 - 页面: {}", pageId);
            return false;
        }
    }

    /**
     * 读取剪贴板（通过浏览器 API，线程安全）
     * 
     * @param page Playwright 页面
     * @return 剪贴板内容，失败返回 null
     */
    public String read(Page page) {
        if (page == null) {
            log.error("[剪贴板] 读取失败 - Page 为 null");
            return null;
        }
        
        String pageId = getPageId(page);
        ReentrantLock lock = getPageLock(pageId);
        
        try {
            if (!globalLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.error("[剪贴板] 读取失败 - 获取全局锁超时 ({}s)", LOCK_TIMEOUT_SECONDS);
                return null;
            }
            if (!lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.error("[剪贴板] 读取失败 - 获取锁超时 ({}s)", LOCK_TIMEOUT_SECONDS);
                globalLock.unlock();
                return null;
            }
            
            try {
                Object result = page.evaluate("() => navigator.clipboard.readText()");
                String text = result != null ? normalizeLineEndings(result.toString()) : "";
                log.debug("[剪贴板] 读取成功 - 页面: {}, 长度: {} 字符", pageId, text.length());
                return text;
            } catch (com.microsoft.playwright.TimeoutError e) {
                log.error("[剪贴板] 读取超时 - 页面: {}, 错误: {}", pageId, e.getMessage());
                return null;
            } catch (Exception e) {
                log.error("[剪贴板] 读取失败 - 页面: {}, 错误类型: {}, 错误: {}", 
                    pageId, e.getClass().getSimpleName(), e.getMessage());
                return null;
            } finally {
                lock.unlock();
                globalLock.unlock();
            }
        } catch (InterruptedException e) {
            if (globalLock.isHeldByCurrentThread()) {
                globalLock.unlock();
            }
            Thread.currentThread().interrupt();
            log.error("[剪贴板] 读取被中断 - 页面: {}", pageId);
            return null;
        }
    }

    /**
     * 使用 execCommand 写入剪贴板（降级方案）
     */
    private boolean writeWithExecCommand(Page page, String text, String pageId) {
        try {
            String script = """
                (text) => {
                    const textarea = document.createElement('textarea');
                    textarea.value = text;
                    textarea.style.position = 'fixed';
                    textarea.style.left = '-9999px';
                    document.body.appendChild(textarea);
                    textarea.select();
                    const result = document.execCommand('copy');
                    document.body.removeChild(textarea);
                    return result;
                }
                """;
            Object result = page.evaluate(script, text);
            boolean success = Boolean.TRUE.equals(result);
            if (success) {
                log.debug("[剪贴板] execCommand写入成功 - 页面: {}", pageId);
            } else {
                log.warn("[剪贴板] execCommand写入返回false - 页面: {}", pageId);
            }
            return success;
        } catch (Exception e) {
            log.error("[剪贴板] execCommand写入失败 - 页面: {}, 错误类型: {}, 错误: {}", 
                pageId, e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    /**
     * 粘贴内容到页面元素
     * 先写入剪贴板，然后模拟 Ctrl+V
     * 
     * @param page Playwright 页面
     * @param selector 元素选择器
     * @param text 要粘贴的文本
     * @return true 如果成功
     */
    public boolean pasteToElement(Page page, String selector, String text) {
        if (page == null) {
            log.error("[剪贴板] 粘贴失败 - Page 为 null");
            return false;
        }
        if (selector == null) {
            log.error("[剪贴板] 粘贴失败 - 选择器为 null");
            return false;
        }
        if (text == null) {
            log.error("[剪贴板] 粘贴失败 - 文本为 null");
            return false;
        }
        
        String pageId = getPageId(page);
        boolean globalAcquired = false;
        try {
            globalAcquired = globalLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!globalAcquired) {
                log.error("[剪贴板] 粘贴失败 - 获取全局锁超时 ({}s)", LOCK_TIMEOUT_SECONDS);
                return false;
            }
            // 先写入剪贴板
            if (!write(page, text)) {
                log.error("[剪贴板] 粘贴失败 - 无法写入剪贴板，页面: {}, 选择器: {}", pageId, selector);
                return false;
            }
            
            // 聚焦元素
            page.locator(selector).click();
            
            // 模拟 Ctrl+V / Cmd+V
            String modifier = System.getProperty("os.name").toLowerCase().contains("mac") 
                ? "Meta" : "Control";
            page.keyboard().press(modifier + "+v");
            
            log.debug("[剪贴板] 粘贴到元素成功 - 页面: {}, 选择器: {}", pageId, selector);
            return true;
        } catch (com.microsoft.playwright.TimeoutError e) {
            log.error("[剪贴板] 粘贴超时 - 页面: {}, 选择器: {}, 错误: {}", pageId, selector, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[剪贴板] 粘贴被中断 - 页面: {}, 选择器: {}", pageId, selector);
            return false;
        } catch (Exception e) {
            log.error("[剪贴板] 粘贴到元素失败 - 页面: {}, 选择器: {}, 错误类型: {}, 错误: {}", 
                pageId, selector, e.getClass().getSimpleName(), e.getMessage());
            return false;
        } finally {
            if (globalAcquired) {
                globalLock.unlock();
            }
        }
    }

    /**
     * 复制页面元素的文本内容
     * 选中元素内容，然后模拟 Ctrl+C
     * 
     * @param page Playwright 页面
     * @param selector 元素选择器
     * @return 复制的文本内容
     */
    public String copyFromElement(Page page, String selector) {
        if (page == null) {
            log.error("[剪贴板] 复制失败 - Page 为 null");
            return null;
        }
        if (selector == null) {
            log.error("[剪贴板] 复制失败 - 选择器为 null");
            return null;
        }
        
        String pageId = getPageId(page);
        boolean globalAcquired = false;
        try {
            globalAcquired = globalLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!globalAcquired) {
                log.error("[剪贴板] 复制失败 - 获取全局锁超时 ({}s)", LOCK_TIMEOUT_SECONDS);
                return null;
            }
            // 全选元素内容
            page.locator(selector).click();
            String modifier = System.getProperty("os.name").toLowerCase().contains("mac") 
                ? "Meta" : "Control";
            page.keyboard().press(modifier + "+a");
            page.keyboard().press(modifier + "+c");
            
            // 等待剪贴板操作完成
            Thread.sleep(100);
            
            // 读取剪贴板
            String result = read(page);
            log.debug("[剪贴板] 从元素复制成功 - 页面: {}, 选择器: {}", pageId, selector);
            return result;
        } catch (com.microsoft.playwright.TimeoutError e) {
            log.error("[剪贴板] 复制超时 - 页面: {}, 选择器: {}, 错误: {}", pageId, selector, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[剪贴板] 复制被中断 - 页面: {}, 选择器: {}", pageId, selector);
            return null;
        } catch (Exception e) {
            log.error("[剪贴板] 从元素复制失败 - 页面: {}, 选择器: {}, 错误类型: {}, 错误: {}", 
                pageId, selector, e.getClass().getSimpleName(), e.getMessage());
            return null;
        } finally {
            if (globalAcquired) {
                globalLock.unlock();
            }
        }
    }

    /**
     * 清空剪贴板
     * 
     * @param page Playwright 页面
     * @return true 如果成功
     */
    public boolean clear(Page page) {
        return write(page, "");
    }

    /**
     * 检查剪贴板是否包含指定文本
     * 
     * @param page Playwright 页面
     * @param text 要检查的文本
     * @return true 如果包含
     */
    public boolean contains(Page page, String text) {
        String content = read(page);
        return content != null && content.contains(text);
    }

    private String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
    
    /**
     * 获取页面的唯一标识
     */
    private String getPageId(Page page) {
        try {
            return String.valueOf(System.identityHashCode(page));
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * 获取或创建页面的剪贴板锁
     */
    private ReentrantLock getPageLock(String pageId) {
        return pageLocks.computeIfAbsent(pageId, k -> new ReentrantLock());
    }
    
    /**
     * 清理页面锁（当页面关闭时调用）
     * 
     * @param page Playwright 页面
     */
    public void cleanupPageLock(Page page) {
        if (page != null) {
            String pageId = getPageId(page);
            ReentrantLock removed = pageLocks.remove(pageId);
            if (removed != null) {
                log.debug("[剪贴板] 清理页面锁 - 页面: {}", pageId);
            }
        }
    }
    
    /**
     * 获取当前持有的锁数量（用于监控）
     */
    public int getLockCount() {
        return pageLocks.size();
    }
    
    /**
     * 清理所有页面锁（在应用关闭时调用）
     */
    public void clearAllLocks() {
        int count = pageLocks.size();
        pageLocks.clear();
        if (count > 0) {
            log.info("[剪贴板] 清理所有页面锁 - 数量: {}", count);
        }
    }
    
    /**
     * 检查是否存在锁泄漏（用于监控）
     * @param maxExpectedLocks 最大预期锁数量
     * @return true 如果可能存在泄漏
     */
    public boolean hasLockLeak(int maxExpectedLocks) {
        int currentLocks = pageLocks.size();
        if (currentLocks > maxExpectedLocks) {
            log.warn("[剪贴板] 可能存在锁泄漏 - 当前锁数: {}, 最大预期: {}", currentLocks, maxExpectedLocks);
            return true;
        }
        return false;
    }
}
