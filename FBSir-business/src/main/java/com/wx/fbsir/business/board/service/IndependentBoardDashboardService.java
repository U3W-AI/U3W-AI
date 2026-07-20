package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardUsageOperation;
import com.wx.fbsir.business.board.dto.BoardDashboardView;
import com.wx.fbsir.business.board.dto.BoardEnterpriseContextView;
import com.wx.fbsir.business.board.dto.BoardEntitlementSnapshot;
import com.wx.fbsir.business.board.dto.BoardMeetingLookupView;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.dto.BoardRecentMeetingView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Authenticated-user scoped current reads for the me.u3w.com dashboard. */
@Service
public class IndependentBoardDashboardService {
    public static final String CONNECTOR_ACTIVE = "ACTIVE";
    public static final String CONNECTOR_PENDING = "PENDING_CONNECTION";
    public static final String CONNECTOR_NOT_CONNECTED = "NOT_CONNECTED";
    public static final String COMING_SOON = "COMING_SOON";
    public static final int MAX_CONTEXTS = 100;

    private final IndependentBoardMapper mapper;
    private final IndependentBoardEntitlementService entitlementService;

    public IndependentBoardDashboardService(
            IndependentBoardMapper mapper,
            IndependentBoardEntitlementService entitlementService) {
        this.mapper = mapper;
        this.entitlementService = entitlementService;
    }

    @Transactional(readOnly = true)
    public List<BoardEnterpriseContextView> listContexts(Long authenticatedUserId) {
        requirePositive(authenticatedUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        List<BoardEnterpriseMemberScope> rows = mapper.selectActiveContextsByUser(authenticatedUserId);
        if (rows == null) {
            throw new ServiceException("BOARD_CONTEXT_CURRENT_READ_FAILED", 500);
        }
        if (rows.size() > MAX_CONTEXTS) {
            throw new ServiceException("BOARD_CONTEXT_LIMIT_EXCEEDED", 500);
        }
        return rows.stream()
                .map(row -> contextView(requireContext(row, null, authenticatedUserId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public BoardDashboardView getDashboard(Long tenantId, Long authenticatedUserId) {
        requirePositive(tenantId, "TENANT_REQUIRED");
        requirePositive(authenticatedUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        BoardEnterpriseMemberScope context = requireContext(
                mapper.selectActiveContext(tenantId, authenticatedUserId),
                tenantId, authenticatedUserId);
        BoardEntitlementSnapshot entitlement = entitlementService.getSnapshot(
                tenantId, authenticatedUserId);
        List<BoardUsageOperation> operationRows = mapper.selectRecentOperationsByTenantAndUser(
                tenantId, authenticatedUserId,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC);
        if (operationRows == null || operationRows.size() > 10) {
            throw new ServiceException("BOARD_RECENT_MEETING_CURRENT_READ_FAILED", 500);
        }
        List<BoardRecentMeetingView> recentMeetings = operationRows.stream()
                .map(row -> recentMeeting(row, tenantId, authenticatedUserId))
                .toList();
        return new BoardDashboardView(
                contextView(context),
                entitlement,
                recentMeetings,
                connectorState(entitlement),
                COMING_SOON,
                COMING_SOON);
    }

    @Transactional(readOnly = true)
    public BoardMeetingLookupView getMeetingReservation(
            Long tenantId,
            String operationId,
            Long authenticatedUserId) {
        requirePositive(tenantId, "TENANT_REQUIRED");
        requirePositive(authenticatedUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        requireOperationId(operationId);
        requireContext(mapper.selectActiveContext(tenantId, authenticatedUserId),
                tenantId, authenticatedUserId);
        BoardUsageOperation operation = mapper.selectOperation(tenantId, operationId);
        if (operation == null
                || !Objects.equals(operation.getTenantId(), tenantId)
                || !Objects.equals(operation.getOperationId(), operationId)
                || !Objects.equals(operation.getUserId(), authenticatedUserId)
                || !Objects.equals(operation.getProductCode(),
                        IndependentBoardEntitlementService.PRODUCT_CODE)
                || !Objects.equals(operation.getMetricCode(),
                        IndependentBoardEntitlementService.MEETING_METRIC)) {
            return new BoardMeetingLookupView(false, null);
        }
        return new BoardMeetingLookupView(
                true, recentMeeting(operation, tenantId, authenticatedUserId));
    }

    private BoardEnterpriseMemberScope requireContext(
            BoardEnterpriseMemberScope context,
            Long expectedTenantId,
            Long expectedUserId) {
        boolean valid = context != null
                && context.getTenantId() != null && context.getTenantId() > 0L
                && context.getMemberId() != null && context.getMemberId() > 0L
                && Objects.equals(context.getUserId(), expectedUserId)
                && (expectedTenantId == null || Objects.equals(context.getTenantId(), expectedTenantId))
                && Objects.equals(context.getStatus(), 1)
                && Objects.equals(context.getDelFlag(), "0")
                && StringUtils.hasText(context.getTenantName())
                && StringUtils.hasText(context.getMemberRole());
        if (!valid) {
            throw new ServiceException("TENANT_MEMBER_USER_SCOPE_INVALID", 403);
        }
        return context;
    }

    private BoardEnterpriseContextView contextView(BoardEnterpriseMemberScope context) {
        return new BoardEnterpriseContextView(
                context.getTenantId(), context.getMemberId(),
                context.getTenantName(), context.getMemberRole());
    }

    private BoardRecentMeetingView recentMeeting(
            BoardUsageOperation operation,
            Long expectedTenantId,
            Long expectedUserId) {
        if (operation == null
                || !Objects.equals(operation.getTenantId(), expectedTenantId)
                || !Objects.equals(operation.getUserId(), expectedUserId)
                || !Objects.equals(operation.getProductCode(),
                        IndependentBoardEntitlementService.PRODUCT_CODE)
                || !Objects.equals(operation.getMetricCode(),
                        IndependentBoardEntitlementService.MEETING_METRIC)) {
            throw new ServiceException("BOARD_RECENT_MEETING_SCOPE_INVALID", 500);
        }
        return new BoardRecentMeetingView(
                operation.getOperationId(), operation.getStatus(), operation.getEffectivePlanCode(),
                operation.getAgendaCount(), operation.getSeatCount(), operation.getRemainingCount(),
                operation.getBucketDate(), operation.getCreateTime());
    }

    private String connectorState(BoardEntitlementSnapshot entitlement) {
        if ("PENDING_CONNECTOR".equals(entitlement.activationState())) {
            return CONNECTOR_PENDING;
        }
        if (entitlement.connectorVerified() && "ACTIVE".equals(entitlement.activationState())) {
            return CONNECTOR_ACTIVE;
        }
        return CONNECTOR_NOT_CONNECTED;
    }

    private void requirePositive(Long value, String code) {
        if (value == null || value <= 0L) {
            throw new ServiceException(code, 400);
        }
    }

    private void requireOperationId(String operationId) {
        if (operationId == null
                || !operationId.matches(BoardMeetingReservationRequest.OPERATION_ID_PATTERN)) {
            throw new ServiceException("OPERATION_ID_INVALID", 400);
        }
    }
}
