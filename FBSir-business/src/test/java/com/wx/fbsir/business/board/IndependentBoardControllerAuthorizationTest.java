package com.wx.fbsir.business.board;

import com.wx.fbsir.business.board.controller.IndependentBoardAdminController;
import com.wx.fbsir.business.board.controller.IndependentBoardMeController;
import com.wx.fbsir.business.board.dto.BoardEntitlementGrantRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.common.annotation.Anonymous;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.enums.BusinessType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBoardControllerAuthorizationTest {

    @Test
    void meAndAdminApisHaveFixedPathsAndDifferentAuthorizationPolicies() throws Exception {
        assertEquals("/my/independent-board", basePath(IndependentBoardMeController.class));
        assertEquals("/business/independent-board", basePath(IndependentBoardAdminController.class));

        assertEndpoint(IndependentBoardMeController.class, "contexts", "isAuthenticated()",
                GetMapping.class, "/contexts");
        assertEndpoint(IndependentBoardMeController.class, "dashboard", "isAuthenticated()",
                GetMapping.class, "/dashboard", Long.class);
        assertEndpoint(IndependentBoardMeController.class, "meetingReservation", "isAuthenticated()",
                GetMapping.class, "/meeting-reservations/{operationId}", String.class, Long.class);
        assertEndpoint(IndependentBoardMeController.class, "entitlement", "isAuthenticated()",
                GetMapping.class, "/entitlement", Long.class);
        assertEndpoint(IndependentBoardMeController.class, "reserve", "isAuthenticated()",
                PostMapping.class, "/meeting-reservations", BoardMeetingReservationRequest.class);
        assertEndpoint(IndependentBoardAdminController.class, "grant",
                "@ss.hasRole('admin') and @ss.hasPermi('board:entitlement:grant')",
                PostMapping.class, "/entitlements", BoardEntitlementGrantRequest.class);
        assertEndpoint(IndependentBoardAdminController.class, "entitlements",
                "@ss.hasRole('admin') and @ss.hasPermi('board:entitlement:query')",
                GetMapping.class, "/entitlements", Long.class);
        assertEndpoint(IndependentBoardAdminController.class, "operations",
                "@ss.hasRole('admin') and @ss.hasPermi('board:operation:audit')",
                GetMapping.class, "/operations", Long.class);
    }

    @Test
    void reservationPayloadCannotSupplyAUserIdentity() {
        String[] components = Arrays.stream(BoardMeetingReservationRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);
        assertTrue(Arrays.asList(components).contains("tenantId"));
        assertFalse(Arrays.asList(components).contains("userId"));
        assertFalse(Arrays.asList(components).contains("memberId"));
        Max safetyLimit = Arrays.stream(BoardMeetingReservationRequest.class.getRecordComponents())
                .filter(component -> component.getName().equals("seatCount"))
                .findFirst().orElseThrow().getAccessor().getAnnotation(Max.class);
        assertNotNull(safetyLimit);
        assertEquals(BoardMeetingReservationRequest.INITIAL_SAFETY_MAX_SEAT_COUNT, safetyLimit.value());
        Pattern operationIdPattern = Arrays.stream(BoardMeetingReservationRequest.class.getRecordComponents())
                .filter(component -> component.getName().equals("operationId"))
                .findFirst().orElseThrow().getAccessor().getAnnotation(Pattern.class);
        assertNotNull(operationIdPattern);
        assertEquals(BoardMeetingReservationRequest.OPERATION_ID_PATTERN,
                operationIdPattern.regexp());
    }

    @Test
    void entitlementGrantIsRecordedByTheRuoYiOperationLog() throws Exception {
        Method grant = IndependentBoardAdminController.class.getMethod(
                "grant", BoardEntitlementGrantRequest.class);

        Log log = grant.getAnnotation(Log.class);

        assertNotNull(log);
        assertEquals(BusinessType.GRANT, log.businessType());
        assertEquals("Independent Board entitlement", log.title());
    }

    private String basePath(Class<?> controller) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        return mapping.value()[0];
    }

    private void assertEndpoint(
            Class<?> controller, String methodName, String expression,
            Class<?> mappingType, String path, Class<?>... parameterTypes) throws Exception {
        Method method = controller.getMethod(methodName, parameterTypes);
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals(expression, authorization.value());
        assertNull(method.getAnnotation(Anonymous.class));
        if (mappingType == GetMapping.class) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            assertNotNull(mapping);
            assertEquals(path, mapping.value()[0]);
        } else {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            assertNotNull(mapping);
            assertEquals(path, mapping.value()[0]);
        }
    }
}
