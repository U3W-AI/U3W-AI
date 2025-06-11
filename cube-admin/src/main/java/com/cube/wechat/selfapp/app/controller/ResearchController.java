package com.cube.wechat.selfapp.app.controller;

import com.cube.common.annotation.Log;
import com.cube.common.core.controller.BaseController;
import com.cube.wechat.selfapp.app.domain.Research;
import com.cube.wechat.selfapp.app.service.ResearchService;
import com.cube.wechat.selfapp.wecom.util.ResultBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月13日 09:10
 */
@RestController
@RequestMapping("/res")
public class ResearchController  extends BaseController {

     @Autowired
     private ResearchService researchService;


     @GetMapping("/getReportList")
     public ResultBody getReportList(Research research){
          return researchService.getReportList(research);
     };

     @GetMapping("/getReportDetail")
     public ResultBody getReportDetail(String resId){
          return researchService.getReportDetail(resId);
     };

     @PostMapping("/addReport")
     @Log(title = "研报管理-上传干货")
     public ResultBody addReport(@RequestBody Research research){
          research.setUserId(getUserId());
          research.setUserName(getNickName());
          return researchService.addReport(research);
     };

     @PostMapping("/updateReport")
     @Log(title = "研报管理-修改干货")
     public ResultBody updateReport(@RequestBody Research research){
          research.setUserId(getUserId());
          research.setUserName(getNickName());
          return researchService.updateReport(research);
     };

     @PostMapping("/changeResportFlowStatus")
     @Log(title = "研报管理-审核干货")
     public ResultBody changeResportFlowStatus(@RequestBody Research research){
          //设置用户ID和昵称
          research.setUserId(getUserId());
          research.setUserName(getNickName());
          return researchService.changeResportFlowStatus(research);
     };

     @GetMapping("/getResOpeData")
     public ResultBody getResOpeData(Research research){
          return researchService.getResOpeData(research);
     }
}
