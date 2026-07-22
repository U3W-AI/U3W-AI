package com.wx.fbsir.business.board.credit.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Strict reversal command; target identity, scope, currency and amount are server-derived. */
@JsonDeserialize(using = BoardCreditReversalRequest.StrictDeserializer.class)
public record BoardCreditReversalRequest(
        @NotBlank
        @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        String originalOperationId,
        @NotNull @Min(0) Long expectedAccountVersion,
        @NotBlank @Pattern(regexp = "DUPLICATE_GRANT|OPERATOR_ERROR|POLICY_VIOLATION")
        String reasonCode,
        @NotBlank @Size(min = 8, max = 128)
        @Pattern(regexp = "[^\\p{Cc}\\p{Cf}]{8,128}") String note,
        @NotBlank @Size(min = 16, max = 128)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{15,127}") String idempotencyKey) {

    private static final Set<String> FIELDS =
            Set.of("originalOperationId", "expectedAccountVersion", "reasonCode", "note",
                    "idempotencyKey");

    /** Endpoint-local parser: no caller-selected financial or identity fields can slip through. */
    public static final class StrictDeserializer extends StdDeserializer<BoardCreditReversalRequest> {
        public StrictDeserializer() {
            super(BoardCreditReversalRequest.class);
        }

        @Override
        public BoardCreditReversalRequest deserialize(
                JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw JsonMappingException.from(parser, "CREDIT_REVERSAL_BODY_MUST_BE_OBJECT");
            }
            Set<String> seen = new HashSet<>();
            String originalOperationId = null;
            Long expectedAccountVersion = null;
            String reasonCode = null;
            String note = null;
            String idempotencyKey = null;
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (token != JsonToken.FIELD_NAME) {
                    throw JsonMappingException.from(parser, "CREDIT_REVERSAL_UNEXPECTED_TOKEN");
                }
                String field = parser.currentName();
                if (!FIELDS.contains(field)) {
                    throw JsonMappingException.from(
                            parser, "CREDIT_REVERSAL_UNKNOWN_FIELD:" + field);
                }
                if (!seen.add(field)) {
                    throw JsonMappingException.from(
                            parser, "CREDIT_REVERSAL_DUPLICATE_FIELD:" + field);
                }
                JsonToken valueToken = parser.nextToken();
                if ("expectedAccountVersion".equals(field)) {
                    if (valueToken != JsonToken.VALUE_NUMBER_INT) {
                        throw JsonMappingException.from(
                                parser, "CREDIT_REVERSAL_FIELD_MUST_BE_INTEGER:" + field);
                    }
                    expectedAccountVersion = parser.getLongValue();
                    continue;
                }
                if (valueToken != JsonToken.VALUE_STRING) {
                    throw JsonMappingException.from(
                            parser, "CREDIT_REVERSAL_FIELD_MUST_BE_STRING:" + field);
                }
                String value = parser.getText();
                switch (field) {
                    case "originalOperationId" -> originalOperationId = value;
                    case "reasonCode" -> reasonCode = value;
                    case "note" -> note = value;
                    case "idempotencyKey" -> idempotencyKey = value;
                    default -> throw JsonMappingException.from(
                            parser, "CREDIT_REVERSAL_UNKNOWN_FIELD:" + field);
                }
            }
            for (String field : FIELDS) {
                if (!seen.contains(field)) {
                    throw JsonMappingException.from(
                            parser, "CREDIT_REVERSAL_MISSING_FIELD:" + field);
                }
            }
            if (parser.nextToken() != null) {
                throw JsonMappingException.from(parser, "CREDIT_REVERSAL_TRAILING_TOKEN");
            }
            return new BoardCreditReversalRequest(
                    originalOperationId, expectedAccountVersion, reasonCode, note, idempotencyKey);
        }
    }
}
