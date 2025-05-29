package com.playwright.controller;

import com.playwright.websocket.WebSocketClientService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author 优立方
 * @version JDK 17
 * @date 2025年05月17日 08:26
 */
@RestController
@RequestMapping("/api/browser")
public class AIController {

    private final WebSocketClientService webSocketClientService;

    public AIController(WebSocketClientService webSocketClientService) {
        this.webSocketClientService = webSocketClientService;
    }
}
