package com.playwright.entity.mcp;

import lombok.Data;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/29 15:49
 */
@Data
public class Content {
    /*
 title	string	图文消息的标题
 author	string	作者
 digest	string	图文消息的摘要，仅有单图文消息才有摘要，多图文此处为空
 content	string	图文消息的具体内容，支持HTML标签，必须少于2万字符，小于1M，且此处会去除JS
 content_source_url	string	图文消息的原文地址，即点击“阅读原文”后的URL
 thumb_media_id	string	图文消息的封面图片素材id（必须是永久mediaID）
 show_cover_pic	number	是否显示封面，0为false，即不显示，1为true，即显示
 url	string	图文页的URL，或者，当获取的列表是图片素材列表时，该字段是图片的URL
 thumb_url	string	图文消息的封面图片素材id（必须是永久mediaID）
  */
    private String title;
    private String author;
    private String digest;
    private String content;
    private String content_source_url;
    private String thumb_media_id;
    private int show_cover_pic;
    private String url;
    private String thumb_url;

}
