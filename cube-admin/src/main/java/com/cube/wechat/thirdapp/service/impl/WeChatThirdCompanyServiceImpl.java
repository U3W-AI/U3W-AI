package com.cube.wechat.thirdapp.service.impl;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.WeChatThirdCompany;
import com.cube.wechat.thirdapp.mapper.WeChatThirdCompanyMapper;
import com.cube.wechat.thirdapp.service.WeChatThirdCompanyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年08月06日 09:54
 */
@Service
@Slf4j
public class WeChatThirdCompanyServiceImpl implements WeChatThirdCompanyService {

    @Autowired
    private WeChatThirdCompanyMapper WeChatThirdCompanyMapper;

    @Override
    public R saveCompanyInfo(WeChatThirdCompany WeChatThirdCompany) {
        try {
            //查询是否已存在
            WeChatThirdCompany thirdCompany = WeChatThirdCompanyMapper.selectByCorpId(WeChatThirdCompany.getCorpId(),WeChatThirdCompany.getSuiteId());
            if(thirdCompany!=null){
                //修改
                String id = thirdCompany.getId();
                WeChatThirdCompany.setId(id);
                WeChatThirdCompany.setModtime(new Date());
                WeChatThirdCompany.setRectime(new Date());
                WeChatThirdCompanyMapper.updateByPrimaryKeySelective(WeChatThirdCompany);
            }else{
                WeChatThirdCompany.setId(UUID.randomUUID().toString());
                WeChatThirdCompany.setModtime(new Date());
                WeChatThirdCompany.setAddtime(new Date());
                WeChatThirdCompany.setRectime(new Date());
                WeChatThirdCompanyMapper.insertSelective(WeChatThirdCompany);
            }
            return R.ok(WeChatThirdCompany,"保存成功");
        }catch (Exception e){
            e.printStackTrace();
            return R.fail("保存失败");
        }

    }

    @Override
    public R updateCompanyStatus(WeChatThirdCompany WeChatThirdCompany) {
        WeChatThirdCompany thirdCompany = WeChatThirdCompanyMapper.selectByCorpId(WeChatThirdCompany.getCorpId(), WeChatThirdCompany.getSuiteId());
        if(thirdCompany!=null){
            //修改
            String id = thirdCompany.getId();
            WeChatThirdCompany.setId(id);
            WeChatThirdCompany.setModtime(new Date());
            WeChatThirdCompanyMapper.updateByPrimaryKeySelective(WeChatThirdCompany);
        }
        return R.ok();
    }

    @Override
    public R<WeChatThirdCompany> selectCompanyInfo(WeChatThirdCompany WeChatThirdCompany) {
        WeChatThirdCompany thirdCompany = WeChatThirdCompanyMapper.selectByCorpId(WeChatThirdCompany.getCorpId(), WeChatThirdCompany.getSuiteId());
        return R.ok(thirdCompany);
    }

    @Override
    public R<String> selectCorpServerName(Map map) {
        String corpServerName = WeChatThirdCompanyMapper.selectCorpServerName(map);
        return R.ok(corpServerName);
    }

    @Override
    public R<List<WeChatThirdCompany>> selectAllCompany(WeChatThirdCompany WeChatThirdCompany) {
        List<WeChatThirdCompany> WeChatThirdCompanyList = WeChatThirdCompanyMapper.selectAllCorp(WeChatThirdCompany.getSuiteId());
        return R.ok(WeChatThirdCompanyList);
    }
}
