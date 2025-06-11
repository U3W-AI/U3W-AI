package com.cube.wechat.thirdapp.entiy.vo;

import com.cube.wechat.thirdapp.entiy.RmLabel;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author 张云龙
 */

@Data
public class LabelInfoVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 标签组主键
     */
    private String id;

    /**
     * 标签组名称
     */
    private String groupName;

    /**
     * 1：创建2：微信导入
     */
    private Integer groupType;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 是否删除【0：正常    1：删除】
     */
    private Integer isDelete;


    /**
     * 是否置顶1：置顶0:不置顶
     */
    private Integer isTop;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 标签集合
     */
    private List<RmLabel> labelList;


}
