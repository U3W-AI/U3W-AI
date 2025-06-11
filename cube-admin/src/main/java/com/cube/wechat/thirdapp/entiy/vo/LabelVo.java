package com.cube.wechat.thirdapp.entiy.vo;

import com.cube.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * @author 张云龙
 */
@Data
public class LabelVo extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     *  标签id
     */
    private Long labelId;

    /**
     * 标签名称
     */
    private String labelName;

    /**
     * 标签组
     */
    private Long labelGroupId;

    /**
     * 是否置顶【0：否    1：是】
     */
    private Integer top;

    /**
     * 标签组来源【1：手动创建   2：外部导入】
     */
    private String groupSource;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 标签组名称
     */
    private String labelGroupName;
}
