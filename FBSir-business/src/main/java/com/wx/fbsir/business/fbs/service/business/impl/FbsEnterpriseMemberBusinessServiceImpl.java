package com.wx.fbsir.business.fbs.service.business.impl;

import com.wx.fbsir.business.fbs.domain.entity.*;
import com.wx.fbsir.business.fbs.dto.business.enterprise.*;
import com.wx.fbsir.business.fbs.mapper.*;
import com.wx.fbsir.business.fbs.service.business.IFbsEnterpriseMemberBusinessService;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import com.wx.fbsir.system.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 企业成员管理BusinessService实现
 *
 * @author FBSir
 * @date 2026-04-09
 */
@Service
public class FbsEnterpriseMemberBusinessServiceImpl implements IFbsEnterpriseMemberBusinessService {

    @Autowired
    private FbsEnterpriseMemberMapper memberMapper;

    @Autowired
    private FbsEnterpriseMapper enterpriseMapper;

    @Autowired
    private FbsEnterprisePackMapper enterprisePackMapper;

    @Autowired
    private FbsMemberPackMapper memberPackMapper;

    @Autowired(required = false)
    private SysUserMapper sysUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addMember(EnterpriseMemberAddRequest request, String createdBy) {
        // 1. 检查企业存在且正常
        FbsEnterprise enterprise = enterpriseMapper.selectById(request.getEnterpriseId());
        if (enterprise == null) {
            throw new IllegalArgumentException("企业不存在");
        }
        if (enterprise.getStatus() != 1) {
            throw new IllegalArgumentException("企业状态异常，无法添加成员");
        }

        // 2. 检查用户存在（sys_user）
        if (sysUserMapper != null) {
            SysUser user = sysUserMapper.selectUserById(request.getUserId());
            if (user == null) {
                throw new IllegalArgumentException("用户不存在");
            }
        }

        // 3. 检查是否已是该企业成员（status=1 不可重复添加）
        FbsEnterpriseMember existing = memberMapper.selectByEnterpriseAndUser(
            request.getEnterpriseId(), request.getUserId());
        if (existing != null && existing.getStatus() != null && existing.getStatus() == 1) {
            throw new IllegalArgumentException("该用户已是企业成员");
        }

        // 4. 确定角色（默认 MEMBER）
        String role = StringUtils.hasText(request.getRole()) ? request.getRole() : "MEMBER";

        // 5. 新增或重新激活成员记录
        Long memberId;
        if (existing != null && existing.getStatus() != null && existing.getStatus() == 2) {
            // 已移除 → 重新激活
            existing.setStatus(1);
            existing.setRole(role);
            existing.setJoinTime(new Date());
            existing.setUpdatedBy(createdBy);
            memberMapper.updateMember(existing);
            memberId = existing.getId();
        } else {
            // 新增
            FbsEnterpriseMember member = new FbsEnterpriseMember();
            member.setEnterpriseId(request.getEnterpriseId());
            member.setUserId(request.getUserId());
            member.setRole(role);
            member.setJoinTime(new Date());
            member.setStatus(1); // 正常
            member.setCreatedBy(createdBy);
            memberMapper.insertMember(member);
            memberId = member.getId();
        }

        // 6. 自动继承企业当前所有已授权场景包
        List<FbsEnterprisePack> activePacks = enterprisePackMapper.selectByEnterpriseId(request.getEnterpriseId())
            .stream()
            .filter(p -> p.getStatus() != null && p.getStatus() == 1)
            .collect(Collectors.toList());

        if (!activePacks.isEmpty()) {
            List<FbsMemberPack> memberPacks = activePacks.stream().map(pack -> {
                FbsMemberPack mp = new FbsMemberPack();
                mp.setMemberId(memberId);
                mp.setEnterprisePackId(pack.getId());
                mp.setPackId(pack.getPackId());
                mp.setGrantTime(new Date());
                mp.setExpiryTime(pack.getExpiryTime());
                mp.setStatus(1); // 正常
                mp.setCreatedBy(createdBy);
                return mp;
            }).collect(Collectors.toList());
            memberPackMapper.insertMemberPackBatch(memberPacks);
        }

        return memberId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeMember(Long memberId, String updatedBy) {
        FbsEnterpriseMember existing = memberMapper.selectById(memberId);
        if (existing == null) {
            return false;
        }
        // Fail-Closed：仅 status=1 可移除
        if (existing.getStatus() == null || existing.getStatus() != 1) {
            return false;
        }

        FbsEnterpriseMember member = new FbsEnterpriseMember();
        member.setId(memberId);
        member.setStatus(2); // 已移除
        member.setUpdatedBy(updatedBy);

        return memberMapper.updateMember(member) > 0;
        // 成员 pack 授权记录保留，不级联删除（消费时通过成员状态 fail-closed）
    }

    @Override
    public List<FbsEnterpriseMember> getMemberPage(Long enterpriseId) {
        FbsEnterpriseMember filter = new FbsEnterpriseMember();
        filter.setEnterpriseId(enterpriseId);
        return memberMapper.selectMemberList(filter);
    }

    @Override
    public EnterpriseMemberDetailResponse getMemberDetail(Long memberId) {
        FbsEnterpriseMember member = memberMapper.selectById(memberId);
        if (member == null) {
            return null;
        }

        FbsEnterprise enterprise = enterpriseMapper.selectById(member.getEnterpriseId());

        // 查询用户名
        String userName = null;
        if (sysUserMapper != null) {
            SysUser user = sysUserMapper.selectUserById(member.getUserId());
            if (user != null) {
                userName = user.getUserName();
            }
        }

        // 查询成员已授权包数量
        List<FbsMemberPack> memberPacks = memberPackMapper.selectByMemberId(memberId);
        long activePackCount = memberPacks.stream()
            .filter(p -> p.getStatus() != null && p.getStatus() == 1)
            .count();

        EnterpriseMemberDetailResponse resp = new EnterpriseMemberDetailResponse();
        resp.setId(member.getId());
        resp.setEnterpriseId(member.getEnterpriseId());
        resp.setEnterpriseName(enterprise != null ? enterprise.getEnterpriseName() : null);
        resp.setUserId(member.getUserId());
        resp.setUserName(userName);
        resp.setRole(member.getRole());
        resp.setRoleDesc(resolveRoleDesc(member.getRole()));
        resp.setJoinTime(member.getJoinTime() != null ? member.getJoinTime().toString() : null);
        resp.setStatus(member.getStatus());
        resp.setStatusDesc(resolveStatusDesc(member.getStatus()));
        resp.setPackCount((int) activePackCount);
        return resp;
    }

    // ---- 私有辅助 ----
    private static String resolveRoleDesc(String role) {
        if (role == null) return null;
        return switch (role) {
            case "ADMIN" -> "管理员";
            case "MEMBER" -> "普通成员";
            default -> role;
        };
    }

    private static String resolveStatusDesc(Integer status) {
        if (status == null) return null;
        return switch (status) {
            case 1 -> "正常";
            case 2 -> "已移除";
            default -> status.toString();
        };
    }
}
