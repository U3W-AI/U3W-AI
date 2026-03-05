package com.wx.fbsir.business.systemprompt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;


import java.time.LocalDateTime;


@Getter
@Table(name = "system_prompts", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "version"})
})
public class SystemPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;


    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "version", length = 50)
    private String version = "1.0";

    @Column(name = "status", nullable = false)
    private Boolean status = true; // true=启用, false=禁用

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "tags", length = 500)
    private String tags; // 可存储逗号分隔字符串或JSON字符串，若需复杂查询可改用@Convert

    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    public String getContent() {
        return content;
    }
}