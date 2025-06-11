package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 群成员
 * rm_external_group_chat_member
 */
@Data
public class RmExternalGroupChatMember implements Serializable {
    /**
     * 主键ID
     */
    private String id;

    /**
     * 微信群聊ID
     */
    private String chatId;

    /**
     * 系统群聊ID
     */
    private String systemChatId;

    /**
     * 群聊名称
     */
    private String chatName;

    /**
     * 群主ID
     */
    private String chatGroupLeaderId;

    /**
     * 群成员ID
     */
    private String memberUserId;

    /**
     * 成员类型。
     1 - 企业成员
     2 - 外部联系人
     */
    private Integer memberType;

    /**
     * 外部联系人在微信开放平台的唯一身份标识（微信unionid），通过此字段企业可将外部联系人与公众号/小程序用户关联起来。仅当群成员类型是微信用户（包括企业成员未添加好友），且企业绑定了微信开发者ID有此字段（查看绑定方法）。第三方不可获取，上游企业不可获取下游企业客户的unionid字段
     */
    private String memberUnionid;

    /**
     * 进群时间
     */
    private Date memberJoinTime;

    /**
     * 退群时间
     */
    private Date memberQuitTime;

    /**
     * 入群方式。
     1 - 由群成员邀请入群（直接邀请入群）
     2 - 由群成员邀请入群（通过邀请链接入群）
     3 - 通过扫描群二维码入群
     */
    private Integer memberJoinScene;

    /**
     * 退群方式
     0 - 自己退群
     1 - 群主/群管理员移出
     */
    private Integer memberQuitScene;

    /**
     * 邀请者ID
     */
    private String memberInvitorUserId;

    /**
     * 群成员群昵称
     */
    private String memberGroupNickname;

    /**
     * 如果是微信用户，则返回其在微信中设置的名字
     如果是企业微信联系人，则返回其设置对外展示的别名或实名
     */
    private String memberName;

    /**
     * 当前群成员版本号


     */
    private String memberVersion;

    /**
     * 成员身份1：普通成员2：群管理员 3、群主
     */
    private Integer memberIdentity;

    /**
     * 成员状态1：有效2：退出
     */
    private Integer memberStatus;

    /**
     * 企业 ID
     */
    private String corpId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
