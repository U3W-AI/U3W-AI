package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * rm_external_info_field_config
 * <p>
 * 好友信息配置表
 *
 * @author 张云龙
 */
@Data
public class RmCompanyInfoFieldConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private String id;

    /**
     * 信息名称
     */
    private String infoName;

    /**
     * 企业id
     */
    private String corpId;
    /**
     * 值
     */
    private String infoValue;

    /**
     * 应用id
     */
    private String appId;

    /**
     * 控件类型
     */
    private String controlType;

    /**
     * 控件名称
     */
    private String controlName;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 是否删除1：删除0:未删除
     */
    private Integer isDelete;

    /**
     * 是否启用1：启用0：未启用
     */
    private Integer isStatus;

    /**
     * 是否为默认值1:默认值0：不是默认值
     */
    private Integer isDefault;

    /**
     * 下拉菜单值 存json
     */
    private String selectValue;
    /**
     * 下拉菜单值。前端入参
     */
    private List<String> selectValueList;

    /**
     * 到期提醒是否开启1:开启0：不开启
     */
    private Integer isReminder;

    /**
     * 提醒时间
     */
    private String reminderTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否为置顶1:置顶0：不置顶
     */
    private Integer isTop;
    /**
     * 是否需要记录历史极力
     */
    private Integer isRecorded;

    /**
     * 是否为多填写项1:是0：不是
     */
    private Integer isThereMultiple;

    /**
     * 是否判断重复
     */
    private Integer isRepetition;

}
