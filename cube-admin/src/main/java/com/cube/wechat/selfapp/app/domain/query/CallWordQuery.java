package com.cube.wechat.selfapp.app.domain.query;

import lombok.Data;

/**
 * ClassName: CallWordQuery
 * Package: com.cube.wechat.selfapp.app.domain.query
 * Description:
 *
 * @Author pupil
 * @Create 2025/9/20 9:42
 * @Version 1.0
 */
@Data
public class CallWordQuery {
    private Integer pageNum;

    private Integer pageSize;

    private String wordContent;
}
