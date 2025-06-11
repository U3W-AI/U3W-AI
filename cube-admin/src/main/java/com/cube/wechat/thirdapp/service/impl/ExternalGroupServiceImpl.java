package com.cube.wechat.thirdapp.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.cube.common.core.domain.R;
import com.cube.common.utils.StringUtils;
import com.cube.wechat.thirdapp.constant.BasicConstant;
import com.cube.wechat.thirdapp.entiy.RmExternalGroupChat;
import com.cube.wechat.thirdapp.entiy.RmExternalGroupChatMember;
import com.cube.wechat.thirdapp.entiy.RmUserExternalSyn;
import com.cube.wechat.thirdapp.entiy.WeChatCorpUser;
import com.cube.wechat.thirdapp.mapper.RmExternalGroupChatMapper;
import com.cube.wechat.thirdapp.mapper.RmExternalGroupChatMemberMapper;
import com.cube.wechat.thirdapp.mapper.RmUserExternalSynMapper;
import com.cube.wechat.thirdapp.service.ExternalGroupService;
import com.cube.wechat.thirdapp.service.WeChatCorpUserService;
import com.cube.wechat.thirdapp.service.WeChatDataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 @author sjl
  * @Created date 2024/5/15 21:34
 */
@Service
@Transactional
@Slf4j
public class ExternalGroupServiceImpl implements ExternalGroupService {
    @Autowired
    private RmUserExternalSynMapper rmUserExternalSynMapper;
    @Autowired
    private BasicConstant constant;
    @Autowired
    private WeChatCorpUserService weChatCorpUserService;
    @Autowired
    private WeChatDataService weChatDataService;
    @Autowired
    private RmExternalGroupChatMapper rmExternalGroupChatMapper;
    @Autowired
    private RmExternalGroupChatMemberMapper rmExternalGroupChatMemberMapper;

    @Override
    public R<Map> synCorpExternalGroup(String corpId, String userId) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("corpId", corpId);
            if (StringUtils.isNotEmpty(userId)) {
                log.info("=================开始处理" + userId + "的群聊==================");
                //查询是否已处理
                RmUserExternalSyn rmUserExternalSyn = new RmUserExternalSyn();
                rmUserExternalSyn.setUserId(userId);
                rmUserExternalSyn.setCorpId(corpId);
                rmUserExternalSyn.setCorpId(corpId);
                rmUserExternalSyn.setSynType(1);
                RmUserExternalSyn userExternalSyn = rmUserExternalSynMapper.selectIsSynContractByUserId(rmUserExternalSyn);
                if (userExternalSyn != null && userExternalSyn.getIsSynContacts() == 1) {
                    log.info("=================" + userId + "的群聊已经处理，无需重复处理==================");
                    return R.ok();
                } else {
                    map.put("userId", userId);
                    rmUserExternalSyn.setId(UUID.randomUUID().toString());
                    //开始处理
                    R<Map> resultMap = synExternalGrpupListByUserId(map);
                    if (resultMap.getCode() == R.SUCCESS) {
                        //处理结束
                        rmUserExternalSyn.setIsSynContacts(1);
                        rmUserExternalSyn.setSynDate(new Date());
                        rmUserExternalSynMapper.insertSelective(rmUserExternalSyn);
                    }

                }
                log.info("=================处理" + userId + "的群聊结束==================");
            } else {
                //查询所有人员
                WeChatCorpUser weChatCorpUser = new WeChatCorpUser();
                weChatCorpUser.setCorpId(corpId);
                //不过滤状态
                //qywxCorpUser.setStatus(1);
                weChatCorpUser.setSuiteId(constant.getSuiteID());
                R<List<WeChatCorpUser>> selectCorpAllUser = weChatCorpUserService.selectAllUserByCorpId(weChatCorpUser);
                if (selectCorpAllUser.getCode() == R.SUCCESS && selectCorpAllUser.getData() != null) {
                    List<WeChatCorpUser> qywxCorpUserList = selectCorpAllUser.getData();
                    for (WeChatCorpUser corpUser : qywxCorpUserList) {
                        map.put("userId", corpUser.getUserId());
                        log.info("=================开始处理" + corpUser.getUserId() + "的群聊==================");
                        //查询是否已处理
                        RmUserExternalSyn rmUserExternalSyn = new RmUserExternalSyn();
                        rmUserExternalSyn.setUserId(corpUser.getUserId());
                        rmUserExternalSyn.setCorpId(corpId);
                        rmUserExternalSyn.setSynType(1);
                        RmUserExternalSyn userExternalSyn = rmUserExternalSynMapper.selectIsSynContractByUserId(rmUserExternalSyn);
                        if (userExternalSyn != null && userExternalSyn.getIsSynContacts() == 1) {
                            log.info("=================" + corpUser.getUserId() + "的群聊已经处理，无需重复处理==================");
                            continue;
                        } else {
                            Map<String, Object> dataMap = new HashMap<>();
                            dataMap.put("userId", corpUser.getUserId());
                            dataMap.put("corpId", corpId);
                            rmUserExternalSyn.setId(UUID.randomUUID().toString());
                            //开始处理
                            R<Map> resultR = synExternalGrpupListByUserId(dataMap);
                            if (resultR.getCode() == R.SUCCESS) {
                                rmUserExternalSyn.setSynDate(new Date());
                                rmUserExternalSyn.setIsSynContacts(1);
                                rmUserExternalSyn.setSynType(1);
                                //处理结束
                                rmUserExternalSynMapper.insertSelective(rmUserExternalSyn);
                            }
                            log.info("=================处理" + corpUser.getUserId() + "的群聊结束==================");
                            //更新处理状态
                        }
                    }
                } else {
                    return R.fail("未处理任何数据");
                }
            }
            log.info("=================群聊处理结束==================");
            return R.ok();
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("处理失败，发生异常:" + e.getMessage());
        }
    }

    @Override
    public R<Map> synExternalGrpupListByUserId(Map map) {
        try {
            String corpId = MapUtils.getString(map, "corpId");
            String userId = MapUtils.getString(map, "userId");
            List<String> userList = new ArrayList<>();
            List<RmExternalGroupChat> chatGroupList = new ArrayList<>();
            List<RmExternalGroupChatMember> chatGroupChatMemberroupList = new ArrayList<>();
            userList.add(userId);
            //删除该用户的原群聊数据
            rmExternalGroupChatMapper.deleteGroupChatByUserId(userId);
            //删除该用户的原群聊成员数据
            rmExternalGroupChatMemberMapper.deleteGroupChatMemberByUserId(userId);
            R<List<Map>> groupChatListR = weChatDataService.getGroupChatList(corpId, userList);
            if (groupChatListR.getCode() == R.SUCCESS) {
                List<Map> groupList = groupChatListR.getData();
                if (groupList != null && groupList.size() > 0) {
                    for (Map groupMap : groupList) {
                        RmExternalGroupChat rmExternalGroupChat = new RmExternalGroupChat();
                        String systemChatId = UUID.randomUUID().toString();
                        rmExternalGroupChat.setId(systemChatId);
                        String chatId = MapUtils.getString(groupMap, "chat_id");
                        Integer status = MapUtils.getInteger(groupMap, "status");
                        rmExternalGroupChat.setChatId(chatId);
                        rmExternalGroupChat.setChatStatus(status);
                        rmExternalGroupChat.setCorpId(corpId);
                        //查询群聊详情
                        R<Map> groupChatInfoR = weChatDataService.getGroupChatInfo(corpId, chatId);
                        if (groupChatInfoR.getCode() == R.SUCCESS) {
                            Map groupChatInfoMap = groupChatInfoR.getData();
                            if (groupChatInfoMap != null) {
                                //群聊名称
                                String groupChatName = MapUtils.getString(groupChatInfoMap, "name");
                                if (StringUtils.isEmpty(groupChatName)) {
                                    groupChatName = "群聊";
                                }
                                rmExternalGroupChat.setChatName(groupChatName);
                                rmExternalGroupChat.setCorpId(corpId);
                                Long createTime = MapUtils.getLong(groupChatInfoMap, "create_time");
                                //群聊创建时间
                                if (ObjectUtils.isEmpty(createTime)) {
                                    rmExternalGroupChat.setChatCreateTime(new Date());
                                } else {
                                    DateTime date = DateUtil.date(createTime * 1000);// 需要将秒数转换为毫秒数
                                    rmExternalGroupChat.setChatCreateTime(date);
                                }
                                //群主ID
                                String owner = MapUtils.getString(groupChatInfoMap, "owner");
                                rmExternalGroupChat.setChatGroupLeaderId(owner);
                                //群公告
                                String notice = MapUtils.getString(groupChatInfoMap, "notice");
                                rmExternalGroupChat.setChatNotice(notice);
                                //群成员
                                List<Map> memberList = (List<Map>) groupChatInfoMap.get("member_list");
                                if (memberList != null && memberList.size() > 0) {
                                    for (Map memberInfoMap : memberList) {
                                        RmExternalGroupChatMember rmExternalGroupChatMember = new RmExternalGroupChatMember();
                                        rmExternalGroupChatMember.setChatId(chatId);
                                        rmExternalGroupChatMember.setChatName(groupChatName);
                                        rmExternalGroupChatMember.setChatGroupLeaderId(owner);
                                        rmExternalGroupChatMember.setCorpId(corpId);
                                        rmExternalGroupChatMember.setMemberStatus(1);
                                        //群成员id
                                        String memberUserId = MapUtils.getString(memberInfoMap, "userid");
                                        rmExternalGroupChatMember.setMemberUserId(memberUserId);
                                        //成员类型。
                                        Integer memberType = MapUtils.getInteger(memberInfoMap, "type");
                                        rmExternalGroupChatMember.setMemberType(memberType);
                                        //unionid
                                        String unionid = MapUtils.getString(memberInfoMap, "unionid");
                                        rmExternalGroupChatMember.setMemberUnionid(unionid);
                                        //入群时间
                                        Long join_time = MapUtils.getLong(memberInfoMap, "join_time");
                                        if (ObjectUtils.isEmpty(join_time)) {
                                            rmExternalGroupChatMember.setMemberJoinTime(new Date());
                                        } else {
                                            DateTime date = DateUtil.date(join_time * 1000);// 需要将秒数转换为毫秒数
                                            rmExternalGroupChatMember.setMemberJoinTime(date);
                                        }
                                        //入群方式。1 - 由群成员邀请入群（直接邀请入群）
                                        //2 - 由群成员邀请入群（通过邀请链接入群）
                                        //3 - 通过扫描群二维码入群
                                        Integer join_scene = MapUtils.getInteger(memberInfoMap, "join_scene");
                                        rmExternalGroupChatMember.setMemberJoinScene(join_scene);
                                        //邀请者
                                        Map invitor = (Map) MapUtils.getObject(memberInfoMap, "invitor");
                                        if (invitor != null) {
                                            //邀请者id
                                            String invitorUserId = MapUtils.getString(invitor, "userid");
                                            rmExternalGroupChatMember.setMemberInvitorUserId(invitorUserId);
                                        }
                                        //群昵称
                                        String groupNickname = MapUtils.getString(memberInfoMap, "group_nickname");
                                        rmExternalGroupChatMember.setMemberGroupNickname(groupNickname);

                                        //名称
                                        String name = MapUtils.getString(memberInfoMap, "name");
                                        rmExternalGroupChatMember.setMemberName(name);
                                        //身份
                                        rmExternalGroupChatMember.setMemberIdentity(1);
                                        //管理员
                                        List<Map> adminList = (List<Map>) MapUtils.getObject(groupChatInfoMap, "admin_list");
                                        if (adminList != null && adminList.size() > 0) {
                                            List<Map> adminCollect = adminList.stream().filter(p -> MapUtils.getString(p, "userid").equals(memberUserId)).collect(Collectors.toList());
                                            if (adminCollect != null && adminCollect.size() > 0) {
                                                //管理员身份
                                                rmExternalGroupChatMember.setMemberIdentity(2);
                                            }
                                        }
                                        if (StringUtils.isNotEmpty(owner) && StringUtils.isNotEmpty(memberUserId) && owner.equals(memberUserId)) {
                                            //群主身份
                                            rmExternalGroupChatMember.setMemberIdentity(3);
                                        }
                                        //版本号
                                        String memberVersion = MapUtils.getString(groupChatInfoMap, "member_version");
                                        rmExternalGroupChat.setChatMemberVersion(memberVersion);
                                        rmExternalGroupChat.setChatGroupStatus(1);
                                        rmExternalGroupChatMember.setMemberVersion(memberVersion);
                                        rmExternalGroupChatMember.setSystemChatId(systemChatId);
                                        rmExternalGroupChatMember.setId(UUID.randomUUID().toString());
                                        rmExternalGroupChatMember.setCreateTime(new Date());
                                        chatGroupChatMemberroupList.add(rmExternalGroupChatMember);
                                    }
                                }
                                chatGroupList.add(rmExternalGroupChat);
                            } else {
                                continue;
                            }
                        } else {
                            continue;
                        }
                    }
                }
                if (chatGroupList != null && chatGroupList.size() > 0) {
                    //插入
                    List<List<RmExternalGroupChat>> partition = Lists.partition(chatGroupList, 2000);
                    if (partition != null && partition.size() > 0) {
                        for (List<RmExternalGroupChat> rmExternalGroupChats : partition) {
                            rmExternalGroupChatMapper.insertBatchSelective(rmExternalGroupChats);
                        }
                    }
                }
                if (chatGroupChatMemberroupList != null && chatGroupChatMemberroupList.size() > 0) {
                    List<List<RmExternalGroupChatMember>> partition = Lists.partition(chatGroupChatMemberroupList, 2000);
                    if (partition != null && partition.size() > 0) {
                        for (List<RmExternalGroupChatMember> rmExternalGroupChatMembers : partition) {
                            rmExternalGroupChatMemberMapper.insertBatchSelective(rmExternalGroupChatMembers);
                        }
                    }
                }
                return R.ok(null, "群聊处理成功");
            } else {
                return R.fail("查询群聊失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("处理群聊数据失败");
        }

    }

    @Override
    public R<Map> createExternalGroup(Map map) {
        try {
            boolean isUpdate = false;
            String corpId = MapUtils.getString(map, "corpId");
            String chatId = MapUtils.getString(map, "chatId");
            R<Map> groupChatInfo = weChatDataService.getGroupChatInfo(corpId, chatId);
            List<RmExternalGroupChatMember> chatGroupChatMemberroupList = new ArrayList<>();
            if (groupChatInfo.getCode() == R.SUCCESS) {
                RmExternalGroupChat rmExternalGroupChat = new RmExternalGroupChat();
                Map groupChatInfoMap = groupChatInfo.getData();
                if (groupChatInfoMap != null) {
                    //群聊名称
                    String groupChatName = MapUtils.getString(groupChatInfoMap, "name");
                    if (StringUtils.isEmpty(groupChatName)) {
                        groupChatName = "群聊";
                    }
                    rmExternalGroupChat.setChatName(groupChatName);
                    rmExternalGroupChat.setCorpId(corpId);
                    rmExternalGroupChat.setChatGroupStatus(1);
                    rmExternalGroupChat.setChatId(chatId);
                    rmExternalGroupChat.setChatStatus(0);
                    Long createTime = MapUtils.getLong(groupChatInfoMap, "create_time");
                    //群聊创建时间
                    if (ObjectUtils.isEmpty(createTime)) {
                        rmExternalGroupChat.setChatCreateTime(new Date());
                    } else {
                        DateTime date = DateUtil.date(createTime * 1000);// 需要将秒数转换为毫秒数
                        rmExternalGroupChat.setChatCreateTime(date);
                    }
                    //群主ID
                    String owner = MapUtils.getString(groupChatInfoMap, "owner");
                    rmExternalGroupChat.setChatGroupLeaderId(owner);
                    //群公告
                    String notice = MapUtils.getString(groupChatInfoMap, "notice");
                    rmExternalGroupChat.setChatNotice(notice);
                    String systemChatId = UUID.randomUUID().toString();
                    //群成员
                    List<Map> memberList = (List<Map>) groupChatInfoMap.get("member_list");
                    if (memberList != null && memberList.size() > 0) {
                        for (Map memberInfoMap : memberList) {
                            RmExternalGroupChatMember rmExternalGroupChatMember = new RmExternalGroupChatMember();
                            rmExternalGroupChatMember.setChatId(chatId);
                            rmExternalGroupChatMember.setChatName(groupChatName);
                            rmExternalGroupChatMember.setChatGroupLeaderId(owner);
                            rmExternalGroupChatMember.setCorpId(corpId);
                            //群成员id
                            String memberUserId = MapUtils.getString(memberInfoMap, "userid");
                            rmExternalGroupChatMember.setMemberUserId(memberUserId);
                            //成员类型。
                            Integer memberType = MapUtils.getInteger(memberInfoMap, "type");
                            rmExternalGroupChatMember.setMemberType(memberType);
                            //unionid
                            String unionid = MapUtils.getString(memberInfoMap, "unionid");
                            rmExternalGroupChatMember.setMemberUnionid(unionid);
                            //入群时间
                            Long join_time = MapUtils.getLong(memberInfoMap, "join_time");
                            if (ObjectUtils.isEmpty(join_time)) {
                                rmExternalGroupChatMember.setMemberJoinTime(new Date());
                            } else {
                                DateTime date = DateUtil.date(join_time * 1000);// 需要将秒数转换为毫秒数
                                rmExternalGroupChatMember.setMemberJoinTime(date);
                            }
                            //入群方式。1 - 由群成员邀请入群（直接邀请入群）
                            //2 - 由群成员邀请入群（通过邀请链接入群）
                            //3 - 通过扫描群二维码入群
                            Integer join_scene = MapUtils.getInteger(memberInfoMap, "join_scene");
                            rmExternalGroupChatMember.setMemberJoinScene(join_scene);
                            //邀请者
                            Map invitor = (Map) MapUtils.getObject(memberInfoMap, "invitor");
                            if (invitor != null) {
                                //邀请者id
                                String invitorUserId = MapUtils.getString(invitor, "userid");
                                rmExternalGroupChatMember.setMemberInvitorUserId(invitorUserId);
                            }
                            //群昵称
                            String groupNickname = MapUtils.getString(memberInfoMap, "group_nickname");
                            rmExternalGroupChatMember.setMemberGroupNickname(groupNickname);

                            //名称
                            String name = MapUtils.getString(memberInfoMap, "name");
                            rmExternalGroupChatMember.setMemberName(name);
                            //身份
                            rmExternalGroupChatMember.setMemberIdentity(1);
                            //管理员
                            List<Map> adminList = (List<Map>) MapUtils.getObject(groupChatInfoMap, "admin_list");
                            if (StringUtils.isNotEmpty(owner) && StringUtils.isNotEmpty(memberUserId) && owner.equals(memberUserId)) {
                                //群主身份
                                rmExternalGroupChatMember.setMemberIdentity(3);
                            }
                            if (adminList != null && adminList.size() > 0) {
                                List<Map> adminCollect = adminList.stream().filter(p -> MapUtils.getString(p, "userid").equals(memberUserId)).collect(Collectors.toList());
                                if (adminCollect != null && adminCollect.size() > 0) {
                                    //管理员身份
                                    rmExternalGroupChatMember.setMemberIdentity(2);
                                }
                            }

                            RmExternalGroupChat externalGroupChat = rmExternalGroupChatMapper.selectByChatId(chatId);
                            if (externalGroupChat != null) {
                                isUpdate = true;
                                systemChatId = externalGroupChat.getId();
                                rmExternalGroupChat.setChatUpdateTime(new Date());
                                rmExternalGroupChat.setId(systemChatId);
                            } else {
                                rmExternalGroupChatMember.setCreateTime(new Date());
                                rmExternalGroupChat.setId(systemChatId);
                            }
                            //版本号
                            String memberVersion = MapUtils.getString(groupChatInfoMap, "member_version");
                            rmExternalGroupChat.setChatMemberVersion(memberVersion);
                            rmExternalGroupChatMember.setMemberVersion(memberVersion);
                            rmExternalGroupChatMember.setSystemChatId(systemChatId);
                            rmExternalGroupChatMember.setId(UUID.randomUUID().toString());
                            rmExternalGroupChatMember.setMemberStatus(1);
                            chatGroupChatMemberroupList.add(rmExternalGroupChatMember);
                        }
                    }
                }
                //修改
                if (isUpdate) {
                    rmExternalGroupChatMapper.updateByPrimaryKeySelective(rmExternalGroupChat);
                } else {
                    //新增群聊
                    rmExternalGroupChatMapper.insertSelective(rmExternalGroupChat);
                }
                //清除原群聊成员
                rmExternalGroupChatMemberMapper.deleteGroupChatNemberByChatId(chatId);
                if (chatGroupChatMemberroupList != null && chatGroupChatMemberroupList.size() > 0) {
                    rmExternalGroupChatMemberMapper.insertBatchSelective(chatGroupChatMemberroupList);
                }
                return R.ok(null, "群聊创建成功");
            } else {
                return R.fail("群聊创建失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("群聊创建失败，发生异常");
        }
    }

    /**
     * 更新群基本信息
     * @param map
     * @return
     */
    @Override
    public R<Map> updateExternalGroup(Map map) {
        try {
            String corpId = MapUtils.getString(map, "corpId");
            String chatId = MapUtils.getString(map, "chatId");
            //查询已存在数据
            RmExternalGroupChat externalGroupChat = rmExternalGroupChatMapper.selectByChatId(chatId);
            if (externalGroupChat != null) {
                R<Map> groupChatInfo = weChatDataService.getGroupChatInfo(corpId, chatId);
                if (groupChatInfo.getCode() == R.SUCCESS) {
                    RmExternalGroupChat rmExternalGroupChat = new RmExternalGroupChat();
                    Map groupChatInfoMap = groupChatInfo.getData();
                    if (groupChatInfoMap != null) {
                        //群聊名称
                        String groupChatName = MapUtils.getString(groupChatInfoMap, "name");
                        if (StringUtils.isEmpty(groupChatName)) {
                            groupChatName = "群聊";
                        }
                        rmExternalGroupChat.setChatName(groupChatName);
                        rmExternalGroupChat.setCorpId(corpId);
                        //群公告
                        String notice = MapUtils.getString(groupChatInfoMap, "notice");
                        rmExternalGroupChat.setChatNotice(notice);
                        rmExternalGroupChat.setChatUpdateTime(new Date());
                        rmExternalGroupChat.setId(externalGroupChat.getId());
                        rmExternalGroupChatMapper.updateByPrimaryKeySelective(rmExternalGroupChat);

                        //修改成员表群聊名称
                        RmExternalGroupChatMember rmExternalGroupChatMember = new RmExternalGroupChatMember();
                        rmExternalGroupChatMember.setChatId(chatId);
                        rmExternalGroupChatMember.setCorpId(corpId);
                        rmExternalGroupChatMember.setChatName(groupChatName);
                        rmExternalGroupChatMemberMapper.updateByRmExternalGroupChatMember(rmExternalGroupChatMember);
                    }
                    return R.ok(null, "群聊更新成功");
                } else {
                    return R.fail("群聊更新失败");
                }
            } else {
                return R.fail("群聊更新失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("群聊更新失败，发生异常");
        }
    }

    /**
     * 添加群成员
     * @param map
     * @return
     */
    @Override
    public R<Map> addGrouChatMember(Map map) {
        try {
            String corpId = MapUtils.getString(map, "corpId");
            String chatId = MapUtils.getString(map, "chatId");
            List<String> userList = (List<String>) map.get("userList");
            if (userList != null && userList.size() > 0) {
                //查询已存在群聊
                RmExternalGroupChat externalGroupChat = rmExternalGroupChatMapper.selectByChatId(chatId);
                //查询企业群聊已存在成员
                List<RmExternalGroupChatMember> rmExternalGroupChatMembers = rmExternalGroupChatMemberMapper.selectChatAllMemberByChatId(chatId, corpId);
                if(CollectionUtils.isEmpty(rmExternalGroupChatMembers)){
                    rmExternalGroupChatMembers=new ArrayList<>();
                }
                if (externalGroupChat != null) {
                    R<Map> groupChatInfo = weChatDataService.getGroupChatInfo(corpId, chatId);
                    List<RmExternalGroupChatMember> insertChatGroupChatMemberroupList = new ArrayList<>();
                    List<RmExternalGroupChatMember> updateChatGroupChatMemberroupList = new ArrayList<>();
                    if (groupChatInfo.getCode() == R.SUCCESS) {
                        Map groupChatInfoMap = groupChatInfo.getData();
                        if (groupChatInfoMap != null) {
                            //群成员
                            List<Map> memberList = (List<Map>) groupChatInfoMap.get("member_list");
                            for (String userId : userList) {
                                if (memberList != null && memberList.size() > 0) {
                                    List<Map> collect = memberList.stream().filter(p -> MapUtils.getString(p, "userid").equals(userId)).collect(Collectors.toList());
                                    if (collect != null && collect.size() > 0) {
                                        Map memberInfoMap = collect.get(0);
                                        //群主ID
                                        String owner = MapUtils.getString(groupChatInfoMap, "owner");
                                        RmExternalGroupChatMember rmExternalGroupChatMember = new RmExternalGroupChatMember();
                                        rmExternalGroupChatMember.setCreateTime(new Date());
                                        rmExternalGroupChatMember.setChatId(chatId);
                                        rmExternalGroupChatMember.setChatName(externalGroupChat.getChatName());
                                        rmExternalGroupChatMember.setCorpId(corpId);
                                        rmExternalGroupChatMember.setSystemChatId(externalGroupChat.getId());
                                        rmExternalGroupChatMember.setChatGroupLeaderId(externalGroupChat.getChatGroupLeaderId());
                                        rmExternalGroupChatMember.setMemberUserId(userId);
                                        //成员类型。
                                        Integer memberType = MapUtils.getInteger(memberInfoMap, "type");
                                        rmExternalGroupChatMember.setMemberType(memberType);
                                        rmExternalGroupChatMember.setMemberStatus(1);
                                        String unionid = MapUtils.getString(memberInfoMap, "unionid");
                                        rmExternalGroupChatMember.setMemberUnionid(unionid);
                                        //入群时间
                                        Long join_time = MapUtils.getLong(memberInfoMap, "join_time");
                                        if (ObjectUtils.isEmpty(join_time)) {
                                            rmExternalGroupChatMember.setMemberJoinTime(new Date());
                                        } else {
                                            DateTime date = DateUtil.date(join_time * 1000);// 需要将秒数转换为毫秒数
                                            rmExternalGroupChatMember.setMemberJoinTime(date);
                                        }
                                        //入群方式。1 - 由群成员邀请入群（直接邀请入群）
                                        //2 - 由群成员邀请入群（通过邀请链接入群）
                                        //3 - 通过扫描群二维码入群
                                        Integer join_scene = MapUtils.getInteger(memberInfoMap, "join_scene");
                                        rmExternalGroupChatMember.setMemberJoinScene(join_scene);
                                        //邀请者
                                        Map invitor = (Map) MapUtils.getObject(memberInfoMap, "invitor");
                                        if (invitor != null) {
                                            //邀请者id
                                            String invitorUserId = MapUtils.getString(invitor, "userid");
                                            rmExternalGroupChatMember.setMemberInvitorUserId(invitorUserId);
                                        }
                                        //群昵称
                                        String groupNickname = MapUtils.getString(memberInfoMap, "group_nickname");
                                        rmExternalGroupChatMember.setMemberGroupNickname(groupNickname);
                                        //名称
                                        String name = MapUtils.getString(memberInfoMap, "name");
                                        rmExternalGroupChatMember.setMemberName(name);
                                        //身份
                                        rmExternalGroupChatMember.setMemberIdentity(1);
                                        //管理员
                                        List<Map> adminList = (List<Map>) MapUtils.getObject(groupChatInfoMap, "admin_list");
                                        if (StringUtils.isNotEmpty(owner) && StringUtils.isNotEmpty(userId) && owner.equals(userId)) {
                                            //群主身份
                                            rmExternalGroupChatMember.setMemberIdentity(3);
                                        }
                                        if (adminList != null && adminList.size() > 0) {
                                            List<Map> adminCollect = adminList.stream().filter(p -> MapUtils.getString(p, "userid").equals(userId)).collect(Collectors.toList());
                                            if (adminCollect != null && adminCollect.size() > 0) {
                                                //管理员身份
                                                rmExternalGroupChatMember.setMemberIdentity(2);
                                            }
                                        }
                                        //版本号
                                        String memberVersion = MapUtils.getString(groupChatInfoMap, "member_version");
                                        rmExternalGroupChatMember.setMemberVersion(memberVersion);
                                        rmExternalGroupChatMember.setId(UUID.randomUUID().toString());

                                        List<RmExternalGroupChatMember> collected = rmExternalGroupChatMembers.stream().filter(p -> p.getMemberUserId().equals(userId)).collect(Collectors.toList());
                                        //已存在
                                        if(collected!=null && collected.size()>0){
                                            RmExternalGroupChatMember edRmExternalGroupChatMember = collected.get(0);
                                            //修改
                                            rmExternalGroupChatMember.setId(edRmExternalGroupChatMember.getId());
                                            rmExternalGroupChatMember.setMemberStatus(1);
                                            rmExternalGroupChatMember.setUpdateTime(new Date());
                                            updateChatGroupChatMemberroupList.add(rmExternalGroupChatMember);
                                        }else{
                                            insertChatGroupChatMemberroupList.add(rmExternalGroupChatMember);
                                        }
                                    }
                                }
                            }
                        }
                        //新增
                        if (insertChatGroupChatMemberroupList != null && insertChatGroupChatMemberroupList.size() > 0) {
                            rmExternalGroupChatMemberMapper.insertBatchSelective(insertChatGroupChatMemberroupList);
                        }
                        //更新
                        if(updateChatGroupChatMemberroupList!=null && updateChatGroupChatMemberroupList.size()>0){
                            rmExternalGroupChatMemberMapper.updateBatchByPrimaryKeySelective(updateChatGroupChatMemberroupList);
                        }
                        return R.ok(null, "成员进群成功");
                    } else {
                        return R.fail("成员进群失败");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("成员进群失败,发生异常");
        }
        return R.ok();
    }

    @Override
    public R<Map> delGroupChatMember(Map map) {
        try {
            String chatId = MapUtils.getString(map, "chatId");
            Integer quitScene = MapUtils.getInteger(map, "quitScene");
            String version = MapUtils.getString(map, "version");
            List<String> userList = (List<String>) map.get("userList");
            if (userList != null && userList.size() > 0) {
                //查询群
                RmExternalGroupChat externalGroupChat = rmExternalGroupChatMapper.selectByChatId(chatId);
                if (externalGroupChat != null) {
                    for (String userId : userList) {
                        //查询群成员详情
                        RmExternalGroupChatMember rmExternalGroupChatMember = rmExternalGroupChatMemberMapper.selectChatMemberByMemberUserId(chatId, userId);
                        if (rmExternalGroupChatMember != null) {
                            RmExternalGroupChatMember externalGroupChatMember = new RmExternalGroupChatMember();
                            externalGroupChatMember.setId(rmExternalGroupChatMember.getId());
                            externalGroupChatMember.setMemberStatus(2);
                            externalGroupChatMember.setUpdateTime(new Date());
                            externalGroupChatMember.setMemberQuitTime(new Date());
                            externalGroupChatMember.setMemberQuitScene(quitScene);
                            externalGroupChatMember.setMemberVersion(version);
                            rmExternalGroupChatMemberMapper.updateByPrimaryKeySelective(externalGroupChatMember);
                        }
                        externalGroupChat.setChatMemberVersion(version);
                        externalGroupChat.setChatUpdateTime(new Date());
                        rmExternalGroupChatMapper.updateByPrimaryKeySelective(externalGroupChat);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 变更群主
     * @param map
     * @return
     */
    @Override
    public R<Map> updateGroupChatOwner(Map map) {
        try {
            String corpId = MapUtils.getString(map, "corpId");
            String chatId = MapUtils.getString(map, "chatId");
            String version = MapUtils.getString(map, "version");
            R<Map> groupChatInfo = weChatDataService.getGroupChatInfo(corpId, chatId);
            if (groupChatInfo.getCode() == R.SUCCESS) {
                Map groupChatInfoMap = groupChatInfo.getData();
                if (groupChatInfoMap != null) {
                    //群主ID
                    String owner = MapUtils.getString(groupChatInfoMap, "owner");
                    //更新群聊数据中的群主id
                    RmExternalGroupChat externalGroupChat = rmExternalGroupChatMapper.selectByChatId(chatId);
                    if (externalGroupChat != null) {
                        externalGroupChat.setChatGroupLeaderId(owner);
                        externalGroupChat.setChatUpdateTime(new Date());
                        externalGroupChat.setChatMemberVersion(version);
                        rmExternalGroupChatMapper.updateByPrimaryKeySelective(externalGroupChat);
                    }
                    //更新群成员中的群主
                    RmExternalGroupChatMember rmExternalGroupChatMember = new RmExternalGroupChatMember();
                    rmExternalGroupChatMember.setChatId(chatId);
                    rmExternalGroupChatMember.setCorpId(corpId);
                    rmExternalGroupChatMember.setChatGroupLeaderId(owner);
                    rmExternalGroupChatMember.setUpdateTime(new Date());
                    rmExternalGroupChatMemberMapper.updateByRmExternalGroupChatMember(rmExternalGroupChatMember);
                    //清除原群主
                    rmExternalGroupChatMemberMapper.clearGroupChatLeaderByChatId(rmExternalGroupChatMember);
                    //更新新群主
                    rmExternalGroupChatMember.setMemberUserId(owner);
                    rmExternalGroupChatMember.setMemberIdentity(3);
                    rmExternalGroupChatMember.setMemberVersion(version);
                    rmExternalGroupChatMemberMapper.addGroupChatLeaderByChatIdAndUserId(rmExternalGroupChatMember);
                }
            }
            return R.ok(null, "群主变更成功");
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("群主变更失败，发生异常");
        }

    }

    /**
     * 群解散
     * @param map
     * @return
     */
    @Override
    public R<Map> dismissGroupChat(Map map) {
        try {
            String chatId = MapUtils.getString(map, "chatId");
            RmExternalGroupChat externalGroupChat = rmExternalGroupChatMapper.selectByChatId(chatId);
            if (externalGroupChat != null) {
                externalGroupChat.setChatGroupStatus(2);
                externalGroupChat.setChatDismissTime(new Date());
                externalGroupChat.setChatUpdateTime(new Date());
                rmExternalGroupChatMapper.updateByPrimaryKeySelective(externalGroupChat);
            }
            return R.ok(null,"群聊解散成功");
        }catch (Exception e){
            e.printStackTrace();
            return R.fail("群聊解散失败，发生异常");
        }
    }

    @Override
    public R<Map> updateGroupChatMemberName() {
        log.info("==================================开始更新群成员昵称==============");
        try {
            //查询所有群聊
            List<RmExternalGroupChat> rmExternalGroupChatsList = rmExternalGroupChatMapper.selectAllGroupChatByCorpId("");
            if(rmExternalGroupChatsList!=null&&rmExternalGroupChatsList.size()>0){
                for (RmExternalGroupChat externalGroupChat : rmExternalGroupChatsList) {
                    String chatId = externalGroupChat.getChatId();
                    String corpId = externalGroupChat.getCorpId();
                    R<Map> groupChatInfo = weChatDataService.getGroupChatInfo(corpId, chatId);
                    if (groupChatInfo.getCode() == R.SUCCESS) {
                        Map groupChatInfoMap = groupChatInfo.getData();
                        if (groupChatInfoMap != null) {
                            //群成员
                            List<Map> memberList = (List<Map>) groupChatInfoMap.get("member_list");
                            if(memberList!=null&&memberList.size()>0){
                                for (Map memberInfoMap : memberList) {
                                    RmExternalGroupChatMember rmExternalGroupChatMember = new RmExternalGroupChatMember();
                                    //群昵称
                                    String groupNickname = MapUtils.getString(memberInfoMap, "group_nickname");
                                    if(StringUtils.isNotEmpty(groupNickname)){
                                        //群成员id
                                        String memberUserId = MapUtils.getString(memberInfoMap, "userid");
                                        rmExternalGroupChatMember.setMemberUserId(memberUserId);
                                        rmExternalGroupChatMember.setMemberGroupNickname(groupNickname);
                                        rmExternalGroupChatMember.setCorpId(corpId);
                                        rmExternalGroupChatMember.setChatId(chatId);
                                        //更新群成员群昵称
                                        rmExternalGroupChatMember.setUpdateTime(new Date());
                                        rmExternalGroupChatMemberMapper.updateByMemberUserId(rmExternalGroupChatMember);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            return R.fail("更新群成员昵称失败");
        }
        log.info("==================================更新群成员昵称结束==============");
        return R.ok();
    }
}
