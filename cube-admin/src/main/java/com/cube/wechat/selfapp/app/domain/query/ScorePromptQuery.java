package com.cube.wechat.selfapp.app.domain.query;

import lombok.Data;

/**
 * ClassName: ScorePromptQuery
 * Package: com.cube.wechat.selfapp.app.domain.query
 * Description:
 *
 * @Author pupil
 * @Create 2025/9/20 13:05
 * @Version 1.0
 */
@Data
public class ScorePromptQuery {
    private Integer pageNum;

    private Integer pageSize;

    private String name;

    private String prompt;
}
