-- auto-generated definition
create table cv_storage
(
    id                    bigint auto_increment comment '主键ID'
        primary key,
    user_id               bigint                             not null comment '用户ID',
    cv_id                 varchar(64)                        not null comment '简历ID（自动生成）',
    cv_name               varchar(255)                       not null comment '简历名称',
    name                  varchar(100)                       null comment '解析后的姓名',
    phone                 varchar(32)                        null comment '解析后的手机号',
    mail                  varchar(100)                       null comment '解析后的邮箱',
    shortlink             varchar(255)                       not null comment '简历访问短链接',
    parse_content         longtext                           null comment '解析内容（来自腾讯元器智能体）',
    process_status        tinyint  default 0                 not null comment '处理状态：0-处理中，1-已完成，2-失败',
    file_url              varchar(512)                       not null comment '简历文件实际存储位置',
    available             tinyint  default 1                 not null comment '是否启用：0-禁用，1-启用',
    create_time           datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time           datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    create_by             varchar(64)                        null comment '创建者',
    update_by             varchar(64)                        null comment '更新者',
    remark                varchar(500)                       null comment '备注',
    access_code_available tinyint  default 0                 not null comment '是否启用访问码：0-禁用，1-启用',
    deadline              datetime                           null comment '截止时间',
    constraint uk_cv_id
        unique (cv_id),
    constraint uk_shortlink
        unique (shortlink)
)
    comment '简历存储与解析结果表';

create index idx_user_id
    on cv_storage (user_id);

-- ---------------------------------------

-- auto-generated definition
create table cv_monitor
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    shortlink   varchar(255)                       not null comment '简历访问短链接（关联 cv_storage.shortlink）',
    date        date                               not null comment '访问日期',
    count       int      default 0                 not null comment '访问量',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_shortlink_date
        unique (shortlink, date) comment '短链接和日期唯一索引'
)
    comment '简历访问监控表';

-- ---------------------------------------

-- auto-generated definition
create table cv_access_log
(
    id          bigint auto_increment comment 'ID'
        primary key,
    shortlink   varchar(255)                       null comment '短链接',
    ip          varchar(64)                        null comment 'IP',
    browser     varchar(64)                        null comment '浏览器',
    os          varchar(64)                        null comment '操作系统',
    device      varchar(64)                        null comment '访问设备',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '简历访问日志表';

create index idx_shortlink_time
    on cv_access_log (shortlink, create_time);

-- ---------------------------------------

-- auto-generated definition
create table cv_access_code
(
    id               bigint auto_increment comment '主键ID'
        primary key,
    shortlink        varchar(255)                       not null comment '简历访问短链接（关联 cv_storage.shortlink）',
    access_code      varchar(32)                        not null comment '简历访问码',
    accessible_count int      default 1                 not null comment '可访问次数',
    deadline         datetime                           null comment '截止时间',
    available        tinyint  default 1                 not null comment '是否启用：0-禁用，1-启用',
    create_time      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    del_flag         tinyint  default 0                 not null comment '删除标识：0-不删除，1-已删除',
    constraint uk_shortlink_access_code
        unique (shortlink, access_code)
)
    comment '简历访问码表';

create index idx_shortlink
    on cv_access_code (shortlink);

