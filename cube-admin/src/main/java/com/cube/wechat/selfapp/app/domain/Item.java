package com.cube.wechat.selfapp.app.domain;

import lombok.Data;

import java.util.Date;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/29 15:46
 */
/*

参数名	类型	说明
media_id	string	消息ID
content	object	图文消息，内容
update_time	number	更新日期
name	string	图片、语音、视频素材的名字
url	string	图片、语音、视频素材URL
 */
@Data
public class Item {
    private String media_id;
    private Content content;
    private Date update_time;
    private String name;
    private String url;
}
