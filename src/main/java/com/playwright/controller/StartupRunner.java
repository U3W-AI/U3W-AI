package com.playwright.controller;

/**
 * @author 优立方
 * @version JDK 17
 * @date 2025年02月06日 14:52
 */
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class StartupRunner {

    @Autowired
    private BrowserController browserController;

    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() {
        System.out.println("Spring Boot 启动完成，调用 Controller 方法...");
        browserController.checkLogin("22");
    }
}
