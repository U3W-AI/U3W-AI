package com.wx.fbsir.business.board.credit.controller;

import com.wx.fbsir.business.board.credit.dto.BoardCreditAuditEnvelope;
import com.wx.fbsir.business.board.credit.dto.BoardCreditAuditRecord;
import com.wx.fbsir.business.board.credit.dto.BoardCreditGrantRequest;
import com.wx.fbsir.business.board.credit.dto.BoardCreditReversalRequest;
import com.wx.fbsir.business.board.credit.service.IndependentBoardCreditService;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Global-administrator surface for the isolated credit shadow ledger. */
@Validated
@RestController
@RequestMapping("/business/independent-board")
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.credit-candidate",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardCreditAdminController extends BaseController {
    private final IndependentBoardCreditService creditService;

    public IndependentBoardCreditAdminController(IndependentBoardCreditService creditService) {
        this.creditService = creditService;
    }

    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:credit:grant')")
    @PostMapping("/credit-operations/grants")
    public ResponseEntity<AjaxResult> grant(
            @Valid @RequestBody BoardCreditGrantRequest request) {
        return successResponse(creditService.grant(request, getUserId()));
    }

    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:credit:reverse')")
    @PostMapping("/credit-operations/reversals")
    public ResponseEntity<AjaxResult> reverse(
            @Valid @RequestBody BoardCreditReversalRequest request) {
        return successResponse(creditService.reverse(request, getUserId()));
    }

    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:credit:query')")
    @GetMapping("/credit-accounts/{userId}")
    public ResponseEntity<AjaxResult> account(
            @PathVariable("userId") @Min(1) Long userId) {
        return successResponse(toSafeAccountView(creditService.audit(userId)));
    }

    private static CreditAccountView toSafeAccountView(BoardCreditAuditEnvelope envelope) {
        Objects.requireNonNull(envelope, "credit audit envelope");
        List<CreditOperationView> records = Objects.requireNonNull(
                        envelope.records(), "credit audit records")
                .stream()
                .map(IndependentBoardCreditAdminController::toSafeOperationView)
                .toList();
        return new CreditAccountView(
                envelope.userId(),
                envelope.accountScope(),
                envelope.currencyCode(),
                envelope.openingBalance(),
                envelope.balance(),
                envelope.version(),
                envelope.updatedAt(),
                records,
                envelope.limit(),
                envelope.truncated());
    }

    private static CreditOperationView toSafeOperationView(BoardCreditAuditRecord record) {
        Objects.requireNonNull(record, "credit audit record");
        return new CreditOperationView(
                record.operationId(),
                record.operationType(),
                record.delta(),
                record.reasonCode(),
                record.actorUserId(),
                record.reversalOfOperationId(),
                record.balanceBefore(),
                record.balanceAfter(),
                record.sequenceNo(),
                record.createdAt());
    }

    private static ResponseEntity<AjaxResult> successResponse(Object data) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(AjaxResult.success(data));
    }

    /** Query projection intentionally excludes account IDs, idempotency keys, digests and hashes. */
    public record CreditAccountView(
            Long userId,
            String accountScope,
            String currencyCode,
            Long openingBalance,
            Long balance,
            Long version,
            Date updatedAt,
            List<CreditOperationView> records,
            int limit,
            boolean truncated) {
    }

    /** Safe administrator operation view; the free-form audit note remains ledger-internal. */
    public record CreditOperationView(
            String operationId,
            String operationType,
            Long delta,
            String reasonCode,
            Long actorUserId,
            String reversalOfOperationId,
            Long balanceBefore,
            Long balanceAfter,
            Long sequenceNo,
            Date createdAt) {
    }
}
