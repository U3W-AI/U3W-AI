package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 群聊
 * rm_external_group_chat
 */
@Data
public class RmExternalGroupChat implements Serializable {
    /**
     * 群聊ID 主键
     */
    private String id;

    /**
     * 企业微信群聊ID
     */
    private String chatId;

    /**
     * 0 - 跟进人正常
     1 - 跟进人离职
     2 - 离职继承中
     3 - 离职继承完成
     */
    private Integer chatStatus;

    /**
     * 群名称
     */
    private String chatName;

    /**
     * 群主ID
     */
    private String chatGroupLeaderId;

    /**
     * 群聊创建时间
     */
    private Date chatCreateTime;

    /**
     * 群聊解散时间
     */
    private Date chatDismissTime;

    /**
     * 群公告
     */
    private String chatNotice;

    /**
     * 群聊更新时间
     */
    private Date chatUpdateTime;

    /**
     * 企业ID
     */
    private String corpId;

    /**
     * 群成员版本号
     */
    private String chatMemberVersion;

    /**
     * 群状态1：正常2：解散
     */
    private Integer chatGroupStatus;
    /**
     * 用户群数
     */
    private Integer userGroupChatTotal;
    /**
     *累计退出
     */
    private Integer totalQuit;
    /**
     *本周新增
     */
    private Integer newAddWeek;
    /**
     *成员总数
     */
    private Integer memberTotal;

    private static final long serialVersionUID = 1L;
}
