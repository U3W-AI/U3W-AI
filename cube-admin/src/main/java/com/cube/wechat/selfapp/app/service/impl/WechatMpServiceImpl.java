package com.cube.wechat.selfapp.app.service.impl;

import com.cube.wechat.selfapp.app.config.WechatMpConfig;
import com.cube.wechat.selfapp.app.constants.WxExceptionConstants;
import com.cube.wechat.selfapp.app.domain.Item;
import com.cube.wechat.selfapp.app.domain.WcOfficeAccount;
import com.cube.wechat.selfapp.app.mapper.UserInfoMapper;
import com.cube.wechat.selfapp.app.service.WechatMpService;
import com.cube.wechat.selfapp.app.util.WeChatUtils;
import com.cube.wechat.selfapp.corpchat.util.ResultBody;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.draft.WxMpAddDraft;
import me.chanjar.weixin.mp.bean.draft.WxMpDraftArticles;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/31 17:32
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WechatMpServiceImpl implements WechatMpService {
    private final WeChatUtils weChatUtils;
    private final UserInfoMapper userInfoMapper;
    private final WechatMpConfig wechatMpConfig;
    @Override
    public ResultBody publishToOffice(Map map) {
        try {
            String unionId = map.get("unionId").toString();
            String title = map.get("title").toString();
            String contentText = map.get("contentText").toString();
            String shareUrl = map.get("shareUrl").toString();
            Object thumbMediaId = map.get("thumbMediaId");
            String userId = userInfoMapper.getUserIdByUnionId(unionId);
            WcOfficeAccount wo = weChatUtils.getOfficeAccountByUserId(userId);
            if(unionId == null || title == null || contentText == null || shareUrl == null) {
                throw new RuntimeException(WxExceptionConstants.WX_PARAMETER_EXCEPTION);
            }
            if (wo == null) {
                throw new RuntimeException(WxExceptionConstants.WX_AUTH_EXCEPTION);
            }
            WxMpService wxMpService = wechatMpConfig.getWxMpService(unionId);
            WxMpDraftArticles draft = new WxMpDraftArticles();
            draft.setTitle(title);
            draft.setContent(contentText); // 包含图片标签的最终内容
            if(thumbMediaId == null) {
                draft.setThumbMediaId(wo.getMediaId());
            } else {
                draft.setThumbMediaId(thumbMediaId.toString()); // 直接使用已有封面图media_id
            }
            draft.setShowCoverPic(1); // 显示封面
            draft.setContentSourceUrl(shareUrl); // 添加文章来源链接
            WxMpAddDraft wxMpAddDraft = WxMpAddDraft.builder().articles(List.of(draft)).build();
            // 3. 调用微信接口上传草稿
            String mediaId = wxMpService.getDraftService().addDraft(wxMpAddDraft);
            String publishedArticleUrl = weChatUtils.getPublishedArticleUrl(mediaId, unionId);
            if (publishedArticleUrl == null || publishedArticleUrl.isEmpty()) {
                return ResultBody.error(204, "获取文章链接失败");
            } else {
                return ResultBody.success(publishedArticleUrl);
            }
        } catch (Exception e) {
            return ResultBody.error(500, e.getMessage());
        }
    }

    @Override
    public ResultBody getMaterial(Map map) {
        try {
            String unionId = null;
            String type = null;
            try {
                unionId = map.get("unionId").toString();
                type = map.get("type").toString();
            } catch (Exception e) {
                throw new RuntimeException(WxExceptionConstants.WX_PARAMETER_EXCEPTION);
            }
            List<Item> itemList = Collections.emptyList();
            // TODO 后续增添更多类型
            if(type.equals("image")) {
                itemList = weChatUtils.getImgMaterial("image", unionId);
            }
            return ResultBody.success(itemList);
        } catch (Exception e) {
            return ResultBody.error(204, e.getMessage());
        }
    }

    @Override
    public ResultBody uploadMaterial(String type, String unionId, String imgDescription, MultipartFile multipartFile) {
        try {
            // TODO 后续增加其他类型上传
            if(type.equals("image")) {
                InputStream inputStream = multipartFile.getInputStream();
                String url = weChatUtils.uploadImgMaterial(unionId, inputStream, imgDescription);
                if(url == null || url.isEmpty()) {
                    return ResultBody.error(204, "url为空");
                }
                return ResultBody.success(url);
            }
            return ResultBody.error(204, "暂不支持该类型上传");
        } catch (Exception e) {
            return ResultBody.error(500, e.getMessage());
        }
    }

    @Override
    public ResultBody uploadCoverImgMaterial(Map map) {
        return null;
    }
}
