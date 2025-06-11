package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * qywx_third_user
 * @author
 */
@Data
public class WeChatThirdUser implements Serializable {
    /**
     * 主键
     */
    private String id;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 用户id
     */
    private String userId;
    /**
     * 唯一应用id
     */
    private String suiteId;


    /**
     * 部门名称
     */
    private String name;

    /**
     * 父部门id
     */
    private Integer parentid;

    /**
     * 职位
     */
    private String position;

    /**
     * 性别
     */
    private String gender;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 是否是部门负责人
     */
    private String isLeaderInDept;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 头像缩略图
     */
    private String thumbAvatar;

    /**
     * 电话
     */
    private String telephone;

    /**
     * 别名
     */
    private String alias;

    /**
     * 地址
     */
    private String address;

    /**
     * open_userid
     */
    private String openUserid;

    /**
     * 主部门id
     */
    private Integer mainDepartment;

    /**
     * 二维码
     */
    private String qrCode;

    /**
     * 状态，-1为删除，禁用为0 启用为1
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
