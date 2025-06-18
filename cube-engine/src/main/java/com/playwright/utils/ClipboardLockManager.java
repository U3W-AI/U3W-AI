package com.playwright.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2025年06月17日 14:06
 */
@Component
public class ClipboardLockManager {

    private final ReentrantLock lock = new ReentrantLock();

    public void runWithClipboardLock(Runnable action) {
        try {
            lock.lock();
            action.run();
        } finally {
            lock.unlock();
        }
    }
}
