package com.wx.fbsir.business.board.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionRequest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardPlanPolicyRevisionRequestContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestIsAFullPolicyReplacementWithoutCallerSelectedProductOrImmutableIdentity()
            throws Exception {
        BoardPlanPolicyRevisionRequest request = objectMapper.readValue(
                "{\"planCode\":\"BOARD_VIP\",\"expectedVersion\":3,"
                        + "\"planName\":\"独董会 VIP 版\",\"dailyMeetingLimit\":8,"
                        + "\"agendaLimit\":30,\"seatLimit\":null,"
                        + "\"secretaryEnabled\":true,"
                        + "\"rollbackOfReceiptId\":null,"
                        + "\"idempotencyKey\":\"plan:20260722:0001\"}",
                BoardPlanPolicyRevisionRequest.class);

        assertEquals("BOARD_VIP", request.planCode());
        assertEquals(3L, request.expectedVersion());
        assertEquals("独董会 VIP 版", request.planName());
        assertEquals(8, request.dailyMeetingLimit());
        assertEquals(30, request.agendaLimit());
        assertNull(request.seatLimit());
        assertEquals(true, request.secretaryEnabled());
        assertNull(request.rollbackOfReceiptId());
        assertEquals("plan:20260722:0001", request.idempotencyKey());
        assertEquals(List.of(
                        "planCode", "expectedVersion", "planName", "dailyMeetingLimit",
                        "agendaLimit", "seatLimit", "secretaryEnabled", "rollbackOfReceiptId",
                        "idempotencyKey"),
                Arrays.stream(BoardPlanPolicyRevisionRequest.class.getRecordComponents())
                        .map(component -> component.getName()).toList());
        assertFalse(Arrays.stream(BoardPlanPolicyRevisionRequest.class.getRecordComponents())
                .anyMatch(component -> List.of(
                        "productCode", "actorUserId", "vip", "connectorRequired", "status")
                        .contains(component.getName())));
    }

    @Test
    void requestRejectsUnknownDuplicateMissingWrongTypeAndTrailingJson() {
        String valid = "{\"planCode\":\"BOARD_FREE\",\"expectedVersion\":1,"
                + "\"planName\":\"独董会免费版\",\"dailyMeetingLimit\":1,"
                + "\"agendaLimit\":5,\"seatLimit\":3,\"secretaryEnabled\":false,"
                + "\"rollbackOfReceiptId\":null,"
                + "\"idempotencyKey\":\"plan:20260722:0002\"}";
        for (String body : List.of(
                "{\"planCode\":\"BOARD_FREE\",\"expectedVersion\":1,"
                        + "\"planName\":\"独董会免费版\",\"dailyMeetingLimit\":1,"
                        + "\"agendaLimit\":5,\"seatLimit\":3,\"secretaryEnabled\":false,"
                        + "\"rollbackOfReceiptId\":null,"
                        + "\"idempotencyKey\":\"plan:20260722:0002\","
                        + "\"productCode\":\"FBSIR_INDEPENDENT_BOARD\"}",
                "{\"planCode\":\"BOARD_FREE\",\"planCode\":\"BOARD_VIP\","
                        + "\"expectedVersion\":1,\"planName\":\"独董会免费版\","
                        + "\"dailyMeetingLimit\":1,\"agendaLimit\":5,\"seatLimit\":3,"
                        + "\"secretaryEnabled\":false,"
                        + "\"rollbackOfReceiptId\":null,"
                        + "\"idempotencyKey\":\"plan:20260722:0002\"}",
                "{\"planCode\":\"BOARD_FREE\",\"expectedVersion\":1,"
                        + "\"planName\":\"独董会免费版\",\"dailyMeetingLimit\":1,"
                        + "\"agendaLimit\":5,\"seatLimit\":3,"
                        + "\"rollbackOfReceiptId\":null,"
                        + "\"idempotencyKey\":\"plan:20260722:0002\"}",
                "{\"planCode\":\"BOARD_FREE\",\"expectedVersion\":\"1\","
                        + "\"planName\":\"独董会免费版\",\"dailyMeetingLimit\":1,"
                        + "\"agendaLimit\":5,\"seatLimit\":3,\"secretaryEnabled\":false,"
                        + "\"rollbackOfReceiptId\":null,"
                        + "\"idempotencyKey\":\"plan:20260722:0002\"}",
                "{\"planCode\":\"BOARD_FREE\",\"expectedVersion\":1,"
                        + "\"planName\":\"独董会免费版\",\"dailyMeetingLimit\":1,"
                        + "\"agendaLimit\":5,\"seatLimit\":3,\"secretaryEnabled\":0,"
                        + "\"rollbackOfReceiptId\":null,"
                        + "\"idempotencyKey\":\"plan:20260722:0002\"}",
                valid + "{}", valid + "null", valid + "123")) {
            assertThrows(JsonProcessingException.class,
                    () -> objectMapper.readValue(body, BoardPlanPolicyRevisionRequest.class), body);
        }
    }
}
