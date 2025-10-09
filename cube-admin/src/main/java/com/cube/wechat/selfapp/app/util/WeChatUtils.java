package com.cube.wechat.selfapp.app.util;

import cn.hutool.core.io.IoUtil;
import com.alibaba.fastjson.JSONObject;
import com.cube.wechat.selfapp.app.config.WechatMpConfig;
import com.cube.wechat.selfapp.app.constants.WxExceptionConstants;
import com.cube.wechat.selfapp.app.domain.Item;
import com.cube.wechat.selfapp.app.domain.WcOfficeAccount;
import com.cube.wechat.selfapp.app.domain.WechatInfo;
import com.cube.wechat.selfapp.app.service.UserInfoService;
import com.cube.wechat.selfapp.corpchat.util.ResultBody;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.draft.WxMpDraftArticles;
import me.chanjar.weixin.mp.bean.draft.WxMpDraftInfo;
import me.chanjar.weixin.mp.bean.material.WxMpMaterial;
import me.chanjar.weixin.mp.bean.material.WxMpMaterialFileBatchGetResult;
import me.chanjar.weixin.mp.bean.material.WxMpMaterialUploadResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/29 16:39
 */
@Component
@RequiredArgsConstructor
public class WeChatUtils {
    private final UserInfoUtil userInfoUtil;
    private final UserInfoService userInfoService;
    @Autowired
    private  WechatMpConfig wechatMpConfig;
    @Value("${ruoyi.profile}")
    private String tmpUrl;

    /**
     * 获取token
     */
    public String getOfficeAccessToken(String appId, String secret) {
        String accessTokenUrl = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=" + appId + "&secret=" + secret;
        JSONObject jsonObject = RestUtils.get(accessTokenUrl);
        int errCode = jsonObject.getIntValue("errcode");
        if (errCode == 0) {
            return jsonObject.getString("access_token");
        }
        return null;
    }

    /**
     * 获取用户公众号信息包括token
     *
     * @param unionId 用户唯一标识
     */
    public WechatInfo getWechatInfoByUnionId(String unionId) throws Exception {
        try {
            String userId = userInfoUtil.getUserIdByUnionId(unionId);
            if (userId == null) {
                throw new RuntimeException(WxExceptionConstants.WX_AUTH_EXCEPTION);
            }
            WcOfficeAccount wo = getOfficeAccountByUserId(userId);
            String accessToken = getOfficeAccessToken(wo.getAppId(), wo.getAppSecret());
            if (accessToken == null) {
                throw new RuntimeException(WxExceptionConstants.WX_AUTH_EXCEPTION);
            }
            WechatInfo wechatInfo = new WechatInfo();
            wechatInfo.setToken(accessToken);
            wechatInfo.setAppId(wo.getAppId());
            wechatInfo.setSecret(wo.getAppSecret());
            return wechatInfo;
        } catch (Exception e) {
            throw new RuntimeException(WxExceptionConstants.WX_AUTH_EXCEPTION);
        }
    }

    /**
     * 获取用户公众号基本信息
     *
     * @param userId 用户id
     */
    public WcOfficeAccount getOfficeAccountByUserId(String userId) throws Exception {
        try {
            ResultBody officeAccountByUserId = userInfoService.getOfficeAccountByUserId(userId);
            Object data = officeAccountByUserId.getData();
            if (data != null && data instanceof WcOfficeAccount) {
                return (WcOfficeAccount) data;
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(WxExceptionConstants.WX_AUTH_EXCEPTION);
        }
    }

    /**
     * 发布草稿并获取正式图文的永久URL
     *
     * @param draftMediaId 草稿的media_id
     * @return 正式图文的URL
     */
    public String getPublishedArticleUrl(String draftMediaId, String unionId) throws WxErrorException {
        WxMpService wxMpService = wechatMpConfig.getWxMpService(unionId);
        WxMpDraftInfo draft = wxMpService.getDraftService()
                .getDraft(draftMediaId);
        String url = "";
        List<WxMpDraftArticles> newsItem = draft.getNewsItem();
        for (WxMpDraftArticles wxMpDraftArticles : newsItem) {
            url = wxMpDraftArticles.getUrl();
        }
        return url;
    }

    public String uploadImgMaterial(String unionId, InputStream inputStream, String description) throws WxErrorException, IOException {
        try {
            WxMpService wxMpService = wechatMpConfig.getWxMpService(unionId);
            if (wxMpService == null) {
                throw new RuntimeException(WxExceptionConstants.WX_AUTH_EXCEPTION);
            }
            String imgUrl = tmpUrl + "/img/" + unionId + "/" + unionId + "-" + description + ".png";
            File folder = new File(tmpUrl + "/img/" + unionId);
            // 判断文件夹是否存在
            if (!folder.exists()) {
                boolean mkdirs = folder.mkdirs();
                if (!mkdirs) {
                    throw new RuntimeException(WxExceptionConstants.WX_UPLOAD_IMG_EXCEPTION);
                }
            }
            File file = new File(imgUrl);
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            IoUtil.copy(inputStream, fileOutputStream);
            WxMpMaterial wxMpMaterial = new WxMpMaterial();
            wxMpMaterial.setFile(file);
            wxMpMaterial.setName(description);
            WxMpMaterialUploadResult result = wxMpService.getMaterialService().materialFileUpload("image", wxMpMaterial);
            String mediaId = result.getMediaId();
            System.out.println(mediaId);
            return result.getUrl();
        } catch (Exception e) {
            throw new RuntimeException(WxExceptionConstants.WX_UPLOAD_IMG_EXCEPTION);
        }
    }

    public List<Item> getImgMaterial(String type, String unionId) throws WxErrorException {
        try {
            int page = 0;
            int count = 20;
            WxMpService wxMpService = wechatMpConfig.getWxMpService(unionId);
            if (wxMpService == null) {
                throw new RuntimeException(WxExceptionConstants.WX_AUTH_EXCEPTION);
            }
            WxMpMaterialFileBatchGetResult result = wxMpService.getMaterialService()
                    .materialFileBatchGet(type, page, count);
            // 总素材数
            int totalCount = result.getTotalCount();
            System.out.println("图片素材总数：" + totalCount);

            List<Item> items = new ArrayList<>();
            // 本次查询到的素材列表
            List<WxMpMaterialFileBatchGetResult.WxMaterialFileBatchGetNewsItem> materials = result.getItems();
            for (WxMpMaterialFileBatchGetResult.WxMaterialFileBatchGetNewsItem material : materials) {
                Item item = new Item();
                item.setMedia_id(material.getMediaId());
                item.setName(material.getName());
                item.setUrl(material.getUrl());
                item.setUpdate_time(material.getUpdateTime());
                items.add(item);
            }
            return items;
        } catch (Exception e) {
            throw new RuntimeException(WxExceptionConstants.WX_GET_MATERIAL_EXCEPTION);
        }
    }
}
