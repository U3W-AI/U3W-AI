package com.cube.wechat.thirdapp.service.impl;

import com.cube.common.core.domain.R;
import com.cube.common.utils.StringUtils;
import com.cube.wechat.thirdapp.entiy.WeChatCorpRelationship;
import com.cube.wechat.thirdapp.entiy.WeChatHierarchicalReturnResult;
import com.cube.wechat.thirdapp.mapper.WeChatCorpRelationshipMapper;
import com.cube.wechat.thirdapp.service.WeChatCorpRelationshipService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 @author sjl
  * @Created date 2024/4/1 14:24
 */
@Service
@Transactional
public class WeChatCorpRelationshipServiceImpl implements WeChatCorpRelationshipService {
    @Autowired
    private WeChatCorpRelationshipMapper weChatCorpRelationshipMapper;

    @Override
    public R saveQywxCorpRelationship(Map map) {
        if (map != null) {
            //清除原数据
            String corpId = MapUtils.getString(map, "corpid");
            String suiteId = MapUtils.getString(map, "suiteId");
            String agentId = MapUtils.getString(map, "agentId");
            String parentId = MapUtils.getString(map, "parentId");
            WeChatCorpRelationship weChatCorpRelationship = new WeChatCorpRelationship();
            weChatCorpRelationship.setCorpId(corpId);
            weChatCorpRelationship.setCorpParentId(parentId);
            weChatCorpRelationship.setAgentId(agentId);
            weChatCorpRelationship.setSuiteId(suiteId);
            //查询是否已存在
            List<WeChatCorpRelationship> corpRelationshipList = weChatCorpRelationshipMapper.selectCorpByCorpId(weChatCorpRelationship);
            if (CollectionUtils.isNotEmpty(corpRelationshipList)) {
                WeChatCorpRelationship corpRelationship = corpRelationshipList.get(0);
                corpRelationship.setStatus(1);
                weChatCorpRelationshipMapper.updateCorpRelatioshipStatus(corpRelationship);
            } else {
                weChatCorpRelationship.setCreateTime(new Date());
                weChatCorpRelationship.setId(UUID.randomUUID().toString());
                weChatCorpRelationship.setStatus(1);
                weChatCorpRelationshipMapper.insertSelective(weChatCorpRelationship);
            }

        }
        return R.ok(null);
    }

    @Override
    public R deleteQywxCorpRelationShip(Map map) {
        if (map != null) {
            String corpId = MapUtils.getString(map, "corpId");
            String suiteId = MapUtils.getString(map, "suiteId");
            WeChatCorpRelationship weChatCorpRelationship = new WeChatCorpRelationship();
            weChatCorpRelationship.setCorpParentId(corpId);
            weChatCorpRelationship.setSuiteId(suiteId);
            weChatCorpRelationship.setStatus(0);
            weChatCorpRelationshipMapper.updateCorpRelatioshipStatusByCorpParentId(weChatCorpRelationship);
        }
        return R.ok();
    }

    @Override
    public R<List<WeChatHierarchicalReturnResult>> selectQywxCorpRelationship(Map map) {
        //是否包含上游企业本身
        String isHaveOneself = MapUtils.getString(map, "isHaveOneself");
        if (StringUtils.isNotEmpty(isHaveOneself) && isHaveOneself.equals("1")) {
            List<WeChatHierarchicalReturnResult> qywxHierarchicalReturnResults = weChatCorpRelationshipMapper.selectCorpDownstreamCorpByParentId(map);
            return R.ok(qywxHierarchicalReturnResults);
        } else {
            List<WeChatHierarchicalReturnResult> qywxHierarchicalReturnResults = weChatCorpRelationshipMapper.selectCorpRelationshipByParentCorpId(map);
            return R.ok(qywxHierarchicalReturnResults);
        }

    }

    @Override
    public R<List<WeChatHierarchicalReturnResult>> selectNewQywxCorpRelationship(Map map) {
        //是否包含上游企业本身
        List<WeChatHierarchicalReturnResult> qywxHierarchicalReturnResults = weChatCorpRelationshipMapper.selectNewCorpRelationshipByParentCorpId(map);
        return R.ok(qywxHierarchicalReturnResults);
    }
}
