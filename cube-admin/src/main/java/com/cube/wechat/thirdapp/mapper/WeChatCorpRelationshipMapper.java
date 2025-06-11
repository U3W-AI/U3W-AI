package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.WeChatCorpRelationship;
import com.cube.wechat.thirdapp.entiy.WeChatHierarchicalReturnResult;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface WeChatCorpRelationshipMapper {
    int deleteByPrimaryKey(String id);


    int insertSelective(WeChatCorpRelationship record);

    WeChatCorpRelationship selectByPrimaryKey(String id);

    void updateCorpRelatioshipStatusByCorpParentId(WeChatCorpRelationship relationship);

    List<WeChatHierarchicalReturnResult> selectCorpRelationshipByParentCorpId(Map map);
    List<WeChatHierarchicalReturnResult> selectCorpDownstreamCorpByParentId(Map map);
    void updateCorpRelatioshipStatus(WeChatCorpRelationship relationship);

    void updateCorpRelatioshipStatusByCorpId(WeChatCorpRelationship relationship);

    List<WeChatCorpRelationship> selectCorpByCorpId(WeChatCorpRelationship relationship);

    int updateByPrimaryKeySelective(WeChatCorpRelationship record);

    List<WeChatHierarchicalReturnResult> selectNewCorpRelationshipByParentCorpId(Map map);
}
