package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.WeChatHierarchicalReturnResult;

import java.util.List;
import java.util.Map;

/**
 @author sjl
  * @Created date 2024/4/1 14:19
 */
public interface WeChatCorpRelationshipService {

    public R saveQywxCorpRelationship(Map map);

    public R deleteQywxCorpRelationShip(Map map);

    public R<List<WeChatHierarchicalReturnResult>> selectQywxCorpRelationship(Map map);

    R<List<WeChatHierarchicalReturnResult>> selectNewQywxCorpRelationship(Map map);
}
