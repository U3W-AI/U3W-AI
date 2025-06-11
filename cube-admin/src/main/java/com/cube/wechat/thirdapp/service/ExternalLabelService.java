package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.RmExternalLabel;

import java.util.List;

/**
 @author sjl
  * @Created date 2024/3/18 13:30
 */
public interface ExternalLabelService {
    public R saveExternalLabel(RmExternalLabel rmExternalLabel);
    public R deleteExternalLabel(RmExternalLabel rmExternalLabel);

    public R saveExternalLabelBatch(List<RmExternalLabel> rmExternalLabelList);
}
