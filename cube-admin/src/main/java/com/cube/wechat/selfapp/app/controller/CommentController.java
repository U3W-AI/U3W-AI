package com.cube.wechat.selfapp.app.controller;

import com.cube.common.annotation.Log;
import com.cube.common.core.controller.BaseController;
import com.cube.wechat.selfapp.app.domain.Comment;
import com.cube.wechat.selfapp.app.domain.TextFilterParam;
import com.cube.wechat.selfapp.app.service.CommentService;
import com.cube.wechat.selfapp.wecom.util.ResultBody;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月12日 16:23
 */
@RestController
@Api(value = "/comment", description = "后台-企微管理-评论管理")
@RequestMapping("/comment")
public class CommentController  extends BaseController {


    @Autowired
    private CommentService commentService;

    @GetMapping("/getComment")
    @Log(title = "企微管理-查询所有评论")
    public ResultBody getComment(Comment comment){
        return commentService.getComment(comment);
    }

    @PostMapping("/updateComment")
    @Log(title = "企微管理-审核评论")
    public ResultBody updateComment(@RequestBody Comment comment){
        return commentService.updateComment(comment);
    }



    @PostMapping("/textFilter")
    public ResultBody textFilter(@RequestBody TextFilterParam textFilterParam){
        String cleanedText = removeCiteTags(textFilterParam.getInputContent());
        return ResultBody.success(cleanedText);
    }

    public static void main(String[] args) {
        String input = "";
        String cleanedText = removeCiteTags(input);
        System.out.println(cleanedText); // 输出: ""
    }

    public static String removeCiteTags(String input) {
        // 定义正则表达式，匹配 <cite> 标签及其内容、*、#、- 和多余的空格
//        String regex = "(<cite>\\d+</cite>|[*#-]|\\s{2,})";
        String regex = "(<cite>\\d+</cite>|[*#-])";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        // 使用正则表达式替换所有匹配的 <cite> 标签及其内容、*、#、- 和多余的空格为空字符串
        String cleanedText = matcher.replaceAll("");
        return cleanedText;
    }

}
