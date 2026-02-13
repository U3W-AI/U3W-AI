package com.wx.fbsir.business.resume.service.impl;

import com.wx.fbsir.business.dailyassistant.domain.YuanqiAgentConfig;
import com.wx.fbsir.business.dailyassistant.service.IYuanqiAgentConfigService;
import com.wx.fbsir.business.dailyassistant.service.impl.YuanqiAgentApiService;
import com.wx.fbsir.business.dailyassistant.service.impl.YuanqiAgentApiService.YuanqiApiResponse;
import com.wx.fbsir.business.documentparse.service.impl.DocumentParseServiceImpl;
import com.wx.fbsir.business.resume.domain.ParseResult;
import com.wx.fbsir.business.resume.mapper.ResumeMapper;
import com.wx.fbsir.business.resume.domain.CV;
import com.wx.fbsir.business.resume.service.ResumeManagementService;
import com.wx.fbsir.business.resume.utils.HashUtil;
import com.wx.fbsir.common.exception.ServiceException;
import com.wx.fbsir.common.utils.uuid.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;


@Service
public class ResumeManagementServiceImpl implements ResumeManagementService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseServiceImpl.class);

    @Autowired
    ResumeMapper resumeMapper;

    @Autowired
    private IYuanqiAgentConfigService yuanqiAgentConfigService;

    @Autowired
    private YuanqiAgentApiService yuanqiAgentApiService;

    @Value("${wxfbsir.domain}")
    private String Domain;

    @Override
    public void storageResume(String resumeId, String resumeName, String url, Long userId) {

        String shorturl = generateSuffix(resumeName);
        CV cv = new CV();
        cv.setUserId(userId);
        cv.setCvId(resumeId);
        cv.setCvName(resumeName);
        cv.setShortlink(shorturl);
        cv.setFileUrl(url);
        cv.setAvailable(1);
        cv.setName("待解析");
        cv.setPhone("待解析");
        cv.setMail("待解析");
        cv.setParseContent("待解析");
        resumeMapper.insertResume(cv);

    }

    /**
     * 生成短链方法（私有）
     * @param resumeName
     * @return
     */
    private String generateSuffix(String resumeName) {
        int customGenerateCount = 0;
        String shortUri;
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接频繁生成，请稍后再试");
            }
            // str = 简历名+UUID
            String str = resumeName + UUID.randomUUID();
            shortUri = HashUtil.hashToBase62(str);
            //查询数据库，保证短链唯一
            Long count = resumeMapper.findCVcountByShortLink(shortUri);
            if(count == 0){
                break;
            }
            customGenerateCount++;
        }
        return shortUri;
    }

    @Override
    public Map<String, Object> hasResume(Long userId) {
        CV cv = resumeMapper.ResumeExists(userId);
        HashMap<String, Object> map = new HashMap<>();
        if(cv != null && cv.getAvailable().equals(1)){
            map.put("exists","true");
        }else{
            map.put("exists","false");
        }
        return map;
    }

    @Override
    public void updataResume(String resumeId, String resumeName, String url, Long userId) {
        CV cv = new CV();
        cv.setUserId(userId);
        cv.setCvId(resumeId);
        cv.setCvName(resumeName);
        cv.setFileUrl(url);
        cv.setAvailable(1);
        cv.setName("待解析");
        cv.setPhone("待解析");
        cv.setMail("待解析");
        cv.setParseContent("待解析");
        resumeMapper.updataResume(cv);
    }

    @Override
    public void parseResume(Long userId) {

        YuanqiAgentConfig config = yuanqiAgentConfigService.selectActiveConfigByUserIdDecrypted(userId, "cv_parse");
        CV cv = resumeMapper.selectResumeByUserId(userId);
        //获取智能体数据
        String appKey = config.getApiKey();
        String appId = config.getAgentId();
        String userIdStr = String.valueOf(userId);
        //访问智能体API
        YuanqiApiResponse response = yuanqiAgentApiService.parseDocument(
                appKey,
                appId,
                userIdStr,
                userIdStr,
                "",
                cv.getFileUrl(),
                cv.getCvName()
        );

        if (response.isSuccess()) {
            CV resume = resumeMapper.selectResumeByUserId(userId);
            //查询是否完成回调
            if (!(resume != null && resume.getProcessStatus() == 1)) {
                resumeMapper.updataParseStatus(userId,0);
            }
        } else {
            log.error("[异步任务] 工作流任务提交失败 - 记录ID: {}, 错误: {}", cv.getCvId(), response.getErrorMessage());
            throw new ServiceException("工作流任务提交失败");
        }
    }

    @Override
    public ParseResult getResumeStatus(Long userId) {
        ParseResult parseResult = resumeMapper.getParseResult(userId);
        parseResult.setShortlink(Domain + "/" + parseResult.getShortlink());
        return parseResult;
    }

    @Override
    public int updataResumeParseResult(String userId, ParseResult parseContent,int statusCode) {
        int result = resumeMapper.updataParseResult(userId,parseContent,statusCode);
        return result;
    }

    @Override
    public void updateAccessCodeAvailable(Integer available, Long userId) {
        CV cv = new CV();
        cv.setUserId(userId);
        cv.setAccessCodeAvailable(available);
        resumeMapper.updataResume(cv);
    }


}
