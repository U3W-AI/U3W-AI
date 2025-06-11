package com.cube.wechat.thirdapp.service.impl;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.RmExternalLabel;
import com.cube.wechat.thirdapp.mapper.RmExternalLabelMapper;
import com.cube.wechat.thirdapp.service.ExternalLabelService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 @author sjl
  * @Created date 2024/3/18 13:31
 */
@Service
@Transactional
public class ExternalLabelServiceImpl implements ExternalLabelService {
    @Autowired
    private RmExternalLabelMapper rmExternalLabelMapper;
    @Override
    public R saveExternalLabel(RmExternalLabel rmExternalLabel) {
        rmExternalLabel.setId(UUID.randomUUID().toString());
        rmExternalLabel.setCreateTime(new Date());
        rmExternalLabelMapper.insertSelective(rmExternalLabel);
        return R.ok(null);
    }

    @Override
    public R deleteExternalLabel(RmExternalLabel rmExternalLabel) {
        rmExternalLabelMapper.deleteExternalLabel(rmExternalLabel);
        return R.ok(null);
    }

    @Override
    public R saveExternalLabelBatch(List<RmExternalLabel> rmExternalLabelList) {
        if (CollectionUtils.isNotEmpty(rmExternalLabelList)) {
            rmExternalLabelMapper.insertExternalLabelSelectiveBatch(rmExternalLabelList);
        }
        return R.ok(null);
    }
}
