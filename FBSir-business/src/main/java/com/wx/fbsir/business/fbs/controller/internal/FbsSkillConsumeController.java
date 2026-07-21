package com.wx.fbsir.business.fbs.controller.internal;

import com.wx.fbsir.common.core.domain.AjaxResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill 消费统一入口（内部接口）
 * 对应 design.md §5.5
 *
 * 旧接口没有可验证的服务身份，且信任请求体 userId；当前仅保留退役信号。
 *
 * @author FBSir
 * @date 2026-04-08
 */
@RestController
@RequestMapping("/fbs/internal/usage")
public class FbsSkillConsumeController {

    /**
     * POST /fbs/internal/usage/consume
     * 固定返回 410；不绑定请求体，也不调用消费或积分服务。
     */
    @PostMapping("/consume")
    public ResponseEntity<AjaxResult> consume() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(AjaxResult.error(HttpStatus.GONE.value(),
                        "INTERNAL_SKILL_CONSUME_DISABLED"));
    }
}
