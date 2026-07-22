package com.wx.fbsir.business.board.credit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.board.credit.dto.BoardCreditGrantRequest;
import com.wx.fbsir.business.board.credit.dto.BoardCreditReversalRequest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardCreditRequestContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void grantRequiresExpectedAccountVersionAndNeverCarriesActorOrAccountScope() throws Exception {
        BoardCreditGrantRequest request = objectMapper.readValue(
                "{\"userId\":42,\"expectedAccountVersion\":3,\"amount\":100,"
                        + "\"reasonCode\":\"CUSTOMER_SUPPORT\","
                        + "\"note\":\"approved support grant\","
                        + "\"idempotencyKey\":\"grant:20260722:0001\"}",
                BoardCreditGrantRequest.class);

        assertEquals(42L, request.userId());
        assertEquals(3L, request.expectedAccountVersion());
        assertEquals(100, request.amount());
        assertEquals("CUSTOMER_SUPPORT", request.reasonCode());
        assertEquals("approved support grant", request.note());
        assertEquals("grant:20260722:0001", request.idempotencyKey());
        assertEquals(List.of(
                        "userId", "expectedAccountVersion", "amount", "reasonCode", "note",
                        "idempotencyKey"),
                Arrays.stream(BoardCreditGrantRequest.class.getRecordComponents())
                        .map(component -> component.getName()).toList());
        assertFalse(Arrays.stream(BoardCreditGrantRequest.class.getRecordComponents())
                .anyMatch(component -> List.of(
                        "actorUserId", "accountScope", "currencyCode", "productCode")
                        .contains(component.getName())));
    }

    @Test
    void reversalRequiresExpectedAccountVersionButCarriesNoTargetIdentityScopeCurrencyOrAmount()
            throws Exception {
        BoardCreditReversalRequest request = objectMapper.readValue(
                "{\"originalOperationId\":\"123e4567-e89b-12d3-a456-426614174000\","
                        + "\"expectedAccountVersion\":4,"
                        + "\"reasonCode\":\"OPERATOR_ERROR\","
                        + "\"note\":\"operator correction\","
                        + "\"idempotencyKey\":\"reverse:20260722:0001\"}",
                BoardCreditReversalRequest.class);

        assertEquals("123e4567-e89b-12d3-a456-426614174000", request.originalOperationId());
        assertEquals(4L, request.expectedAccountVersion());
        assertEquals(List.of(
                        "originalOperationId", "expectedAccountVersion", "reasonCode", "note",
                        "idempotencyKey"),
                Arrays.stream(BoardCreditReversalRequest.class.getRecordComponents())
                        .map(component -> component.getName()).toList());
        assertFalse(Arrays.stream(BoardCreditReversalRequest.class.getRecordComponents())
                .anyMatch(component -> List.of(
                        "userId", "amount", "actorUserId", "accountScope",
                        "currencyCode", "productCode")
                        .contains(component.getName())));
    }

    @Test
    void grantRejectsUnknownDuplicateMissingNullWrongTypesAndTrailingJson() {
        String valid = "{\"userId\":42,\"expectedAccountVersion\":3,\"amount\":100,"
                + "\"reasonCode\":\"CUSTOMER_SUPPORT\",\"note\":\"approved support grant\","
                + "\"idempotencyKey\":\"grant:20260722:0001\"}";
        for (String body : List.of(
                "{\"userId\":42,\"amount\":100,\"reasonCode\":\"CUSTOMER_SUPPORT\","
                        + "\"note\":\"approved support grant\","
                        + "\"idempotencyKey\":\"grant:20260722:0001\",\"actorUserId\":9}",
                "{\"userId\":42,\"userId\":43,\"expectedAccountVersion\":3,\"amount\":100,"
                        + "\"reasonCode\":\"CUSTOMER_SUPPORT\",\"note\":\"approved support grant\","
                        + "\"idempotencyKey\":\"grant:20260722:0001\"}",
                "{\"userId\":42,\"expectedAccountVersion\":3,\"amount\":100,"
                        + "\"reasonCode\":\"CUSTOMER_SUPPORT\","
                        + "\"note\":\"approved support grant\"}",
                "{\"userId\":42,\"expectedAccountVersion\":3,\"amount\":null,"
                        + "\"reasonCode\":\"CUSTOMER_SUPPORT\","
                        + "\"note\":\"approved support grant\","
                        + "\"idempotencyKey\":\"grant:20260722:0001\"}",
                "{\"userId\":42,\"expectedAccountVersion\":3,\"amount\":\"100\","
                        + "\"reasonCode\":\"CUSTOMER_SUPPORT\",\"note\":\"approved support grant\","
                        + "\"idempotencyKey\":\"grant:20260722:0001\"}",
                "{\"userId\":42,\"expectedAccountVersion\":\"3\",\"amount\":100,"
                        + "\"reasonCode\":\"CUSTOMER_SUPPORT\",\"note\":\"approved support grant\","
                        + "\"idempotencyKey\":\"grant:20260722:0001\"}",
                valid + "{}", valid + "null", valid + "123")) {
            assertThrows(JsonProcessingException.class,
                    () -> objectMapper.readValue(body, BoardCreditGrantRequest.class), body);
        }
    }

    @Test
    void reversalRejectsCallerSelectedAmountIdentityAndDuplicateOrTrailingFields() {
        String valid = "{\"originalOperationId\":\"123e4567-e89b-12d3-a456-426614174000\","
                + "\"expectedAccountVersion\":4,"
                + "\"reasonCode\":\"OPERATOR_ERROR\",\"note\":\"operator correction\","
                + "\"idempotencyKey\":\"reverse:20260722:0001\"}";
        for (String body : List.of(
                "{\"originalOperationId\":\"123e4567-e89b-12d3-a456-426614174000\","
                        + "\"expectedAccountVersion\":4,"
                        + "\"reasonCode\":\"OPERATOR_ERROR\",\"note\":\"operator correction\","
                        + "\"idempotencyKey\":\"reverse:20260722:0001\",\"amount\":-100}",
                "{\"originalOperationId\":\"123e4567-e89b-12d3-a456-426614174000\","
                        + "\"expectedAccountVersion\":4,"
                        + "\"reasonCode\":\"OPERATOR_ERROR\",\"note\":\"operator correction\","
                        + "\"idempotencyKey\":\"reverse:20260722:0001\",\"userId\":42}",
                "{\"originalOperationId\":\"123e4567-e89b-12d3-a456-426614174000\","
                        + "\"originalOperationId\":\"223e4567-e89b-12d3-a456-426614174000\","
                        + "\"expectedAccountVersion\":4,"
                        + "\"reasonCode\":\"OPERATOR_ERROR\",\"note\":\"operator correction\","
                        + "\"idempotencyKey\":\"reverse:20260722:0001\"}",
                valid + "{}")) {
            assertThrows(JsonProcessingException.class,
                    () -> objectMapper.readValue(body, BoardCreditReversalRequest.class), body);
        }
    }
}
