package com.wx.fbsir.business.board.attribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBoardAttributionIdentityRegistryContractTest {
    private static final String REGISTRY_RESOURCE =
            "contracts/independent-board-attribution-identity-registry-v1.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void canonicalRegistryDrivesJavaAndMatchesExactSqlProfileSet()
            throws Exception {
        JsonNode registry = registry();
        assertEquals(
                "fbsir.independentBoardAttributionIdentityRegistry.v1",
                registry.path("schemaVersion").asText());
        assertFalse(registry.path("productCreditEligible").asBoolean(true));
        assertEquals(3, registry.path("profiles").size());

        Map<String, List<String>> hostsByVersions = new LinkedHashMap<>();
        for (JsonNode profile : registry.path("profiles")) {
            String pair = profile.path("listedManifestVersion").asText()
                    + "|"
                    + profile.path("embeddedContractVersion").asText();
            hostsByVersions.computeIfAbsent(
                    pair, ignored -> new ArrayList<>()).add(
                    profile.path("hostClientFamily").asText());
        }

        List<String> eventBranches = new ArrayList<>();
        List<String> journeyBranches = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry
                : hostsByVersions.entrySet()) {
            String[] versions = entry.getKey().split("\\|", -1);
            String hostClause = entry.getValue().size() == 1
                    ? "`host_client_family` = '"
                        + entry.getValue().get(0) + "'"
                    : "`host_client_family` in ('"
                        + String.join("', '", entry.getValue()) + "')";
            eventBranches.add("(" + hostClause
                    + " and `listed_manifest_version` = '" + versions[0]
                    + "' and `embedded_contract_version` = '" + versions[1]
                    + "')");
            journeyBranches.add("(`listed_manifest_version` = '"
                    + versions[0]
                    + "' and `embedded_contract_version` = '" + versions[1]
                    + "')");
        }

        String sql = normalized(locate(
                "sql",
                "update_20260823_independent_board_attribution_identity_registry.sql"));
        assertTrue(sql.contains(
                String.join(" or ", eventBranches).toLowerCase(Locale.ROOT)));
        assertTrue(sql.contains(
                String.join(" or ", journeyBranches)
                        .toLowerCase(Locale.ROOT)));

        String verifier = Files.readString(locate(
                "FBSir-business/src/main/java/com/wx/fbsir/business/board/attribution/receipt",
                "BoardAttributionEventV1Verifier.java"),
                StandardCharsets.UTF_8);
        assertTrue(verifier.contains(
                "/contracts/independent-board-attribution-identity-registry-v1.json"));
        assertFalse(verifier.contains("\"26.7.21\""));
        assertFalse(verifier.contains("\"26.8.19\""));
    }

    private JsonNode registry() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream(REGISTRY_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(
                        "canonical identity registry missing");
            }
            return JSON.readTree(input);
        }
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
        throw new IllegalStateException(
                "required artifact not found: " + name);
    }
}
