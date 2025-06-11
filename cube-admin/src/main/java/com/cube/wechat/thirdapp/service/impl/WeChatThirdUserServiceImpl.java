package com.cube.wechat.thirdapp.service.impl;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.WeChatThirdUser;
import com.cube.wechat.thirdapp.mapper.WeChatThirdUserMapper;
import com.cube.wechat.thirdapp.service.WeChatThirdUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * @author sjl
 * @Created date 2024/2/28 15:51
 */
@Service

public class WeChatThirdUserServiceImpl implements WeChatThirdUserService {
    @Autowired
    private WeChatThirdUserMapper weChatThirdUserMapper;
    @Override
    public R saveQywxThirdAuthUser(WeChatThirdUser qywxThirdUser) {
        String corpId = qywxThirdUser.getCorpId();
        //查询是否该企业授权人信息
        List<WeChatThirdUser> qywxThirdUserList = weChatThirdUserMapper.selectByCorpIdAndAppId(corpId,qywxThirdUser.getSuiteId());
        if(!CollectionUtils.isEmpty(qywxThirdUserList)){
            //清除
            weChatThirdUserMapper.deleteByCorpIdAndAppId(corpId,qywxThirdUser.getSuiteId());
        }
        //保存新的
        qywxThirdUser.setId(UUID.randomUUID().toString());
        qywxThirdUser.setAddtime(new Date());
        weChatThirdUserMapper.insertSelective(qywxThirdUser);
        return R.ok();
    }

    @Override
    public R deleteQywxThirdAuthUser(String corpId,String agentId) {
        //清除
        weChatThirdUserMapper.deleteByCorpIdAndAppId(corpId,agentId);
        return R.ok();
    }
}
