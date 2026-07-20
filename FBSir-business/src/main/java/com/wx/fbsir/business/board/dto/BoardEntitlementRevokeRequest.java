package com.wx.fbsir.business.board.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Exact optimistic-concurrency contract for a controlled entitlement revocation. */
@JsonDeserialize(using = BoardEntitlementRevokeRequest.StrictDeserializer.class)
public record BoardEntitlementRevokeRequest(
        @NotNull @Min(1) Long tenantId,
        @NotNull @Min(1) Long memberId,
        @NotNull @Min(1) Long userId,
        @NotNull @Min(1) Long expectedVersion) {

    private static final Set<String> FIELDS =
            Set.of("tenantId", "memberId", "userId", "expectedVersion");

    /** Local fail-closed parser so unrelated endpoints keep their existing Jackson policy. */
    public static final class StrictDeserializer extends StdDeserializer<BoardEntitlementRevokeRequest> {
        public StrictDeserializer() {
            super(BoardEntitlementRevokeRequest.class);
        }

        @Override
        public BoardEntitlementRevokeRequest deserialize(
                JsonParser parser,
                DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw JsonMappingException.from(parser, "ENTITLEMENT_REVOKE_BODY_MUST_BE_OBJECT");
            }

            Set<String> seen = new HashSet<>();
            Long tenantId = null;
            Long memberId = null;
            Long userId = null;
            Long expectedVersion = null;
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (token != JsonToken.FIELD_NAME) {
                    throw JsonMappingException.from(
                            parser, "ENTITLEMENT_REVOKE_UNEXPECTED_TOKEN");
                }
                String field = parser.currentName();
                if (!FIELDS.contains(field)) {
                    throw JsonMappingException.from(
                            parser, "ENTITLEMENT_REVOKE_UNKNOWN_FIELD:" + field);
                }
                if (!seen.add(field)) {
                    throw JsonMappingException.from(
                            parser, "ENTITLEMENT_REVOKE_DUPLICATE_FIELD:" + field);
                }
                if (parser.nextToken() != JsonToken.VALUE_NUMBER_INT) {
                    throw JsonMappingException.from(
                            parser, "ENTITLEMENT_REVOKE_FIELD_MUST_BE_INTEGER:" + field);
                }
                long value = parser.getLongValue();
                switch (field) {
                    case "tenantId" -> tenantId = value;
                    case "memberId" -> memberId = value;
                    case "userId" -> userId = value;
                    case "expectedVersion" -> expectedVersion = value;
                    default -> throw JsonMappingException.from(
                            parser, "ENTITLEMENT_REVOKE_UNKNOWN_FIELD:" + field);
                }
            }
            for (String field : FIELDS) {
                if (!seen.contains(field)) {
                    throw JsonMappingException.from(
                            parser, "ENTITLEMENT_REVOKE_MISSING_FIELD:" + field);
                }
            }
            if (parser.nextToken() != null) {
                throw JsonMappingException.from(
                        parser, "ENTITLEMENT_REVOKE_TRAILING_TOKEN");
            }
            return new BoardEntitlementRevokeRequest(
                    tenantId, memberId, userId, expectedVersion);
        }
    }
}
