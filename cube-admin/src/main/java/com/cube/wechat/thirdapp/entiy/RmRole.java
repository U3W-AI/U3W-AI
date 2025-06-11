package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * rm_role
 * @author
 */
@Data
public class RmRole implements Serializable {
    /**
     * 主键ID
     */
    private String id;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 是否系统级1：是0：否（系统级角色不可删除）
     */
    private Integer isSystem;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 创建时间
     */
    private Date createDate;
    /**
     * 当前页
     */
    private Integer pageIndex;
    /**
     * 每页显示数量
     */
    private Integer pageSize;
    private Integer isDel;
    /**
     * 负责人
     */
    private List<WeChatCorpUser> userList;

    private static final long serialVersionUID = 1L;
}
