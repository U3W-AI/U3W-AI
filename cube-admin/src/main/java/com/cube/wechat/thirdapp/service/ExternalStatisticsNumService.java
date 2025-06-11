package com.cube.wechat.thirdapp.service;


import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.RmExternalStatisticsNum;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ExternalStatisticsNumService {

    R saveExternalStatistics(List<RmExternalStatisticsNum> record, String type);


    R<List<Map<String, Object>>> selectUserIdsByExternalId(Set<String> externalUserIds, String corpId);

    R selectAddExternalByCorpId(String corpId);
}
