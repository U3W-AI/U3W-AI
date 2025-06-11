package com.cube.wechat.thirdapp.entiy.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 张云龙
 */
@Data
public class AntiFraudVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String createTime;

    private String phone;
    private String evilType;
    private int level;
    private int dkLevel;
    private int sdLevel;
    private int gjfLevel;
    private int investLevel;
    private int fakeOtherLevel;
    private int shoppingLevel;
    private int otherLevel;
}
