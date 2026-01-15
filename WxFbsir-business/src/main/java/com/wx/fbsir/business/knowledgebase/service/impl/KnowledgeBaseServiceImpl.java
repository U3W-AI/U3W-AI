package com.wx.fbsir.business.knowledgebase.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.wx.fbsir.business.knowledgebase.util.KnowledgeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wx.fbsir.business.knowledgebase.domain.KnowledgeBaseInfo;
import com.wx.fbsir.business.knowledgebase.domain.SysUserExtend;
import com.wx.fbsir.business.knowledgebase.enums.UploadType;
import com.wx.fbsir.business.knowledgebase.mapper.KnowledgeBaseMapper;
import com.wx.fbsir.business.knowledgebase.mapper.SysUserExtendMapper;
import com.wx.fbsir.business.knowledgebase.service.IKnowledgeBaseService;
import com.wx.fbsir.common.utils.SecurityUtils;
import com.wx.fbsir.common.utils.StringUtils;

import java.io.IOException;

/**
 * 知识库Service业务层处理
 * 
 * @author wxfbsir
 * @date 2025-12-20
 */
@Service
public class KnowledgeBaseServiceImpl implements IKnowledgeBaseService
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private SysUserExtendMapper sysUserExtendMapper;

    @Autowired
    private KnowledgeUtil knowledgeUtil;

    /**
     * 查询知识库
     * 
     * @param kbId 知识库主键
     * @return 知识库
     */
    @Override
    public KnowledgeBaseInfo selectKnowledgeBaseByKbId(Long kbId)
    {
        KnowledgeBaseInfo kb = knowledgeBaseMapper.selectKnowledgeBaseByKbId(kbId);
        if (kb == null)
        {
            return null;
        }

        // 公共模板任何用户都可以查看
        if (kb.getIsPublicTemplate() != null && kb.getIsPublicTemplate() == 1)
        {
            return kb;
        }

        // 非公共模板：仅创建者或超级账户可以查看
        Long currentUserId = SecurityUtils.getUserId();
        SysUserExtend currentUser = sysUserExtendMapper.selectUserExtendByUserId(currentUserId);
        boolean isOwner = currentUser != null
                && knowledgeUtil.isKbOwnedByUser(currentUser.getHasKnowledgeBase(), kbId);

        if (!isOwner)
        {
            log.error("用户无权查看该知识库，userId: {}, kbId: {}", currentUserId, kbId);
            throw new RuntimeException("无权查看该知识库");
        }

        return kb;
    }

    /**
     * 查询知识库列表
     * 
     * @param knowledgeBaseInfo 知识库
     * @return 知识库
     */
    @Override
    public List<KnowledgeBaseInfo> selectKnowledgeBaseList(KnowledgeBaseInfo knowledgeBaseInfo)
    {
        return knowledgeBaseMapper.selectKnowledgeBaseList(knowledgeBaseInfo);
    }

    /**
     * 查询当前用户可见的知识库列表（包含自己的知识库和公共模板）
     *
     * 规则：
     * - 公共模板（isPublicTemplate = 1）：所有用户可见
     * - 非公共模板：仅当前用户“拥有的知识库”字段（hasKnowledgeBase）中包含的ID可见
     *
     * @return 知识库集合
     */
    @Override
    public List<KnowledgeBaseInfo> selectUserVisibleKnowledgeBase()
    {
        Long currentUserId = SecurityUtils.getUserId();
        SysUserExtend currentUser = sysUserExtendMapper.selectUserExtendByUserId(currentUserId);

        List<KnowledgeBaseInfo> result = new ArrayList<>();

        // 1. 公共模板
        List<KnowledgeBaseInfo> publicTemplates = knowledgeBaseMapper.selectPublicTemplateList();
        if (publicTemplates != null)
        {
            result.addAll(publicTemplates);
        }

        // 2. 自己拥有的知识库
        if (currentUser != null && StringUtils.isNotEmpty(currentUser.getHasKnowledgeBase()))
        {
            String hasKb = currentUser.getHasKnowledgeBase();
            List<Long> ownedIds = Arrays.stream(hasKb.split(","))
                    .filter(StringUtils::isNotEmpty)
                    .map(idStr -> {
                        try
                        {
                            return Long.valueOf(idStr.trim());
                        }
                        catch (NumberFormatException e)
                        {
                            return null;
                        }
                    })
                    .filter(id -> id != null)
                    .collect(Collectors.toList());

            if (!ownedIds.isEmpty())
            {
                List<KnowledgeBaseInfo> ownedList = knowledgeBaseMapper.selectKnowledgeBaseByIds(ownedIds);
                if (ownedList != null)
                {
                    // 合并时按 kbId 去重（优先保留公共模板列表中的元素）
                    for (KnowledgeBaseInfo kb : ownedList)
                    {
                        boolean exists = result.stream()
                                .anyMatch(item -> item.getKbId() != null && item.getKbId().equals(kb.getKbId()));
                        if (!exists)
                        {
                            result.add(kb);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * 查询公共模板列表
     * 
     * @return 知识库集合
     */
    @Override
    public List<KnowledgeBaseInfo> selectPublicTemplateList()
    {
        return knowledgeBaseMapper.selectPublicTemplateList();
    }

    /**
     * 新增知识库
     * 
     * 业务逻辑：
     * 1. 将知识库信息插入到数据库
     * 2. 使用事务保证数据一致性，如果发生异常则回滚
     * 
     * @param knowledgeBaseInfo 知识库对象，包含知识库名称、内容、是否为公共模板等信息
     * @return 结果，大于0表示插入成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 添加事务回滚，确保数据一致性
    public int insertKnowledgeBase(KnowledgeBaseInfo knowledgeBaseInfo)
    {
        int rows = knowledgeBaseMapper.insertKnowledgeBase(knowledgeBaseInfo);
        // 记录到当前用户“拥有的知识库”字段中
        if (rows > 0 && knowledgeBaseInfo.getKbId() != null)
        {
            //获取用户id
            Long currentUserId = SecurityUtils.getUserId();
            //获取目前已经拥有的知识库id
            SysUserExtend currentUser = sysUserExtendMapper.selectUserExtendByUserId(currentUserId);
            String ownedKb = currentUser != null ? currentUser.getHasKnowledgeBase() : null;
           //添加在拥有知识库的字段下
            String updated = knowledgeUtil.appendIdToList(ownedKb, knowledgeBaseInfo.getKbId());
            sysUserExtendMapper.updateHasKnowledgeBase(currentUserId, updated);
        }
        return rows;
    }

    /**
     * 修改知识库
     * 
     * 业务逻辑：
     * 1. 检查知识库是否存在
     * 2. 如果修改的是公共模板，需要检查当前用户是否有模块功能操作权限
     * 3. 只有拥有模块功能操作权限的用户或超级账户才能修改公共模板
     * 
     * @param knowledgeBaseInfo 知识库
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateKnowledgeBase(KnowledgeBaseInfo knowledgeBaseInfo)
    {
        // 1. 检查知识库是否存在
        KnowledgeBaseInfo existingKb = knowledgeBaseMapper.selectKnowledgeBaseByKbId(knowledgeBaseInfo.getKbId());
        if (existingKb == null)
        {
            throw new RuntimeException("知识库不存在");
        }
        
        Long currentUserId = SecurityUtils.getUserId();
        SysUserExtend currentUser = sysUserExtendMapper.selectUserExtendByUserId(currentUserId);

        // 2. 公共模板：需要模块功能操作权限或超级账户
        if (existingKb.getIsPublicTemplate() != null && existingKb.getIsPublicTemplate() == 1)
        {
            boolean hasModulePerm = currentUser != null
                    && currentUser.getIsOpenModulePerm() != null
                    && currentUser.getIsOpenModulePerm() == 1;

            boolean isSuper = currentUser != null
                    && currentUser.getIsSuper() != null
                    && currentUser.getIsSuper() == 1;
            
            // 如果没有模块功能操作权限且不是超级账户，则无权限修改
            if (!hasModulePerm && !isSuper)
            {
                log.error("用户无权限修改公共模板，userId: {}, hasModulePerm: {}, isSuper: {}", 
                        currentUserId, hasModulePerm, isSuper);
                throw new RuntimeException("无权限修改公共模板，需要模块功能操作权限或超级账户权限");
            }
        }
        else
        {
            // 3. 非公共模板：仅创建者
            boolean isOwner = currentUser != null
                    && knowledgeUtil.isKbOwnedByUser(currentUser.getHasKnowledgeBase(), knowledgeBaseInfo.getKbId());

            if (!isOwner)
            {
                log.error("用户无权限修改该知识库，userId: {}, kbId: {}", currentUserId, knowledgeBaseInfo.getKbId());
                throw new RuntimeException("无权限修改该知识库，只能修改自己创建的非公共模板知识库");
            }
        }
        
        // 3. 执行更新操作
        return knowledgeBaseMapper.updateKnowledgeBase(knowledgeBaseInfo);
    }

    /**
     * 批量删除知识库
     * 
     * @param kbId 需要删除的知识库主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteKnowledgeBaseByKbIds(Long kbId)
    {
        if (kbId == null )
        {
            return 0;
        }
        Long currentUserId = SecurityUtils.getUserId();
        SysUserExtend currentUser = sysUserExtendMapper.selectUserExtendByUserId(currentUserId);

        checkDeletePermission(currentUser, currentUserId, kbId);

        return knowledgeBaseMapper.deleteKnowledgeBaseByKbIds(kbId);
    }

    /**
     * 删除知识库信息
     * 
     * @param kbId 知识库主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteKnowledgeBaseByKbId(Long kbId)
    {
        Long currentUserId = SecurityUtils.getUserId();
        SysUserExtend currentUser = sysUserExtendMapper.selectUserExtendByUserId(currentUserId);
        checkDeletePermission(currentUser, currentUserId, kbId);
        return knowledgeBaseMapper.deleteKnowledgeBaseByKbId(kbId);
    }

    /**
     * 收藏/取消收藏知识库
     * 
     * 业务逻辑：
     * 1. 检查知识库是否存在
     * 2. 获取用户当前的收藏列表
     * 3. 如果已收藏则取消收藏，如果未收藏则添加收藏
     * 
     * @param kbId 知识库ID
     * @param userId 用户ID
     * @return true-操作成功，false-操作失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavoriteKnowledge(Long kbId, Long userId)
    {
        // 1. 检查知识库是否存在
        KnowledgeBaseInfo kb = knowledgeBaseMapper.selectKnowledgeBaseByKbId(kbId);
        if (kb == null)
        {
            log.error("知识库不存在，kbId: {}", kbId);
            return false;
        }

        // 2. 获取用户当前的收藏列表
        SysUserExtend userExtend = sysUserExtendMapper.selectUserExtendByUserId(userId);
        if (userExtend == null)
        {
            log.error("用户不存在，userId: {}", userId);
            return false;
        }

        String existingLikes = userExtend.getKbLikesIds();
        List<Long> likesList = new ArrayList<>();
        if (StringUtils.isNotEmpty(existingLikes))
        {
            likesList = Arrays.stream(existingLikes.split(","))
                    .filter(StringUtils::isNotEmpty)
                    .map(Long::valueOf)
                    .collect(Collectors.toList());
        }

        // 3. 如果已收藏则取消收藏，如果未收藏则添加收藏
        String newLikes;
        if (likesList.contains(kbId))
        {
            // 取消收藏
            likesList.remove(kbId);
            newLikes = likesList.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            log.info("用户取消收藏知识库，userId: {}, kbId: {}", userId, kbId);
        }
        else
        {
            // 添加收藏
            likesList.add(kbId);
            newLikes = likesList.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            log.info("用户收藏知识库，userId: {}, kbId: {}", userId, kbId);
        }

        // 4. 更新收藏列表
        int result = sysUserExtendMapper.updateKbLikesIds(userId, newLikes);
        return result > 0;
    }

    /**
     * 查询用户收藏的知识库列表
     * 
     * @return 知识库集合
     */
    @Override
    public List<KnowledgeBaseInfo> selectFavoriteKnowledgeBase()
    {
        Long currentUserId = SecurityUtils.getUserId();
        SysUserExtend currentUser = sysUserExtendMapper.selectUserExtendByUserId(currentUserId);
        
        if (currentUser == null || StringUtils.isEmpty(currentUser.getKbLikesIds()))
        {
            return new ArrayList<>();
        }

        String likesIds = currentUser.getKbLikesIds();
        List<Long> kbIdList = Arrays.stream(likesIds.split(","))
                .filter(StringUtils::isNotEmpty)
                .map(idStr -> {
                    try
                    {
                        return Long.valueOf(idStr.trim());
                    }
                    catch (NumberFormatException e)
                    {
                        return null;
                    }
                })
                .filter(id -> id != null)
                .collect(Collectors.toList());

        if (kbIdList.isEmpty())
        {
            return new ArrayList<>();
        }

        return knowledgeBaseMapper.selectKnowledgeBaseByIds(kbIdList);
    }

    /**
     * 知识库上传（上传到智能体元器或企业微信机器人）
     * 
     * 业务逻辑：
     * 1. 查询知识库内容
     * 2. 将知识库内容写入文件并上传到服务器，生成可访问的URL
     * 3. 根据上传类型调用不同的PlayWright脚本：
     *    - 1: 上传到智能体元器
     *    - 2: 上传到企业微信机器人
     *    - 3: 同时上传到两者（多线程并行处理）
     * 
     * 注意：此方法需要配合WxFbsir-engine模块的PlayWright框架使用
     * 
     * @param kbId 知识库ID
     * @param uploadType 上传类型（1-智能体元器，2-企业微信机器人，3-两者）
     * @param agentName 智能体名称（上传类型为1或3时必填）
     * @param robotName 机器人名称（上传类型为2或3时必填）
     * @return true-上传成功，false-上传失败
     */
    @Override
    public boolean uploadKnowledgeBase(Long kbId, Integer uploadType, String agentName, String robotName, String teamName)
    {
        // 1. 查询知识库内容
        KnowledgeBaseInfo kb = knowledgeBaseMapper.selectKnowledgeBaseByKbId(kbId);
        if (kb == null)
        {
            log.error("知识库不存在，kbId: {}", kbId);
            return false;
        }

        // 2. 将知识库内容写入文件并上传到服务器，生成URL
        String importWebUrl;
        try
        {
            importWebUrl = knowledgeUtil.saveKnowledgeContentAndGetUrl(kb);
        }
        catch (IOException e)
        {
            log.error("知识库内容上传失败，kbId: {}, 错误: {}", kbId, e.getMessage(), e);
            return false;
        }

        // 3. 根据上传类型调用不同的PlayWright脚本
        UploadType type = UploadType.getByCode(uploadType);
        if (type == null)
        {
            log.error("无效的上传类型，uploadType: {}", uploadType);
            return false;
        }

        boolean sent = knowledgeUtil.sendKnowledgeToEngines(kb, importWebUrl, type, agentName, robotName, teamName);
        if (sent)
        {
            log.info("知识库上传任务已发送，kbId: {}, uploadType: {}, agentName: {}, robotName: {}, teamName: {}", 
                    kbId, type.getDesc(), agentName, robotName, teamName);
        }
        return sent;
    }

    /**
     * 本地文档上传
     * 
     * 业务逻辑：
     * 1. 验证文件路径是否有效
     * 2. 将本地文件上传到服务器，生成可访问的URL
     * 3. 根据上传类型调用不同的PlayWright脚本上传到目标平台
     * 
     * 注意：此方法需要配合WxFbsir-engine模块的PlayWright框架使用
     * 
     * @param filePath 本地文档路径（文件需要先通过Controller上传到服务器）
     * @param uploadType 上传类型（1-智能体元器，2-企业微信机器人，3-两者）
     * @param agentName 智能体名称（上传类型为1或3时必填）
     * @param robotName 机器人名称（上传类型为2或3时必填）
     * @return true-上传成功，false-上传失败
     */
    @Override
    public boolean uploadLocalDocument(String filePath, Integer uploadType, String agentName, String robotName, String kbName, String teamName)
    {
        // 1. 验证文件路径（此处filePath应为已上传到服务器后的URL）
        if (StringUtils.isEmpty(filePath))
        {
            log.error("文档路径为空");
            return false;
        }

        // 2. 根据上传类型调用不同的PlayWright脚本
        UploadType type = UploadType.getByCode(uploadType);
        if (type == null)
        {
            log.error("无效的上传类型，uploadType: {}", uploadType);
            return false;
        }

        boolean sent = knowledgeUtil.sendDocumentToEngines(filePath, type, agentName, robotName, kbName, teamName);
        if (sent)
        {
            log.info("本地文档上传任务已发送，filePath: {}, uploadType: {}, agentName: {}, robotName: {}, kbName: {}, teamName: {}", 
                    filePath, type.getDesc(), agentName, robotName, kbName, teamName);
        }
        return sent;
    }
    /**
     * 检查当前用户是否有删除指定知识库的权限
     */
    public void checkDeletePermission(SysUserExtend currentUser, Long currentUserId, Long kbId)
    {
        KnowledgeBaseInfo kb = knowledgeBaseMapper.selectKnowledgeBaseByKbId(kbId);
        if (kb == null)
        {
            return;
        }

        boolean isSuper = currentUser != null
                && currentUser.getIsSuper() != null
                && currentUser.getIsSuper() == 1;

        if (kb.getIsPublicTemplate() != null && kb.getIsPublicTemplate() == 1)
        {
            // 公共模板：需要模块功能操作权限或超级账户
            boolean hasModulePerm = currentUser != null
                    && currentUser.getIsOpenModulePerm() != null
                    && currentUser.getIsOpenModulePerm() == 1;

            if (!hasModulePerm && !isSuper)
            {
                log.error("用户无权限删除公共模板，userId: {}, kbId: {}", currentUserId, kbId);
                throw new RuntimeException("无权限删除公共模板，需要模块功能操作权限或超级账户权限");
            }
        }
        else
        {
            // 非公共模板：仅创建者或超级账户可以删除
            boolean isOwner = currentUser != null
                    && knowledgeUtil.isKbOwnedByUser(currentUser.getHasKnowledgeBase(), kbId);

            if (!isSuper && !isOwner)
            {
                log.error("用户无权限删除该知识库，userId: {}, kbId: {}", currentUserId, kbId);
                throw new RuntimeException("无权限删除该知识库，只能删除自己创建的非公共模板知识库");
            }
        }
    }


}
