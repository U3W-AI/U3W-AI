package com.wx.fbsir.business.board.attribution.controller;

import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionEventV1;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionIngestService;
import com.wx.fbsir.common.annotation.Anonymous;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Anonymous
@RestController
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.attribution",
        name = "observation-writer-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardAttributionIngressController {
    static final String PATH =
            "/internal/independent-board/attribution/events";
    private final IndependentBoardAttributionIngestService service;

    public IndependentBoardAttributionIngressController(
            IndependentBoardAttributionIngestService service) {
        this.service = service;
    }

    @PostMapping(path = PATH, consumes = "application/json",
            produces = "application/json")
    public ResponseEntity<IndependentBoardAttributionIngestService.IngestResult>
            ingest(@RequestBody BoardAttributionEventV1 event) {
        IndependentBoardAttributionIngestService.IngestResult result =
                service.ingest(event);
        ResponseEntity.BodyBuilder response = result.idempotentReplay()
                ? ResponseEntity.ok() : ResponseEntity.accepted();
        return response.cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(result);
    }
}
