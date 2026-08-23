package com.wx.fbsir.business.board.attribution;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBoardAttributionV1PersistenceContractTest {
    private static final String MIGRATION =
            "update_20260723_independent_board_attribution_v1.sql";
    private static final String IDENTITY_REGISTRY_MIGRATION =
            "update_20260823_independent_board_attribution_identity_registry.sql";

    @Test
    void migrationIsAnExactOfficialAppendOnlySuccessor() throws IOException {
        String sql = normalized(locate("sql", MIGRATION));
        assertTrue(sql.contains(
                "create table if not exists `fbs_board_attr_journey_v1`"));
        assertTrue(sql.contains(
                "create table if not exists `fbs_board_attr_event_v1`"));
        assertTrue(sql.contains(
                "`listed_manifest_version` = '26.7.21'"));
        assertTrue(sql.contains(
                "`embedded_contract_version` = '26.7.20'"));
        assertTrue(sql.contains(
                "`product_id` = 'fbsir-eight-seat-board'"));
        assertTrue(sql.contains(
                "`package_id` = 'fbsir-eight-seat-board'"));
        assertTrue(sql.contains("`agent_name` = 'board-convener'"));
        assertTrue(sql.contains("`marketplace` = 'experts'"));
        assertTrue(sql.contains(
                "`event_type` = 'entry_observed' and `sequence_no` = 1"));
        assertTrue(sql.contains(
                "`event_type` = 'intent_classified' and `sequence_no` = 2"));
        assertTrue(sql.contains(
                "`event_type` = 'first_value_completed' and `sequence_no` = 3"));
        assertTrue(sql.contains(
                "unique key `uk_board_attr_event_binding_sequence`"));
        assertTrue(sql.contains("authoritative_product_credit"));
        assertTrue(sql.contains(
                "check (`authoritative_product_credit` = 0)"));
        assertTrue(sql.contains(
                "create trigger `trg_board_attr_event_v1_no_update`"));
        assertTrue(sql.contains(
                "create trigger `trg_board_attr_event_v1_no_delete`"));
        assertTrue(sql.contains("board:attribution:query"));
        assertFalse(sql.contains(
                "alter table `fbs_attribution_product_contract`"));
        assertFalse(sql.contains("connector_required"));
    }

    @Test
    void mapperLocksOnlyTheMutableHead() throws IOException {
        String xml = normalized(locate(
                "FBSir-business/src/main/resources/mapper/board/attribution",
                "IndependentBoardAttributionV1Mapper.xml"));
        assertTrue(xml.contains(
                "<select id=\"selectjourneyheadforupdate\""));
        assertTrue(xml.contains("from fbs_board_attr_journey_v1"));
        assertTrue(xml.contains("for update"));
        assertTrue(xml.contains("<select id=\"selecteventbyeventid\""));
        assertTrue(xml.contains("<select id=\"selecteventbyreceiptid\""));
        String eventRead = statement(xml,
                "<select id=\"selecteventbyeventid\"",
                "</select>");
        String eventReadForUpdate = statement(xml,
                "<select id=\"selecteventbyeventidforupdate\"",
                "</select>");
        String receiptRead = statement(xml,
                "<select id=\"selecteventbyreceiptid\"",
                "</select>");
        String receiptReadForUpdate = statement(xml,
                "<select id=\"selecteventbyreceiptidforupdate\"",
                "</select>");
        assertFalse(eventRead.contains("for update"));
        assertFalse(receiptRead.contains("for update"));
        assertTrue(eventReadForUpdate.contains("for update"));
        assertTrue(receiptReadForUpdate.contains("for update"));
        assertTrue(xml.contains(
                "where same_binding_key = #{samebindingkey}"));
        assertTrue(xml.contains(
                "and head_version = #{expectedheadversion}"));
        assertTrue(xml.contains(
                "and last_sequence_no = #{expectedlastsequenceno}"));
        assertFalse(xml.contains("insert ignore into fbs_board_attr_journey_v1"));
        assertTrue(xml.contains("on duplicate key update"));
    }

    @Test
    void identityRegistrySuccessorKeepsLegacyAndAddsOnlyThreeExactProfiles()
            throws IOException {
        String sql = normalized(locate("sql", IDENTITY_REGISTRY_MIGRATION));
        assertTrue(sql.contains("drop check `chk_board_attr_journey_versions`"));
        assertTrue(sql.contains("drop check `chk_board_attr_event_versions`"));
        assertTrue(sql.contains(
                "`host_client_family` = 'workbuddy' and `listed_manifest_version` = '26.7.21'"));
        assertTrue(sql.contains(
                "`host_client_family` in ('workbuddy', 'workbuddyai') and `listed_manifest_version` = '26.8.19'"));
        assertTrue(sql.contains(
                "`embedded_contract_version` = '26.8.19'"));
        assertTrue(sql.contains(
                "drop index `uk_board_attr_journey_identity`"));
        assertTrue(sql.contains(
                "`product_id`, `listed_manifest_version`, `embedded_contract_version`"));
        assertTrue(sql.contains(
                "20260823_independent_board_attribution_identity_registry_044"));
        assertFalse(sql.contains("update `fbs_board_attr_event_v1`"));
        assertFalse(sql.contains("delete from `fbs_board_attr_event_v1`"));
        assertFalse(sql.contains("authoritative_product_credit` = 1"));
    }

    @Test
    void publicManifestBinds043WithoutChanging037() throws IOException {
        String manifest = Files.readString(
                locate("sql", "init-manifest.json"), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("\"version\": \"public_init_043\""));
        assertTrue(manifest.contains("\"file\": \"" + MIGRATION + "\""));
        assertTrue(manifest.contains(
                "\"public_init_037\"") || manifest.contains(
                "\"version\": \"public_init_037\""));
        assertTrue(manifest.contains(
                "59e3696ff3f8d4a16b4659c94a108f35fb1badf2f4de16079229c031a0b44cce"));
        assertTrue(manifest.contains("\"version\": \"public_init_044\""));
        assertTrue(manifest.contains(
                "\"file\": \"" + IDENTITY_REGISTRY_MIGRATION + "\""));
    }

    private static String normalized(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static Path locate(String directory, String name) {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            Path candidate = cursor.resolve(directory).resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("required artifact not found: " + name);
    }

    private static String statement(
            String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException(
                    "required mapper statement missing: " + startMarker);
        }
        return source.substring(start, end + endMarker.length());
    }
}
