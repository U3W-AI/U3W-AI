package com.wx.fbsir.business.smartbot.mapper;

import com.wx.fbsir.business.smartbot.domain.OrchestrationReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrchestrationReceiptMapper {
    int insertReceipt(OrchestrationReceipt receipt);
    List<OrchestrationReceipt> selectByRunId(@Param("runId") String runId);
}
