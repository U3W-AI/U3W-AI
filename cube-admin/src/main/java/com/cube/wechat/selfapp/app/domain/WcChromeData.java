package com.cube.wechat.selfapp.app.domain;

import lombok.Data;
import com.cube.common.annotation.Excel;
import com.cube.common.core.domain.BaseEntity;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年11月22日 10:03
 */

@Data
public class WcChromeData extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主建 */
    private String id;

    private Long userId;

    /** 提示词 */
    @Excel(name = "提示词")
    private String prompt;

    private String userPrompt;

    /** 答案 */
    @Excel(name = "答案")
    private String answer;

    /** ai名称 */
    @Excel(name = "ai名称")
    private String name;

    private String summary;

    private String keyWord;

    private String username;

    private Integer selVal;

    private String nickName;

    private String isFetch;

    private Integer limit;

    private Integer page;
}
