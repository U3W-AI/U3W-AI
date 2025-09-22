package com.cube.wechat.selfapp.app.service;

import com.cube.wechat.selfapp.corpchat.util.ResultBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/31 17:32
 */
public interface WechatMpService {
    /**
     * 投递文章
     *
     * @param map
     * @return
     */
    ResultBody publishToOffice(Map map);

    /**
     * 获取图片素材
     *
     * @param map
     * @return
     */

    ResultBody getMaterial(Map map);

    /**
     * 通过url上传图片素材
     *
     * @param map
     * @return
     */

    ResultBody uploadMaterial(String type, String unionId, String imgDescription, MultipartFile multipartFile);

    /**
     * 上传分封面素材
     *
     * @param map
     * @return
     */

    ResultBody uploadCoverImgMaterial(Map map);
}
