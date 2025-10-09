package com.cube.system.service.impl;

import com.cube.system.domain.LogInfo;
import com.cube.system.mapper.LogInfoMapper;
import com.cube.system.service.ILogInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 日志信息（记录方法执行日志）Service业务层处理
 *
 * @author cube
 * @date 2025-08-08
 */
@Service
public class LogInfoServiceImpl implements ILogInfoService
{
    @Autowired
    private LogInfoMapper logInfoMapper;

    /**
     * 查询日志信息（记录方法执行日志）
     *
     * @param id 日志信息（记录方法执行日志）主键
     * @return 日志信息（记录方法执行日志）
     */
    @Override
    public LogInfo selectLogInfoById(Long id)
    {
        return logInfoMapper.selectLogInfoById(id);
    }

    /**
     * 查询日志信息（记录方法执行日志）列表
     *
     * @param logInfo 日志信息（记录方法执行日志）
     * @return 日志信息（记录方法执行日志）
     */
    @Override
    public List<LogInfo> selectLogInfoList(LogInfo logInfo)
    {
        return logInfoMapper.selectLogInfoList(logInfo);
    }

    /**
     * 新增日志信息（记录方法执行日志）
     *
     * @param logInfo 日志信息（记录方法执行日志）
     * @return 结果
     */
    @Override
    public int insertLogInfo(LogInfo logInfo)
    {
        return logInfoMapper.insertLogInfo(logInfo);
    }

    /**
     * 修改日志信息（记录方法执行日志）
     *
     * @param logInfo 日志信息（记录方法执行日志）
     * @return 结果
     */
    @Override
    public int updateLogInfo(LogInfo logInfo)
    {
        return logInfoMapper.updateLogInfo(logInfo);
    }

    /**
     * 批量删除日志信息（记录方法执行日志）
     *
     * @param ids 需要删除的日志信息（记录方法执行日志）主键
     * @return 结果
     */
    @Override
    public int deleteLogInfoByIds(Long[] ids)
    {
        return logInfoMapper.deleteLogInfoByIds(ids);
    }

    /**
     * 删除日志信息（记录方法执行日志）信息
     *
     * @param id 日志信息（记录方法执行日志）主键
     * @return 结果
     */
    @Override
    public int deleteLogInfoById(Long id)
    {
        return logInfoMapper.deleteLogInfoById(id);
    }

    @Override
    public int cleanLogInfo() {
        return logInfoMapper.cleanLogInfo();
    }
}
