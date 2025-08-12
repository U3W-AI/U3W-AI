package com.cube.system.mapper;

import com.cube.system.domain.LogInfo;

import java.util.List;

/**
 * 日志信息（记录方法执行日志）Mapper接口
 * 
 * @author cube
 * @date 2025-08-08
 */
public interface LogInfoMapper 
{
    /**
     * 查询日志信息（记录方法执行日志）
     * 
     * @param id 日志信息（记录方法执行日志）主键
     * @return 日志信息（记录方法执行日志）
     */
    public LogInfo selectLogInfoById(Long id);

    /**
     * 查询日志信息（记录方法执行日志）列表
     * 
     * @param logInfo 日志信息（记录方法执行日志）
     * @return 日志信息（记录方法执行日志）集合
     */
    public List<LogInfo> selectLogInfoList(LogInfo logInfo);

    /**
     * 新增日志信息（记录方法执行日志）
     * 
     * @param logInfo 日志信息（记录方法执行日志）
     * @return 结果
     */
    public int insertLogInfo(LogInfo logInfo);

    /**
     * 修改日志信息（记录方法执行日志）
     * 
     * @param logInfo 日志信息（记录方法执行日志）
     * @return 结果
     */
    public int updateLogInfo(LogInfo logInfo);

    /**
     * 删除日志信息（记录方法执行日志）
     * 
     * @param id 日志信息（记录方法执行日志）主键
     * @return 结果
     */
    public int deleteLogInfoById(Long id);

    /**
     * 批量删除日志信息（记录方法执行日志）
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteLogInfoByIds(Long[] ids);

    public int cleanLogInfo();

}
