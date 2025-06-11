package com.cube.wechat.selfapp.app.service.impl;

import com.cube.point.controller.PointsSystem;
import com.cube.wechat.selfapp.app.domain.Comment;
import com.cube.wechat.selfapp.app.mapper.CommentMapper;
import com.cube.wechat.selfapp.app.service.CommentService;
import com.cube.wechat.selfapp.wecom.util.ResultBody;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年10月16日 09:29
 */
@Service
public class CommentServiceImpl implements CommentService {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PointsSystem pointsSystem;

    /**
     * 查询所有评论
     * */
    @Override
    public ResultBody getComment(Comment comment) {
        PageHelper.startPage(comment.getPage(),comment.getLimit());
        List<Map> list = commentMapper.getComment(comment);
        PageInfo pageInfo = new PageInfo(list);
        return ResultBody.success(pageInfo);
    }

    /**
     * 审核评论
     * */
    @Override
    public ResultBody updateComment(Comment comment) {
        commentMapper.updateComment(comment);

        Integer isFirst = pointsSystem.checkPointIsOk("每日首次评论",comment.getUserId(),1);
        if(isFirst==0){
            pointsSystem.setUserPoint(comment.getUserId(),"每日首次评论",null,"0x2edc4228a84d672affe8a594033cb84a029bcafc","f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
        }
        return ResultBody.success("设置成功");
    }

}
