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
        String immutableReads = xml.substring(
                xml.indexOf("<select id=\"selecteventbyeventid\""),
                xml.indexOf("<insert id=\"insertjourneyheadifabsent\""));
        assertFalse(immutableReads.contains("for update"));
        assertTrue(xml.contains(
                "where same_binding_key = #{samebindingkey}"));
        assertTrue(xml.contains(
                "and head_version = #{expectedheadversion}"));
        assertTrue(xml.contains(
                "and last_sequence_no = #{expectedlastsequenceno}"));
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
}
