package com.wx.fbsir.business.board;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.board.dto.BoardEntitlementRevokeRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardEntitlementRevokeRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsExactlyOneOccurrenceOfEachContractField() throws Exception {
        BoardEntitlementRevokeRequest request = objectMapper.readValue(
                "{\"tenantId\":7,\"memberId\":11,\"userId\":42,\"expectedVersion\":3}",
                BoardEntitlementRevokeRequest.class);

        assertEquals(new BoardEntitlementRevokeRequest(7L, 11L, 42L, 3L), request);
    }

    @Test
    void rejectsEveryDuplicateContractFieldBeforeConstructingTheDto() {
        for (String body : duplicateFieldBodies()) {
            assertThrows(JsonProcessingException.class, () -> objectMapper.readValue(
                    body, BoardEntitlementRevokeRequest.class));
        }
    }

    @Test
    void rejectsUnknownMissingNullAndNonIntegerFieldsLocally() {
        for (String body : List.of(
                "{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                        + "\"expectedVersion\":3,\"reason\":\"MEMBER_LEFT\"}",
                "{\"tenantId\":7,\"memberId\":11,\"userId\":42}",
                "{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                        + "\"expectedVersion\":null}",
                "{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                        + "\"expectedVersion\":\"3\"}")) {
            assertThrows(JsonProcessingException.class, () -> objectMapper.readValue(
                    body, BoardEntitlementRevokeRequest.class));
        }
    }

    @Test
    void rejectsTrailingObjectNullAndNumberTokensLocally() {
        String valid = "{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                + "\"expectedVersion\":3}";
        for (String body : List.of(valid + "{}", valid + "null", valid + "123")) {
            assertThrows(JsonProcessingException.class, () -> objectMapper.readValue(
                    body, BoardEntitlementRevokeRequest.class));
        }
    }

    private List<String> duplicateFieldBodies() {
        return List.of(
                "{\"tenantId\":7,\"tenantId\":8,\"memberId\":11,"
                        + "\"userId\":42,\"expectedVersion\":3}",
                "{\"tenantId\":7,\"memberId\":11,\"memberId\":12,"
                        + "\"userId\":42,\"expectedVersion\":3}",
                "{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                        + "\"userId\":43,\"expectedVersion\":3}",
                "{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                        + "\"expectedVersion\":3,\"expectedVersion\":4}");
    }
}
