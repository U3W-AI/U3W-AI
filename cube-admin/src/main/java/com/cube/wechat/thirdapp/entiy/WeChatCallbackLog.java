package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author 张云龙
 *
 * system库
 * qywx_callback_log
 */
@Data
public class WeChatCallbackLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private String id;

    /**
     * 类型
     */
    private String type;

    /**
     * 类型描述
     */
    private String typeDescribe;

    /**
     * 入参
     */
    private String param;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 应用id
     */
    private String suiteId;

    /**
     * 1：指令回调2：数据回调
     */
    private Integer interfaceType;

}
