package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.param.LabelParam;
import com.cube.wechat.thirdapp.entiy.TagParam;

import java.util.List;
import java.util.Map;

/**
 *
 * 标签服务层
 *
 * @author 张云龙
 */
public interface ILabelService {

    R<List<Map>> saveQywxLabelData(List<Map> mapList, String corpId);

    void synCorpLabelData(String corpId);

    R saveLabelAndGroup( LabelParam labelParam);


    R deleteLabelAndGroup(LabelParam labelParam);

    R isTop(LabelParam labelParam);

    R getGroupAndLabel(String corpId);

    R updateLabelName( LabelParam labelParam);

    R getLabelByName(String labelName);

    R getLabelInfoByGroupId(String groupId);


    /**
     * 获取企业标签库
     * @return
     */
    R getQywxLabelData(String corpId);


    /**
     * 初始化AI反诈自动画像标签
     * @param corpId
     * @return
     */
    R initializeAntifraudLabel(String corpId);


    /**
     *  企微回调  创建标签
     * @param tagParam
     * @return
     */
    R<Map> createExternalTag(TagParam tagParam);

    /**
     *  企微回调  变更标签
     * @param tagParam
     * @return
     */
    R<Map> updateExternalTag(TagParam tagParam);

    /**
     *  企微回调  删除标签
     * @param tagParam
     * @return
     */
    R<Map> deleteExternalTag(TagParam tagParam);
}
