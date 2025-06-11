package com.cube.wechat.thirdapp.param;

import lombok.Data;

import java.util.List;

/**
 * @author 张云龙
 */

@Data
public class GetCompanyParam {

    /**
     * 查询类型
     *  1是不传查询参数  查全部
     *  2是根据企业名称模糊搜索
     *  3是根据企业标签去查
     */
    private Integer type;


    private String name;

    private List<String> labelIds;

    private Integer pageSize;
    private Integer pageIndex;



}
