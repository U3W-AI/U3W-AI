package com.wx.fbsir.business.board.plan.dto;

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

/** Strict full-replacement command for one reviewed plan policy revision. */
@JsonDeserialize(using = BoardPlanPolicyRevisionRequest.StrictDeserializer.class)
public record BoardPlanPolicyRevisionRequest(
        @NotBlank @Pattern(regexp = "BOARD_FREE|BOARD_VIP") String planCode,
        @NotNull @Min(1) @Max(9_223_372_036_854_775_806L) Long expectedVersion,
        @NotBlank @Size(max = 128)
        @Pattern(regexp = "[^\\p{Cc}\\p{Cf}]{1,128}") String planName,
        @NotNull @Min(1) @Max(10_000) Integer dailyMeetingLimit,
        @NotNull @Min(1) @Max(30) Integer agendaLimit,
        @Min(1) @Max(100) Integer seatLimit,
        @NotNull Boolean secretaryEnabled,
        @Size(min = 16, max = 128)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{15,127}") String rollbackOfReceiptId,
        @NotBlank @Size(min = 16, max = 128)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{15,127}") String idempotencyKey) {

    private static final Set<String> FIELDS = Set.of(
            "planCode", "expectedVersion", "planName", "dailyMeetingLimit",
            "agendaLimit", "seatLimit", "secretaryEnabled", "rollbackOfReceiptId",
            "idempotencyKey");

    /** Unknown, duplicate, missing, mistyped and trailing JSON fail closed. */
    public static final class StrictDeserializer
            extends StdDeserializer<BoardPlanPolicyRevisionRequest> {
        public StrictDeserializer() {
            super(BoardPlanPolicyRevisionRequest.class);
        }

        @Override
        public BoardPlanPolicyRevisionRequest deserialize(
                JsonParser parser, DeserializationContext context) throws IOException {
            requireObject(parser);
            Set<String> seen = new HashSet<>();
            String planCode = null;
            Long expectedVersion = null;
            String planName = null;
            Integer dailyMeetingLimit = null;
            Integer agendaLimit = null;
            Integer seatLimit = null;
            Boolean secretaryEnabled = null;
            String rollbackOfReceiptId = null;
            String idempotencyKey = null;
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (token != JsonToken.FIELD_NAME) {
                    throw JsonMappingException.from(parser, "BOARD_PLAN_REVISION_UNEXPECTED_TOKEN");
                }
                String field = parser.currentName();
                requireKnownOnce(parser, seen, field);
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "planCode" -> planCode = readString(parser, valueToken, field);
                    case "expectedVersion" ->
                            expectedVersion = readLong(parser, valueToken, field);
                    case "planName" -> planName = readString(parser, valueToken, field);
                    case "dailyMeetingLimit" ->
                            dailyMeetingLimit = readInt(parser, valueToken, field);
                    case "agendaLimit" -> agendaLimit = readInt(parser, valueToken, field);
                    case "seatLimit" -> seatLimit = valueToken == JsonToken.VALUE_NULL
                            ? null : readInt(parser, valueToken, field);
                    case "secretaryEnabled" ->
                            secretaryEnabled = readBoolean(parser, valueToken, field);
                    case "rollbackOfReceiptId" -> rollbackOfReceiptId =
                            valueToken == JsonToken.VALUE_NULL
                                    ? null : readString(parser, valueToken, field);
                    case "idempotencyKey" ->
                            idempotencyKey = readString(parser, valueToken, field);
                    default -> throw JsonMappingException.from(
                            parser, "BOARD_PLAN_REVISION_UNKNOWN_FIELD:" + field);
                }
            }
            requireAllAndEnd(parser, seen);
            return new BoardPlanPolicyRevisionRequest(
                    planCode, expectedVersion, planName, dailyMeetingLimit, agendaLimit,
                    seatLimit, secretaryEnabled, rollbackOfReceiptId, idempotencyKey);
        }

        private static void requireObject(JsonParser parser) throws JsonMappingException {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw JsonMappingException.from(parser, "BOARD_PLAN_REVISION_BODY_MUST_BE_OBJECT");
            }
        }

        private static void requireKnownOnce(
                JsonParser parser, Set<String> seen, String field) throws JsonMappingException {
            if (!FIELDS.contains(field)) {
                throw JsonMappingException.from(
                        parser, "BOARD_PLAN_REVISION_UNKNOWN_FIELD:" + field);
            }
            if (!seen.add(field)) {
                throw JsonMappingException.from(
                        parser, "BOARD_PLAN_REVISION_DUPLICATE_FIELD:" + field);
            }
        }

        private static void requireAllAndEnd(JsonParser parser, Set<String> seen)
                throws IOException {
            for (String field : FIELDS) {
                if (!seen.contains(field)) {
                    throw JsonMappingException.from(
                            parser, "BOARD_PLAN_REVISION_MISSING_FIELD:" + field);
                }
            }
            if (parser.nextToken() != null) {
                throw JsonMappingException.from(parser, "BOARD_PLAN_REVISION_TRAILING_TOKEN");
            }
        }

        private static String readString(JsonParser parser, JsonToken token, String field)
                throws IOException {
            if (token != JsonToken.VALUE_STRING) {
                throw JsonMappingException.from(
                        parser, "BOARD_PLAN_REVISION_FIELD_MUST_BE_STRING:" + field);
            }
            return parser.getText();
        }

        private static Long readLong(JsonParser parser, JsonToken token, String field)
                throws IOException {
            if (token != JsonToken.VALUE_NUMBER_INT) {
                throw JsonMappingException.from(
                        parser, "BOARD_PLAN_REVISION_FIELD_MUST_BE_INTEGER:" + field);
            }
            return parser.getLongValue();
        }

        private static Integer readInt(JsonParser parser, JsonToken token, String field)
                throws IOException {
            if (token != JsonToken.VALUE_NUMBER_INT) {
                throw JsonMappingException.from(
                        parser, "BOARD_PLAN_REVISION_FIELD_MUST_BE_INTEGER:" + field);
            }
            return parser.getIntValue();
        }

        private static Boolean readBoolean(JsonParser parser, JsonToken token, String field)
                throws JsonMappingException {
            if (token == JsonToken.VALUE_TRUE) {
                return Boolean.TRUE;
            }
            if (token == JsonToken.VALUE_FALSE) {
                return Boolean.FALSE;
            }
            throw JsonMappingException.from(
                    parser, "BOARD_PLAN_REVISION_FIELD_MUST_BE_BOOLEAN:" + field);
        }
    }
}
