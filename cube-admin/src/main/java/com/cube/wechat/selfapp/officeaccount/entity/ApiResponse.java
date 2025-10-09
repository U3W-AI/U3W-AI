package com.cube.wechat.selfapp.officeaccount.entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一API响应结果类
 * @author AspireLife
 * @date 2024年12月06日
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {
    
    /**
     * 业务状态码
     */
    private Integer code;
    
    /**
     * 状态描述信息
     */
    private String message;
    
    /**
     * 用户的UnionID（仅在成功时返回）
     */
    private String union_id;
    
    public ApiResponse() {}
    
    public ApiResponse(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public ApiResponse(Integer code, String message, String unionId) {
        this.code = code;
        this.message = message;
        this.union_id = unionId;
    }
    
    /**
     * 成功响应
     */
    public static ApiResponse success(String unionId) {
        return new ApiResponse(200, "查询成功", unionId);
    }
    
    /**
     * 未找到消息
     */
    public static ApiResponse notFound() {
        return new ApiResponse(10010, "您好，当前系统繁忙请稍后重试");
    }
    
    /**
     * 请求繁忙（重复消息）
     */
    public static ApiResponse busy() {
        return new ApiResponse(10100, "当前请求人数过多，请稍后重试");
    }
    
    /**
     * 系统异常
     */
    public static ApiResponse error() {
        return new ApiResponse(10020, "系统异常，请稍后重试");
    }
    
    // Getters and Setters
    public Integer getCode() {
        return code;
    }
    
    public void setCode(Integer code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getUnion_id() {
        return union_id;
    }
    
    public void setUnion_id(String union_id) {
        this.union_id = union_id;
    }
    
    @Override
    public String toString() {
        return "ApiResponse{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", union_id='" + union_id + '\'' +
                '}';
    }
} 