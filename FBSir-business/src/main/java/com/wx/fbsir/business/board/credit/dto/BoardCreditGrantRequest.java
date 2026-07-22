package com.wx.fbsir.business.board.credit.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Strict command contract for an explicitly authorized administrative credit grant. */
@JsonDeserialize(using = BoardCreditGrantRequest.StrictDeserializer.class)
public record BoardCreditGrantRequest(
        @NotNull @Min(1) Long userId,
        @NotNull @Min(0) Long expectedAccountVersion,
        @NotNull @Min(1) @Max(100_000) Integer amount,
        @NotBlank @Pattern(regexp = "CUSTOMER_SUPPORT|SERVICE_RECOVERY|MIGRATION_CORRECTION")
        String reasonCode,
        @NotBlank @Size(min = 8, max = 128)
        @Pattern(regexp = "[^\\p{Cc}\\p{Cf}]{8,128}") String note,
        @NotBlank @Size(min = 16, max = 128)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{15,127}") String idempotencyKey) {

    private static final Set<String> FIELDS =
            Set.of("userId", "expectedAccountVersion", "amount", "reasonCode", "note",
                    "idempotencyKey");

    /** Endpoint-local parser: unknown, duplicate, missing and trailing JSON fail closed. */
    public static final class StrictDeserializer extends StdDeserializer<BoardCreditGrantRequest> {
        public StrictDeserializer() {
            super(BoardCreditGrantRequest.class);
        }

        @Override
        public BoardCreditGrantRequest deserialize(
                JsonParser parser, DeserializationContext context) throws IOException {
            requireObject(parser, "CREDIT_GRANT_BODY_MUST_BE_OBJECT");
            Set<String> seen = new HashSet<>();
            Long userId = null;
            Long expectedAccountVersion = null;
            Integer amount = null;
            String reasonCode = null;
            String note = null;
            String idempotencyKey = null;
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (token != JsonToken.FIELD_NAME) {
                    throw JsonMappingException.from(parser, "CREDIT_GRANT_UNEXPECTED_TOKEN");
                }
                String field = parser.currentName();
                requireKnownOnce(parser, seen, field, "CREDIT_GRANT");
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "userId" -> userId = readLong(parser, valueToken, field);
                    case "expectedAccountVersion" ->
                            expectedAccountVersion = readLong(parser, valueToken, field);
                    case "amount" -> amount = readInt(parser, valueToken, field);
                    case "reasonCode" -> reasonCode = readString(parser, valueToken, field);
                    case "note" -> note = readString(parser, valueToken, field);
                    case "idempotencyKey" -> idempotencyKey = readString(parser, valueToken, field);
                    default -> throw JsonMappingException.from(
                            parser, "CREDIT_GRANT_UNKNOWN_FIELD:" + field);
                }
            }
            requireAllAndEnd(parser, seen, "CREDIT_GRANT");
            return new BoardCreditGrantRequest(
                    userId, expectedAccountVersion, amount, reasonCode, note, idempotencyKey);
        }

        private static void requireKnownOnce(
                JsonParser parser, Set<String> seen, String field, String prefix)
                throws JsonMappingException {
            if (!FIELDS.contains(field)) {
                throw JsonMappingException.from(parser, prefix + "_UNKNOWN_FIELD:" + field);
            }
            if (!seen.add(field)) {
                throw JsonMappingException.from(parser, prefix + "_DUPLICATE_FIELD:" + field);
            }
        }

        private static void requireAllAndEnd(
                JsonParser parser, Set<String> seen, String prefix) throws IOException {
            for (String field : FIELDS) {
                if (!seen.contains(field)) {
                    throw JsonMappingException.from(parser, prefix + "_MISSING_FIELD:" + field);
                }
            }
            if (parser.nextToken() != null) {
                throw JsonMappingException.from(parser, prefix + "_TRAILING_TOKEN");
            }
        }

        private static void requireObject(JsonParser parser, String error)
                throws JsonMappingException {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw JsonMappingException.from(parser, error);
            }
        }

        private static Long readLong(JsonParser parser, JsonToken token, String field)
                throws IOException {
            if (token != JsonToken.VALUE_NUMBER_INT) {
                throw JsonMappingException.from(
                        parser, "CREDIT_GRANT_FIELD_MUST_BE_INTEGER:" + field);
            }
            return parser.getLongValue();
        }

        private static Integer readInt(JsonParser parser, JsonToken token, String field)
                throws IOException {
            if (token != JsonToken.VALUE_NUMBER_INT) {
                throw JsonMappingException.from(
                        parser, "CREDIT_GRANT_FIELD_MUST_BE_INTEGER:" + field);
            }
            return parser.getIntValue();
        }

        private static String readString(JsonParser parser, JsonToken token, String field)
                throws IOException {
            if (token != JsonToken.VALUE_STRING) {
                throw JsonMappingException.from(
                        parser, "CREDIT_GRANT_FIELD_MUST_BE_STRING:" + field);
            }
            return parser.getText();
        }
    }
}
