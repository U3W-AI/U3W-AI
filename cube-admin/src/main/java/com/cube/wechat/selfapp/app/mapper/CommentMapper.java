package com.cube.wechat.selfapp.app.mapper;

import com.cube.wechat.selfapp.app.domain.Comment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface CommentMapper {


    /*
    * 查询所有评论
    * */
    List<Map> getComment(Comment comment);

    int updateComment(Comment comment);



}
