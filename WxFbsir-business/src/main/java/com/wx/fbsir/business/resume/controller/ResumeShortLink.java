package com.wx.fbsir.business.resume.controller;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.wx.fbsir.business.documentparse.controller.DocumentParseController;
import com.wx.fbsir.business.resume.domain.ResumeAccessCode;
import com.wx.fbsir.business.resume.service.ResumeShortLinkService;
import com.wx.fbsir.common.annotation.Anonymous;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;



@RestController
public class ResumeShortLink extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseController.class);

    @Autowired
    ResumeShortLinkService resumeShortLinkService;

    /**
     * 简历短链跳转
     * @param shortlink
     */
    @Anonymous
    @GetMapping("/{shorturl}")
    public void restoreUrl(@PathVariable("shorturl") String shortlink,
                           @RequestParam(value = "code",required = false) String accessCode,
                           HttpServletRequest request,
                           HttpServletResponse response){
        // 手动校验是否为 6 位字母数字
        if (!shortlink.matches("[a-zA-Z0-9]{6}")) {
            error("访问短链结构错误");
            return;
        }

        resumeShortLinkService.restoreUrl(shortlink,accessCode,request,response);
    }

    /**
     * 获取访问码列表
     * @return
     */
    @GetMapping("/resume-link/accessCode")
    public TableDataInfo getAllAccessCode(@RequestParam("PageNum") Integer pageNum,
                                          @RequestParam("PageSize") Integer pageSize){
        TableDataInfo tableDataInfo = resumeShortLinkService.getAllAccessCode(pageNum,pageSize,getUserId());
        return tableDataInfo;

    }

    /**
     * 新增访问码
     * @param resumeAccessCode
     * @return
     */
    @PostMapping("/resume-link/addAccessCode")
    public AjaxResult addAccessCode(@RequestBody ResumeAccessCode resumeAccessCode){
        resumeShortLinkService.addAccessCode(resumeAccessCode,getUserId());
        return success();
    }

    /**
     * 批量新增访问码
     * @param Time
     * @return
     */
    @PostMapping("/resume-link/batchAddAccessCode")
    public AjaxResult batchAddAccessCode(@JsonFormat(pattern = "yyyy-MM-dd") Date Time,Integer count){
        for (int i = 0; i < count; i++) {
            ResumeAccessCode resumeAccessCode = new ResumeAccessCode();
            resumeAccessCode.setDeadline(Time);
            resumeAccessCode.setAccessibleCount(1);
            addAccessCode(resumeAccessCode);
        }
        return success();
    }

    /**
     * 获取监控的链接访问数据
     * @param beginTime
     * @param endTime
     * @return
     */
    @GetMapping("/AccessData")
    public TableDataInfo getAccessData(@JsonFormat(pattern = "yyyy-MM-dd") Date beginTime,
                                       @JsonFormat(pattern = "yyyy-MM-dd") Date endTime){
        TableDataInfo tableDataInfo = resumeShortLinkService.getLinkAccessData(beginTime,endTime,getUserId());
        return tableDataInfo;
    }

    /**
     * 分页获取简历访问日志
     * @param pageNum
     * @param pageSize
     * @return
     */
    @GetMapping("/AccessLog")
    public TableDataInfo getLinkAccessLog(@RequestParam("PageNum") Integer pageNum,
                                          @RequestParam("PageSize") Integer pageSize){
        TableDataInfo tableDataInfo = resumeShortLinkService.getLinkAccessLog(pageNum,pageSize,getUserId());
        return tableDataInfo;
    }

    /**
     * 设置截止时间
     * @param Time
     * @return
     */
    @GetMapping("/updateDeadLine")
    public AjaxResult updateDeadLine(@JsonFormat(pattern = "yyyy-MM-dd") Date Time){
        resumeShortLinkService.updateDeadLine(Time,getUserId());
        return success();
    }





}
