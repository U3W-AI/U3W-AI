package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * qywx_third_company
 * @author
 */
@Data
public class WeChatThirdCompany implements Serializable {
    /**
     * 主键id
     */
    private String id;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 企业永久授权码
     */
    private String permanentCode;

    /**
     * 企业名称
     */
    private String corpName;

    /**
     * 企业全称
     */
    private String corpFullName;

    /**
     * 企业类型
     */
    private String subjectType;

    /**
     * 企业认证到期时间
     */
    private String verifiedEndTime;

    /**
     * 授权应用id
     */
    private String agentId;
    /**
     * 唯一应用id
     */
    private String suiteId;

    /**
     * 账户状态，-1为删除，禁用为0 启用为1
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date addtime;

    /**
     * 修改时间
     */
    private Date modtime;

    /**
     * 变动时间
     */
    private Date rectime;

    private static final long serialVersionUID = 1L;
}
