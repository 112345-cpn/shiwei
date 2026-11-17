package com.shiwei.merchant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiwei.auth.entity.User;
import com.shiwei.auth.mapper.UserMapper;
import com.shiwei.common.enums.RoleType;
import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;
import com.shiwei.common.security.TokenStore;
import com.shiwei.merchant.dto.MerchantApplyForm;
import com.shiwei.merchant.dto.MerchantAuditForm;
import com.shiwei.merchant.entity.Merchant;
import com.shiwei.merchant.entity.MerchantStaff;
import com.shiwei.merchant.mapper.MerchantMapper;
import com.shiwei.merchant.mapper.MerchantStaffMapper;
import com.shiwei.merchant.vo.MerchantVO;
import com.shiwei.merchant.vo.StaffVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantMapper merchantMapper;
    private final MerchantStaffMapper staffMapper;
    private final UserMapper userMapper;
    private final TokenStore tokenStore;

    @Transactional(rollbackFor = Exception.class)
    public Long apply(Long userId, MerchantApplyForm form) {
        Long exists = merchantMapper.selectCount(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getLicenseNo, form.getLicenseNo()));
        if (exists != null && exists > 0) {
            throw BizException.of(ErrorCode.MERCHANT_LICENSE_EXISTS);
        }

        Long mine = merchantMapper.selectCount(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getOwnerUserId, userId)
                .ne(Merchant::getAuditStatus, 2));
        if (mine != null && mine > 0) {
            throw BizException.of(ErrorCode.MERCHANT_AUDIT_PENDING);
        }

        Merchant m = new Merchant();
        m.setOwnerUserId(userId);
        m.setName(form.getName());
        m.setLicenseNo(form.getLicenseNo());
        m.setLicenseImg(form.getLicenseImg());
        m.setContactName(form.getContactName());
        m.setContactPhone(form.getContactPhone());
        m.setAuditStatus(0);
        m.setAuditRemark("");
        merchantMapper.insert(m);

        return m.getId();
    }

    /**
     * 平台审核。通过时把用户角色升成商家并补一条 owner 员工记录，
     * 同时刷新他所有在线会话里的角色，否则要等 token 过期才生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long adminId, MerchantAuditForm form) {
        Merchant m = merchantMapper.selectById(form.getMerchantId());
        if (m == null) {
            throw BizException.of(ErrorCode.MERCHANT_NOT_FOUND);
        }
        if (m.getAuditStatus() != null && m.getAuditStatus() != 0) {
            throw BizException.of(ErrorCode.MERCHANT_AUDIT_PENDING, "该商家已审核过");
        }

        m.setAuditStatus(Boolean.TRUE.equals(form.getPass()) ? 1 : 2);
        m.setAuditRemark(form.getRemark() == null ? "" : form.getRemark());
        m.setAuditTime(LocalDateTime.now());
        merchantMapper.updateById(m);

        if (!Boolean.TRUE.equals(form.getPass())) {
            return;
        }

        User owner = userMapper.selectById(m.getOwnerUserId());
        if (owner != null && (owner.getRole() == null || owner.getRole() == RoleType.CONSUMER.getCode())) {
            owner.setRole(RoleType.MERCHANT.getCode());
            userMapper.updateById(owner);
            tokenStore.refreshRole(owner.getId(), RoleType.MERCHANT.getCode());
        }

        Long staffExists = staffMapper.selectCount(Wrappers.<MerchantStaff>lambdaQuery()
                .eq(MerchantStaff::getMerchantId, m.getId())
                .eq(MerchantStaff::getUserId, m.getOwnerUserId()));
        if (staffExists == null || staffExists == 0) {
            MerchantStaff staff = new MerchantStaff();
            staff.setMerchantId(m.getId());
            staff.setUserId(m.getOwnerUserId());
            staff.setStaffRole(0);
            staff.setShopId(0L);
            staff.setStatus(0);
            staffMapper.insert(staff);
        }
    }

    public List<MerchantVO> pending() {
        List<Merchant> list = merchantMapper.selectList(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getAuditStatus, 0)
                .orderByAsc(Merchant::getCreateTime));
        return list.stream().map(MerchantVO::of).toList();
    }

    public MerchantVO my(Long userId) {
        Merchant m = merchantMapper.selectOne(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getOwnerUserId, userId)
                .ne(Merchant::getAuditStatus, 2)
                .last("limit 1"));
        return m == null ? null : MerchantVO.of(m);
    }

    public Merchant requireMine(Long userId) {
        Merchant m = merchantMapper.selectOne(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getOwnerUserId, userId)
                .eq(Merchant::getAuditStatus, 1)
                .last("limit 1"));
        if (m == null) {
            throw BizException.of(ErrorCode.MERCHANT_NOT_OWNER);
        }
        return m;
    }

    /**
     * 取当前用户能管理的商家。员工表里有记录也算，方便运营和核销员进后台。
     */
    public Long merchantIdOf(Long userId) {
        MerchantStaff staff = staffMapper.selectOne(Wrappers.<MerchantStaff>lambdaQuery()
                .eq(MerchantStaff::getUserId, userId)
                .eq(MerchantStaff::getStatus, 0)
                .last("limit 1"));
        if (staff != null) {
            return staff.getMerchantId();
        }
        Merchant m = merchantMapper.selectOne(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getOwnerUserId, userId)
                .eq(Merchant::getAuditStatus, 1)
                .last("limit 1"));
        return m == null ? null : m.getId();
    }

    public void requireMerchantAccess(Long userId, Long merchantId) {
        Long mine = merchantIdOf(userId);
        if (mine == null || !mine.equals(merchantId)) {
            throw BizException.of(ErrorCode.MERCHANT_NOT_OWNER);
        }
    }

    public List<StaffVO> staffList(Long merchantId) {
        List<MerchantStaff> staffs = staffMapper.selectList(Wrappers.<MerchantStaff>lambdaQuery()
                .eq(MerchantStaff::getMerchantId, merchantId)
                .eq(MerchantStaff::getStatus, 0));
        if (staffs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = staffs.stream().map(MerchantStaff::getUserId).toList();
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return staffs.stream().map(s -> {
            User u = users.get(s.getUserId());
            return new StaffVO(s.getId(), s.getUserId(),
                    u == null ? "" : u.getNickName(),
                    u == null ? "" : u.getPhone(),
                    s.getStaffRole(), s.getShopId());
        }).toList();
    }

    public void addStaff(Long merchantId, String phone, Integer staffRole, Long shopId) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone));
        if (user == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        Long exists = staffMapper.selectCount(Wrappers.<MerchantStaff>lambdaQuery()
                .eq(MerchantStaff::getMerchantId, merchantId)
                .eq(MerchantStaff::getUserId, user.getId()));
        if (exists != null && exists > 0) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "该用户已是商家员工");
        }

        MerchantStaff staff = new MerchantStaff();
        staff.setMerchantId(merchantId);
        staff.setUserId(user.getId());
        staff.setStaffRole(staffRole == null ? 1 : staffRole);
        staff.setShopId(shopId == null ? 0L : shopId);
        staff.setStatus(0);
        staffMapper.insert(staff);

        if (user.getRole() == null || user.getRole() == RoleType.CONSUMER.getCode()) {
            user.setRole(RoleType.MERCHANT.getCode());
            userMapper.updateById(user);
            tokenStore.refreshRole(user.getId(), RoleType.MERCHANT.getCode());
        }
    }

    public void removeStaff(Long merchantId, Long staffId) {
        MerchantStaff staff = staffMapper.selectById(staffId);
        if (staff == null || !staff.getMerchantId().equals(merchantId)) {
            throw BizException.of(ErrorCode.NOT_FOUND);
        }
        if (staff.getStaffRole() != null && staff.getStaffRole() == 0) {
            throw BizException.of(ErrorCode.FORBIDDEN, "负责人不能被移除");
        }
        staff.setStatus(1);
        staffMapper.updateById(staff);
    }
}
