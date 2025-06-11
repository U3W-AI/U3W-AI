package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * rm_external_info
 * @author
 */
@Data
public class RmExternalInfo implements Serializable {
    /**
     * 主键
     */
    private String id;

    /**
     * 	外部联系人的userid
     */
    private String externalUserId;

    /**
     * 	外部联系人的名称
     */
    private String name;
    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 	外部联系人头像
     */
    private String avatar;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 	外部联系人所在企业的简称
     */
    private String corpName;

    /**
     * 	外部联系人所在企业的主体名称
     */
    private String corpFullName;

    /**
     * 	外部联系人的类型，1表示该外部联系人是微信用户，2表示该外部联系人是企业微信用户
     */
    private String type;

    /**
     * 外部联系人性别 0-未知 1-男性 2-女性。第三方不可获取，上游企业不可获取下游企业客户该字段，返回值为0，表示未定义
     */
    private String gender;

    /**
     * 	外部联系人在微信开放平台的唯一身份标识（微信unionid），通过此字段企业可将外部联系人与公众号/小程序用户关联起来
     */
    private String unionId;

    /**
     * 	外部联系人的职位，如果外部企业或用户选择隐藏职位，则不返回，仅当联系人类型是企业微信用户时有此字段
     */
    private String position;

    /**
     * 	该成员对此外部联系人的备注
     */
    private String remark;

    /**
     * 添加了此外部联系人的企业成员userid
     */
    private String userId;

    /**
     * 该成员对此微信客户备注的企业名称（仅微信客户有该字段）
     */
    private String remarkCorpName;

    /**
     * 发起添加的userid，如果成员主动添加，为成员的userid；如果是客户主动添加，则为客户的外部联系人userid；如果是内部成员共享/管理员分配，则为对应的成员/管理员userid
     */
    private String operUserid;

    /**
     * 该成员添加此外部联系人的时间
     */
    private Date createTime;

    /**
     * 来源
     */
    private String addWay;

    /**
     * 该成员对此外部联系人的描述
     */
    private String description;

    /**
     * 状态 1、有效2、把客户删除 3 被客户删除 4、双向删除
     */
    private Integer status;
    /**
     * 数据创建时间
     */
    private Date dataCreateTime;
    /**
     * 数据更新时间
     */
    private Date dataUpdateTime;

    private static final long serialVersionUID = 1L;
}
