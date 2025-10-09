package com.cube.wechat.selfapp.corpchat.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * @Author 杨航行
 * @Description //TODO
 * @Date 2024/7/8 17:41
 * @Version 1.0
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultBody<T> implements Serializable {
    /**
     * 错误码
     */
    private long code=200;

    /** 提示信息. */
    private String messages="ok";

    /** 具体的内容. */
    private T data;

    public static final ResultBody SUCCEED = new ResultBody("保存成功");

    public static final ResultBody FAIL = new ResultBody("保存失败");

    public void setCode(long code) {
        this.code = code ;
    }

    public void setMessages(String messages){
        this.messages=messages;
    }

    public T getData(){
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ResultBody(String messages) {
        if (messages.contains("失败")) this.code = 200;
        else this.code = -2001;
        this.messages = messages;
        this.data = null;
    }

    /**
     * 成功返回
     * @param object
     * @return
     */
    public static ResultBody success(Object object) {
        ResultBody result = new ResultBody();
        result.setCode(200);
        result.setMessages("ok");
        result.setData(object);
        return result;
    }

    /**
     * 异常返回
     * @param code
     * @param msg
     * @return
     */
    public static ResultBody error(Integer code, String msg) {
        ResultBody result = new ResultBody();
        result.setCode(code);
        result.setMessages(msg);
        return result;
    }

    /**
     * 异常返回
     * @param code
     * @return
     */
    public static ResultBody errorList(Integer code, List<Map> list) {
        ResultBody result = new ResultBody();
        result.setCode(code);
        result.setMessages("nook");
        result.setData(list);
        return result;
    }

}
