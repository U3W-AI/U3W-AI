package com.wx.fbsir.business.board.mapper;

import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardEnterpriseAuthority;
import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.domain.BoardConnectorBindingReceipt;
import com.wx.fbsir.business.board.domain.BoardEntitlementReceipt;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.domain.BoardUsageBudget;
import com.wx.fbsir.business.board.domain.BoardUsageOperation;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IndependentBoardMapper {
    BoardEnterpriseAuthority selectEnterpriseSlotForUpdate(
            @Param("tenantId") Long tenantId);

    List<BoardEnterpriseMemberScope> selectActiveContextsByUser(@Param("userId") Long userId);

    BoardEnterpriseMemberScope selectActiveContext(@Param("tenantId") Long tenantId,
                                                    @Param("userId") Long userId);

    BoardEnterpriseMemberScope selectActiveContextForUpdate(@Param("tenantId") Long tenantId,
                                                             @Param("userId") Long userId);

    BoardEnterpriseMemberScope selectExactActiveMemberForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("userId") Long userId);

    BoardEnterpriseMemberScope selectMemberSlotForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId);

    BoardProductPlan selectActivePlan(@Param("productCode") String productCode,
                                      @Param("planCode") String planCode);

    BoardProductPlan selectActivePlanForUpdate(@Param("productCode") String productCode,
                                               @Param("planCode") String planCode);

    BoardProductPlan selectPlanSlotForUpdate(@Param("productCode") String productCode,
                                             @Param("planCode") String planCode);

    BoardProductEntitlement selectEntitlement(@Param("tenantId") Long tenantId,
                                              @Param("memberId") Long memberId,
                                              @Param("userId") Long userId,
                                              @Param("productCode") String productCode);

    BoardProductEntitlement selectEntitlementForUpdate(@Param("tenantId") Long tenantId,
                                                       @Param("memberId") Long memberId,
                                                       @Param("productCode") String productCode);

    List<BoardProductEntitlement> selectEntitlementsByTenant(@Param("tenantId") Long tenantId,
                                                            @Param("productCode") String productCode);

    int insertEntitlement(BoardProductEntitlement entitlement);

    int updateEntitlementIfVersion(@Param("entitlement") BoardProductEntitlement entitlement,
                                   @Param("expectedVersion") Long expectedVersion);

    int insertEntitlementReceipt(BoardEntitlementReceipt receipt);

    List<BoardEntitlementReceipt> selectEntitlementReceiptsByTenant(
            @Param("tenantId") Long tenantId);

    BoardConnectorBinding selectConnectorBinding(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("userId") Long userId,
            @Param("productCode") String productCode,
            @Param("sourceCode") String sourceCode,
            @Param("connectorCode") String connectorCode);

    BoardConnectorBinding selectConnectorBindingForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("userId") Long userId,
            @Param("productCode") String productCode,
            @Param("sourceCode") String sourceCode,
            @Param("connectorCode") String connectorCode);

    BoardConnectorBinding selectConnectorBindingSlotForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("productCode") String productCode,
            @Param("sourceCode") String sourceCode,
            @Param("connectorCode") String connectorCode);

    List<BoardConnectorBinding> selectConnectorBindingsByTenant(
            @Param("tenantId") Long tenantId,
            @Param("productCode") String productCode,
            @Param("sourceCode") String sourceCode,
            @Param("connectorCode") String connectorCode);

    List<String> selectConnectorBindingScopes(@Param("bindingId") String bindingId);

    List<String> selectConnectorBindingScopesForUpdate(@Param("bindingId") String bindingId);

    int insertConnectorBinding(BoardConnectorBinding binding);

    int insertConnectorBindingScope(@Param("bindingId") String bindingId,
                                    @Param("scopeCode") String scopeCode,
                                    @Param("createdAt") Date createdAt);

    int revokeConnectorBindingIfVersion(@Param("binding") BoardConnectorBinding binding,
                                        @Param("expectedVersion") Long expectedVersion);

    int reauthorizeConnectorBindingIfVersion(@Param("binding") BoardConnectorBinding binding,
                                             @Param("expectedVersion") Long expectedVersion);

    int insertConnectorBindingReceipt(BoardConnectorBindingReceipt receipt);

    BoardConnectorBindingReceipt selectConnectorBindingReceiptForUpdate(
            @Param("receiptId") String receiptId);

    List<BoardConnectorBindingReceipt> selectConnectorBindingReceiptsForUpdate(
            @Param("bindingId") String bindingId);

    BoardUsageBudget selectUsageBudget(@Param("tenantId") Long tenantId,
                                       @Param("memberId") Long memberId,
                                       @Param("productCode") String productCode,
                                       @Param("metricCode") String metricCode,
                                       @Param("bucketDate") LocalDate bucketDate);

    int prepareUsageBudget(@Param("tenantId") Long tenantId,
                           @Param("memberId") Long memberId,
                           @Param("productCode") String productCode,
                           @Param("metricCode") String metricCode,
                           @Param("bucketDate") LocalDate bucketDate,
                           @Param("dailyLimit") Integer dailyLimit);

    int reserveOneMeeting(@Param("tenantId") Long tenantId,
                          @Param("memberId") Long memberId,
                          @Param("productCode") String productCode,
                          @Param("metricCode") String metricCode,
                          @Param("bucketDate") LocalDate bucketDate,
                          @Param("dailyLimit") Integer dailyLimit);

    BoardUsageOperation selectOperation(@Param("tenantId") Long tenantId,
                                        @Param("operationId") String operationId);

    BoardUsageOperation selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                                 @Param("operationId") String operationId);

    int insertOperation(BoardUsageOperation operation);

    int markOperationReserved(@Param("tenantId") Long tenantId,
                              @Param("operationId") String operationId,
                              @Param("remainingCount") Integer remainingCount);

    List<BoardUsageOperation> selectOperationsByTenant(@Param("tenantId") Long tenantId,
                                                      @Param("productCode") String productCode,
                                                      @Param("metricCode") String metricCode);

    List<BoardUsageOperation> selectRecentOperationsByTenantAndUser(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("productCode") String productCode,
            @Param("metricCode") String metricCode);
}
