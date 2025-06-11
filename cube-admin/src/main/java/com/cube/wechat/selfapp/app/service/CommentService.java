package com.cube.wechat.selfapp.app.service;

import com.cube.wechat.selfapp.app.domain.Comment;
import com.cube.wechat.selfapp.wecom.util.ResultBody;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月12日 16:23
 */
public interface CommentService {

    /**
    * 查询所有评论
    * */
    ResultBody getComment(Comment comment);

    /**
    * 审核评论
    * */
    ResultBody updateComment(Comment comment);


}
