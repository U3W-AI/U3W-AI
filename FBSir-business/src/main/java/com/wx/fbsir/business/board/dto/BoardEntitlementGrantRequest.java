package com.wx.fbsir.business.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Date;

public record BoardEntitlementGrantRequest(
        @NotNull @Min(1) Long tenantId,
        @NotNull @Min(1) Long memberId,
        @NotNull @Min(1) Long userId,
        @NotBlank @Pattern(regexp = "BOARD_FREE|BOARD_VIP") String planCode,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Shanghai") Date validUntil,
        @NotNull @Min(0) Long expectedVersion) {
}
