package com.wx.fbsir.business.board.portal.mapper;

import com.wx.fbsir.business.board.portal.persistence.BoardPortalConnectorBindingRow;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalOAuthClientRow;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalOAuthFamilyRow;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalTenantRow;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IndependentBoardPortalReadMapper {

    Integer selectCurrentAuthority(
            @Param("userId") Long userId,
            @Param("requiredRole") String requiredRole,
            @Param("permission") String permission);

    List<BoardPortalTenantRow> selectTenants(
            @Param("query") String query,
            @Param("status") Integer status,
            @Param("highWaterId") Long highWaterId,
            @Param("lastId") Long lastId,
            @Param("rowLimit") int rowLimit);

    List<BoardPortalOAuthClientRow> selectOAuthClients(
            @Param("status") String status,
            @Param("now") Date now,
            @Param("highWaterId") Long highWaterId,
            @Param("lastId") Long lastId,
            @Param("rowLimit") int rowLimit);

    List<BoardPortalOAuthFamilyRow> selectOAuthFamilies(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("userId") Long userId,
            @Param("currentFirst") boolean currentFirst,
            @Param("status") String status,
            @Param("now") Date now,
            @Param("highWaterId") Long highWaterId,
            @Param("lastId") Long lastId,
            @Param("rowLimit") int rowLimit);

    List<BoardPortalConnectorBindingRow> selectConnectorBindings(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("now") Date now,
            @Param("highWaterId") Long highWaterId,
            @Param("lastId") Long lastId,
            @Param("rowLimit") int rowLimit);
}
