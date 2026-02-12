package com.wx.fbsir.business.resume.service.impl;


import com.wx.fbsir.business.resume.domain.CV;
import com.wx.fbsir.business.resume.domain.ResumeAccessCode;
import com.wx.fbsir.business.resume.domain.ResumeAccessLog;
import com.wx.fbsir.business.resume.domain.ResumeMonitor;
import com.wx.fbsir.business.resume.mapper.ResumeMapper;
import com.wx.fbsir.business.resume.service.ResumeShortLinkService;
import com.wx.fbsir.business.resume.utils.AccessCodeGenerator;
import com.wx.fbsir.business.resume.utils.LinkUtil;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Service
public class ResumeShortLinkServiceImpl implements ResumeShortLinkService {
    @Autowired
    ResumeMapper resumeMapper;

    @Override
    public void restoreUrl(String shortlink,String accessCode, HttpServletRequest request, HttpServletResponse response) {
        //查询简历是否启用
        CV cv = resumeMapper.findResumeByShortLink(shortlink);
        if (cv.getAvailable()!=1){
            //简历禁用
            return;
        }
        //查询截止时间是否过期
        if( cv.getDeadline()!=null && cv.getDeadline().before(new Date())){
            //过期
            return;
        }
        //查询简历是否启用访问码
        if (cv.getAccessCodeAvailable()==1){
            //启用访问码
            if(accessCode==null || accessCode==""){
                try{
                    response.setContentType("text/html;charset=UTF-8");
                    response.getWriter().write("""
                                    <!DOCTYPE html>
                                    <html lang="zh-CN">
                                    <head>
                                        <meta charset="UTF-8">
                                        <title>访问码页面</title>
                                    </head>
                                    <body>
                                        <form id="form">
                                            <input type="hidden" value="%s" id="shortlink">
                                            <input id="accessCode" placeholder="请输入访问码" required>
                                            <button type="button" id="submitBtn">提交</button>
                                        </form>
                            
                                        <script>
                                                         document.getElementById('submitBtn').addEventListener('click', function() {
                                                             const shortlink = document.getElementById('shortlink').value;
                                                             const accessCode = document.getElementById('accessCode').value.trim();
                            
                                                             if (!accessCode) {
                                                                 alert("请输入访问码！");
                                                                 return;
                                                             }
                            
                                                             console.log("shortlink:", shortlink);
                                                             console.log("访问码:", accessCode);
                            
                                                             // 构建文件 URL
                                                             const url = window.location.origin + '/' + encodeURIComponent(shortlink) + '?code=' + encodeURIComponent(accessCode);
                            
                                                             // 直接打开 PDF / 文件
                                                             window.open(url, '_blank'); // 新标签页打开
                                                         });
                                        </script>
                                    </body>
                                    </html>
                    """.formatted(shortlink));
                } catch (IOException e){
                    //报错
                    throw new ServiceException("返回简历出错");
                }
                return;
            }
            //查询访问码并减一
            int num = resumeMapper.getAccessibleCountByCode(shortlink,accessCode);
            if (num != 1){
                //返回访问码失效页面
                try {
                    response.setContentType("text/html;charset=UTF-8");
                    response.getWriter().write("""
                            <!DOCTYPE html>
                            <html lang="zh-CN">
                            <head>
                                <meta charset="UTF-8">
                                <title>访问码页面</title>
                            </head>
                            <body>
                                <form id="form">
                                    <input type="hidden" value="%s" id="shortlink">
                    
                                    <input id="accessCode" placeholder="请输入访问码" required>
                    
                                    <!-- 新增：红色提示 -->
                                    <div style="color:red; margin-top:6px;">
                                        访问码已失效
                                    </div>
                    
                                    <button type="button" id="submitBtn">提交</button>
                                </form>
                    
                                <script>
                                    document.getElementById('submitBtn').addEventListener('click', function() {
                                        const shortlink = document.getElementById('shortlink').value;
                                        const accessCode = document.getElementById('accessCode').value.trim();
                    
                                        if (!accessCode) {
                                            alert("请输入访问码！");
                                            return;
                                        }
                    
                                        console.log("shortlink:", shortlink);
                                        console.log("访问码:", accessCode);
                    
                                        const url = window.location.origin + '/'
                                            + encodeURIComponent(shortlink)
                                            + '?code=' + encodeURIComponent(accessCode);
                    
                                        window.open(url, '_blank');
                                    });
                                </script>
                            </body>
                            </html>
                        """.formatted(shortlink));
                } catch (IOException e) {
                    throw new ServiceException("返回简历出错");
                }
                return;
            }
        }
        //未启用访问码/经判断访问码合法，直接跳转
        String fileUrl = cv.getFileUrl();
        //http重定向
        try {
            //访问时间记录
            resumeMapper.addVisitDate(cv.getShortlink(), LocalDate.now());
            //访问日志记录
            ResumeAccessLog resumeAccessLog = new ResumeAccessLog();
            resumeAccessLog.setShortlink(cv.getShortlink());
                //IP、浏览器、操作系统、访问设备
            resumeAccessLog.setBrowser(LinkUtil.getBrowser(request));
            resumeAccessLog.setDevice(LinkUtil.getDevice(request));
            resumeAccessLog.setIp(LinkUtil.getActualIp(request));
            resumeAccessLog.setOs(LinkUtil.getOs(request));

            resumeMapper.addAccessLog(resumeAccessLog);

            //格式问题
            int lastSlash = fileUrl.lastIndexOf("/");
            String prefix = fileUrl.substring(0, lastSlash + 1);
            String fileName = fileUrl.substring(lastSlash + 1);

            String encodedFileName = URLEncoder
                    .encode(fileName, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            String redirectUrl = prefix + encodedFileName;

            response.sendRedirect(redirectUrl);
            return;

        } catch (Exception e) {
            throw new ServiceException("链接重定向出错");
        }
    }

    @Override
    public TableDataInfo getAllAccessCode(Integer pageNum, Integer pageSize,Long userId) {
        //查询访问码total
        Long total = resumeMapper.getTotalAccessCodeByUserId(userId);
        //查询访问码列表
        List<ResumeAccessCode> list = resumeMapper.getAllAccessCodeByUserId((pageNum - 1) * pageSize,pageSize,userId);
        TableDataInfo tableDataInfo = new TableDataInfo();
        tableDataInfo.setCode(200);
        tableDataInfo.setMsg("操作成功");
        tableDataInfo.setRows(list);
        tableDataInfo.setTotal(total);
        return tableDataInfo;
    }

    @Override
    public void addAccessCode(ResumeAccessCode resumeAccessCode, Long userId) {
        //随机生成访问码
        resumeAccessCode.setAccessCode(AccessCodeGenerator.generate4());
        //查询用户短链接
        String shontlink = resumeMapper.selectShortLinkByUserId(userId);
        //根据短链，插入访问码
        resumeMapper.addAccessCode(resumeAccessCode,shontlink);
    }

    @Override
    public TableDataInfo getLinkAccessData(Date beginTime, Date endTime, Long userId) {
        String shortlink = resumeMapper.selectShortLinkByUserId(userId);
        List<ResumeMonitor> list = resumeMapper.getLinkAccessData(beginTime,endTime,shortlink);
        TableDataInfo tableDataInfo = new TableDataInfo();
        tableDataInfo.setCode(200);
        tableDataInfo.setMsg("操作成功");
        tableDataInfo.setRows(list);
        tableDataInfo.setTotal(list.size());
        return tableDataInfo;
    }

    @Override
    public TableDataInfo getLinkAccessLog(Integer pageNum, Integer pageSize, Long userId) {
        String shortlink = resumeMapper.selectShortLinkByUserId(userId);
        //查询日志total
        Long total = resumeMapper.getTotalAccessLog(shortlink);
        //查询日志列表
        List<ResumeAccessLog> list = resumeMapper.getAllAccessLog((pageNum - 1) * pageSize,pageSize,shortlink);
        //构建返回参数
        TableDataInfo tableDataInfo = new TableDataInfo();
        tableDataInfo.setCode(200);
        tableDataInfo.setMsg("操作成功");
        tableDataInfo.setRows(list);
        tableDataInfo.setTotal(total);
        return tableDataInfo;
    }

    @Override
    public void updateDeadLine(Date time, Long userId) {
        resumeMapper.updateDeadLine(time,userId);
    }


}
