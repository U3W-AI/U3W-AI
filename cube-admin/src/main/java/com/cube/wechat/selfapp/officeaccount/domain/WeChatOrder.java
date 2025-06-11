package com.cube.wechat.selfapp.officeaccount.domain;

import lombok.Data;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年12月10日 14:27
 */
@Data
public class WeChatOrder {

    private String ToUserName;
    private String Encrypt;
}
