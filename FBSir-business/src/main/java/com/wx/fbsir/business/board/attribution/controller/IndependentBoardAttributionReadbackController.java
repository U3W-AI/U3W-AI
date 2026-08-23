package com.wx.fbsir.business.board.attribution.controller;

import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackRequestV1;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackRequestVerifier;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackResponseV1;
import com.wx.fbsir.business.board.attribution.receipt.VerifiedBoardAttributionReadbackRequest;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionReadbackService;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionReadbackAdmission;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionReadbackUnavailableException;
import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.common.annotation.Anonymous;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Anonymous
@RestController
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.attribution",
        name = "authoritative-readback-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardAttributionReadbackController {
    public static final String PATH =
            "/internal/independent-board/attribution/events/readback";
    private static final String RETRY_AFTER_SECONDS = "1";

    private final IndependentBoardAttributionReadbackService service;
    private final IndependentBoardAttributionReadbackAdmission admission;
    private final BoardAttributionReadbackRequestVerifier verifier;
    private final IndependentBoardAttributionProperties properties;

    public IndependentBoardAttributionReadbackController(
            IndependentBoardAttributionReadbackService service,
            IndependentBoardAttributionReadbackAdmission admission,
            BoardAttributionReadbackRequestVerifier verifier,
            IndependentBoardAttributionProperties properties) {
        this.service = service;
        this.admission = admission;
        this.verifier = verifier;
        this.properties = properties;
    }

    @PostMapping(path = PATH, consumes = "application/json",
            produces = "application/json")
    public ResponseEntity<BoardAttributionReadbackResponseV1> readback(
            @RequestBody BoardAttributionReadbackRequestV1 request) {
        VerifiedBoardAttributionReadbackRequest verified =
                verifier.verify(request, properties);
        try (var ignored = admission.acquire(verified.signerKeyId())) {
            BoardAttributionReadbackResponseV1 response =
                    service.read(verified);
            HttpStatus status = httpStatus(response.status());
            BoardAttributionReadbackResponseV1 signed =
                    verifier.signResponse(
                            response, verified, status.value(), properties);
            return response(status, signed, false);
        } catch (DataAccessException
                | TransactionException
                | IndependentBoardAttributionReadbackUnavailableException error) {
            BoardAttributionReadbackResponseV1 signed =
                    verifier.signResponse(
                            service.unavailable(verified),
                            verified,
                            HttpStatus.SERVICE_UNAVAILABLE.value(),
                            properties);
            return response(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    signed,
                    true);
        }
    }

    private ResponseEntity<BoardAttributionReadbackResponseV1> response(
            HttpStatus status,
            BoardAttributionReadbackResponseV1 body,
            boolean retryable) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0");
        if (retryable) {
            builder.header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        }
        return builder.body(body);
    }

    private HttpStatus httpStatus(String status) {
        return switch (status) {
            case IndependentBoardAttributionReadbackService.COMMITTED_EXACT ->
                    HttpStatus.OK;
            case IndependentBoardAttributionReadbackService.NOT_FOUND_AUTHORITATIVE ->
                    HttpStatus.NOT_FOUND;
            case IndependentBoardAttributionReadbackService.IDENTITY_COLLISION ->
                    HttpStatus.CONFLICT;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
