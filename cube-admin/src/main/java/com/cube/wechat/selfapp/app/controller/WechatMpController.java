package com.cube.wechat.selfapp.app.controller;

import com.cube.wechat.selfapp.app.service.WechatMpService;
import com.cube.wechat.selfapp.corpchat.util.ResultBody;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/31 17:32
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/wx")
public class WechatMpController {
    private final WechatMpService wechatMpService;

    /**
     * 投递到公众号
     *
     * @return
     */
    @PostMapping("/publishToOffice")
    public ResultBody publishToOffice(@RequestBody Map map) {
        return wechatMpService.publishToOffice(map);
    }

    @PostMapping("/getMaterial")
    public ResultBody getMaterial(@RequestBody Map map) {
        return wechatMpService.getMaterial(map);
    }

    @PostMapping("/uploadMaterial")
    public ResultBody uploadMaterial(@RequestParam("type") String type,
                                     @RequestParam("unionId") String unionId,
                                     @RequestParam("imgDescription") String imgDescription,
                                     @RequestParam("multipartFile") MultipartFile multipartFile) {
        return wechatMpService.uploadMaterial(type, unionId, imgDescription, multipartFile);
    }

    @PostMapping("/uploadCoverImgMaterial")
    public ResultBody uploadCoverImgMaterial(@RequestBody Map map) {
        return wechatMpService.uploadCoverImgMaterial(map);
    }
}
