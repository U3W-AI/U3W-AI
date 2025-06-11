package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

/**
 * @author 张云龙
 */

@Data
public class TagParam {


    //应用id
    private String suiteId;


    //授权企业的CorpID
    private String corpId;

    /**
     *  create  企业客户标签创建事件
     *  update   企业客户标签变更事件
     *  delete   企业客户标签删除事件
     *  shuffle  企业客户标签删除事件
     *
     */
    private String changeType;

    /**
     * 标签或标签组的ID
     */
    private String tagId;

    /**
     * 标签 tag
     * 标签组 tag_group
     */
    private String tagType;


}
