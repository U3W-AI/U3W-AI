package com.wx.fbsir.business.systemprompt.mapper;

import com.wx.fbsir.business.systemprompt.domain.SystemPrompt;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SystemPromptMapper {
    /**
     * 查询系统提示词
     *
     * @param id 系统提示词 ID
     * @return 系统提示词
     */
    @Select("select * from system_prompts where id = #{id}")
    SystemPrompt selectSystemPromptById(Long id);

    /**
     * 更新系统提示词
     *
     * @param systemPrompt 系统提示词
     * @return 更新结果
     */

    boolean updateSystemPrompt(SystemPrompt systemPrompt);

    /**
     * 插入系统提示词
     *
     * @param systemPrompt 系统提示词
     * @return 插入结果
     */
    @Insert("insert into system_prompts (name, content, description, version, status, category, tags) " +
            "values (#{name}, #{content}, #{description}, #{version}, #{status}, #{category}, #{tags})")
    boolean insertSystemPrompt(SystemPrompt systemPrompt);

    /**
     * 删除系统提示词
     *
     * @param id 系统提示词 ID
     * @return 删除结果
     */
    @Delete("delete from system_prompts where id = #{id}")
    boolean deleteSystemPrompt(Long id);
}
