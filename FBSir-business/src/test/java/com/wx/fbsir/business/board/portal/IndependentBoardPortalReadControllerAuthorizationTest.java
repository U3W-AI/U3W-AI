package com.wx.fbsir.business.board.portal;

import com.wx.fbsir.common.annotation.Anonymous;
import com.wx.fbsir.common.annotation.Log;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class IndependentBoardPortalReadControllerAuthorizationTest {

    private static final String ME_CONTROLLER =
            "com.wx.fbsir.business.board.portal.controller.IndependentBoardPortalMeReadController";
    private static final String ADMIN_CONTROLLER =
            "com.wx.fbsir.business.board.portal.controller.IndependentBoardPortalAdminReadController";

    @Test
    void candidateControllersAreDefaultOffAndExposeOnlyTheFiveApprovedReads() throws Exception {
        Class<?> meController = Class.forName(ME_CONTROLLER);
        Class<?> adminController = Class.forName(ADMIN_CONTROLLER);

        assertCandidateController(meController, "/my/independent-board");
        assertCandidateController(adminController, "/business/independent-board");

        assertGet(meController, "connector", "/connector",
                "isAuthenticated() and @ss.hasPermi('my:independent-board:connector:view')");
        assertGet(adminController, "oauthClients", "/oauth/clients",
                "@ss.hasRole('admin') and @ss.hasPermi('board:oauth:client:query')");
        assertGet(adminController, "oauthFamilies", "/oauth/families",
                "@ss.hasRole('admin') and @ss.hasPermi('board:oauth:family:query')");
        assertGet(adminController, "connectorBindings", "/connector-bindings",
                "@ss.hasRole('admin') and @ss.hasPermi('board:connector:query')");
        assertGet(adminController, "tenants", "/tenants",
                "@ss.hasRole('admin') and @ss.hasPermi('board:tenant:query')");

        Map<Class<?>, Set<String>> paths = Map.of(
                meController, declaredGetPaths(meController),
                adminController, declaredGetPaths(adminController));
        assertEquals(Set.of("/connector"), paths.get(meController));
        assertEquals(Set.of(
                        "/oauth/clients", "/oauth/families", "/connector-bindings",
                        "/tenants"),
                paths.get(adminController));
        assertFalse(paths.values().stream().flatMap(Set::stream)
                .anyMatch(path -> path.contains("security-receipt")
                        || path.contains("security-event")));
    }

    private static void assertCandidateController(Class<?> controller, String basePath) {
        RequestMapping requestMapping = controller.getAnnotation(RequestMapping.class);
        assertNotNull(requestMapping);
        assertEquals(Set.of(basePath), Set.of(requestMapping.value()));

        ConditionalOnProperty condition = controller.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(condition);
        assertEquals("fbsir.independent-board.portal-candidate", condition.prefix());
        assertEquals(Set.of("enabled"), Set.of(condition.name()));
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());
        assertNull(controller.getAnnotation(Anonymous.class));

        for (Method method : controller.getDeclaredMethods()) {
            assertNull(method.getAnnotation(PostMapping.class));
            assertNull(method.getAnnotation(PutMapping.class));
            assertNull(method.getAnnotation(PatchMapping.class));
            assertNull(method.getAnnotation(DeleteMapping.class));
            assertNull(method.getAnnotation(Log.class));
            assertNull(method.getAnnotation(Anonymous.class));
        }
    }

    private static void assertGet(
            Class<?> controller, String methodName, String path, String authorization) {
        Method method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertNotNull(mapping);
        assertEquals(Set.of(path), Set.of(mapping.value()));
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(authorization, preAuthorize.value());
    }

    private static Set<String> declaredGetPaths(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(mapping -> mapping != null)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
