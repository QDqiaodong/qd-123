package com.factory.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.factory.security.dto.FiberSpliceCommissionDTO;
import com.factory.security.dto.FiberSpliceJointCreateDTO;
import com.factory.security.dto.FiberSpliceJointUpdateDTO;
import com.factory.security.dto.FiberSpliceVoidDTO;
import com.factory.security.entity.FiberSpliceJoint;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.FiberSpliceJointMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.FiberSpliceJointService;
import com.factory.security.vo.FiberSpliceJointVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FiberSpliceJointServiceImpl
        extends ServiceImpl<FiberSpliceJointMapper, FiberSpliceJoint>
        implements FiberSpliceJointService {

    @Autowired
    private ZoneTagMapper zoneTagMapper;

    @Override
    public Page<FiberSpliceJointVO> page(Integer pageNum, Integer pageSize, String keyword,
                                         Long zoneTagId, Integer otdrPassed,
                                         Integer commissionable, Integer status) {
        Page<FiberSpliceJoint> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FiberSpliceJoint> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(FiberSpliceJoint::getSpliceNo, keyword.trim());
        }
        if (zoneTagId != null) {
            // 列表按所属分区筛
            wrapper.eq(FiberSpliceJoint::getZoneTagId, zoneTagId);
        }
        if (otdrPassed != null) {
            wrapper.eq(FiberSpliceJoint::getOtdrPassed, otdrPassed);
        }
        if (commissionable != null) {
            wrapper.eq(FiberSpliceJoint::getCommissionable, commissionable);
        }
        if (status != null) {
            wrapper.eq(FiberSpliceJoint::getStatus, status);
        }
        wrapper.orderByDesc(FiberSpliceJoint::getCreateTime);
        wrapper.orderByDesc(FiberSpliceJoint::getId);
        Page<FiberSpliceJoint> jointPage = page(page, wrapper);

        List<FiberSpliceJoint> joints = jointPage.getRecords();
        Map<Long, ZoneTag> zoneMap = loadZones(
                joints.stream().map(FiberSpliceJoint::getZoneTagId).collect(Collectors.toSet()));

        Page<FiberSpliceJointVO> voPage = new Page<>(jointPage.getCurrent(), jointPage.getSize(),
                jointPage.getTotal());
        voPage.setRecords(joints.stream()
                .map(joint -> buildVO(joint, joint.getZoneTagId() == null ? null
                        : zoneMap.get(joint.getZoneTagId())))
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FiberSpliceJointCreateDTO dto) {
        String spliceNo = StringUtils.trimWhitespace(dto.getSpliceNo());
        if (!StringUtils.hasText(spliceNo)) {
            throw new RuntimeException("请填写接头编号");
        }
        if (dto.getReserveMeters() == null || dto.getReserveMeters() < 0) {
            throw new RuntimeException("盘留米数必须为非负整数");
        }
        Integer otdrPassed = normalizeFlag(dto.getOtdrPassed(), "是否过 OTDR");
        // 同一接头编号不能登记两次：先显式判重给出明确提示，数据库 uk_splice_no 再兜底并发
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<FiberSpliceJoint>()
                .eq(FiberSpliceJoint::getSpliceNo, spliceNo));
        if (count != null && count > 0) {
            throw new RuntimeException(String.format("接头编号「%s」已登记，同一接头编号不能登记两次", spliceNo));
        }
        // 所属分区必须存在（分区标签被删后不能再用于新登记）
        ZoneTag zone = zoneTagMapper.selectById(dto.getZoneTagId());
        if (zone == null) {
            throw new RuntimeException("所属分区不存在或已删除，请重新选择分区");
        }

        FiberSpliceJoint joint = new FiberSpliceJoint();
        joint.setSpliceNo(spliceNo);
        joint.setZoneTagId(zone.getId());
        joint.setZoneName(zone.getTagName());
        joint.setReserveMeters(dto.getReserveMeters());
        joint.setOtdrPassed(otdrPassed);
        // 登记时一律不可投运：通过 OTDR 后再单独“标记可投运”，未过 OTDR 不能标可投运
        joint.setCommissionable(0);
        joint.setStatus(0);
        joint.setRemark(StringUtils.trimWhitespace(dto.getRemark()));
        try {
            save(joint);
        } catch (DuplicateKeyException e) {
            // 并发登记同一接头编号：uk_splice_no 唯一索引兜底
            throw new RuntimeException(String.format("接头编号「%s」已登记，同一接头编号不能登记两次", spliceNo));
        }
        return joint.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FiberSpliceJointUpdateDTO dto) {
        FiberSpliceJoint joint = getRequiredJoint(id);
        if (joint.getStatus() != null && joint.getStatus() == 1) {
            // 作废只留档：作废接头只读，不可再编辑
            throw new RuntimeException("该接头已作废留档，不能再编辑");
        }
        if (dto.getReserveMeters() == null || dto.getReserveMeters() < 0) {
            throw new RuntimeException("盘留米数必须为非负整数");
        }
        Integer otdrPassed = normalizeFlag(dto.getOtdrPassed(), "是否过 OTDR");
        boolean currentlyCommissionable = joint.getCommissionable() != null
                && joint.getCommissionable() == 1;
        if (otdrPassed == 0 && currentlyCommissionable) {
            // 维持“可投运 ⇒ 已过 OTDR”恒成立：已标记可投运的接头不能改成未过 OTDR，
            // 需先取消可投运标记，再改 OTDR 结果
            throw new RuntimeException("该接头已标记可投运，不能改为未过 OTDR；请先取消可投运标记");
        }
        ZoneTag zone = zoneTagMapper.selectById(dto.getZoneTagId());
        if (zone == null) {
            throw new RuntimeException("所属分区不存在或已删除，请重新选择分区");
        }

        joint.setZoneTagId(zone.getId());
        joint.setZoneName(zone.getTagName());
        joint.setReserveMeters(dto.getReserveMeters());
        joint.setOtdrPassed(otdrPassed);
        joint.setRemark(StringUtils.trimWhitespace(dto.getRemark()));
        updateById(joint);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commission(Long id, FiberSpliceCommissionDTO dto) {
        Integer commissionable = normalizeFlag(dto == null ? null : dto.getCommissionable(), "可投运标记");
        FiberSpliceJoint joint = getRequiredJoint(id);
        if (joint.getStatus() != null && joint.getStatus() == 1) {
            throw new RuntimeException("该接头已作废留档，不能再标记可投运");
        }
        if (commissionable == 1) {
            // 未过 OTDR 不能标可投运：Service 层再次强制，不信任前端
            if (joint.getOtdrPassed() == null || joint.getOtdrPassed() != 1) {
                throw new RuntimeException("该接头尚未通过 OTDR，不能标记可投运；请先补做 OTDR 测试");
            }
        }

        FiberSpliceJoint update = new FiberSpliceJoint();
        update.setId(id);
        update.setCommissionable(commissionable);
        updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidJoint(Long id, FiberSpliceVoidDTO dto) {
        FiberSpliceJoint joint = getRequiredJoint(id);
        if (joint.getStatus() != null && joint.getStatus() == 1) {
            throw new RuntimeException("该接头已作废，不能重复作废");
        }

        // 作废只置状态留档，不做任何物理删除：行仍在 fiber_splice_joint 表中，
        // 列表筛选“已作废”可随时查档；作废后接头只读
        FiberSpliceJoint update = new FiberSpliceJoint();
        update.setId(id);
        update.setStatus(1);
        update.setVoidReason(dto == null ? null : StringUtils.trimWhitespace(dto.getReason()));
        update.setVoidTime(LocalDateTime.now());
        updateById(update);
    }

    // -------------------- 辅助方法 --------------------

    private Integer normalizeFlag(Integer value, String fieldName) {
        if (value == null || (value != 0 && value != 1)) {
            throw new RuntimeException(fieldName + "取值非法");
        }
        return value;
    }

    private FiberSpliceJoint getRequiredJoint(Long id) {
        FiberSpliceJoint joint = getById(id);
        if (joint == null) {
            throw new RuntimeException("光纤熔接接头不存在");
        }
        return joint;
    }

    private Map<Long, ZoneTag> loadZones(Set<Long> zoneIds) {
        if (zoneIds == null || zoneIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return zoneTagMapper.selectBatchIds(zoneIds).stream()
                .collect(Collectors.toMap(ZoneTag::getId, Function.identity(), (a, b) -> a));
    }

    private FiberSpliceJointVO buildVO(FiberSpliceJoint joint, ZoneTag zone) {
        FiberSpliceJointVO vo = new FiberSpliceJointVO();
        vo.setId(joint.getId());
        vo.setSpliceNo(joint.getSpliceNo());
        vo.setZoneTagId(joint.getZoneTagId());
        // 分区名称以登记时快照为准：分区标签事后被删/改名，档案仍按快照展示
        vo.setZoneName(joint.getZoneName());
        vo.setZoneDeleted(zone == null && joint.getZoneTagId() != null);
        vo.setReserveMeters(joint.getReserveMeters());
        vo.setOtdrPassed(joint.getOtdrPassed());
        vo.setOtdrPassedText(joint.getOtdrPassed() != null && joint.getOtdrPassed() == 1
                ? "已过 OTDR" : "未过 OTDR");
        vo.setCommissionable(joint.getCommissionable());
        vo.setCommissionableText(joint.getCommissionable() != null && joint.getCommissionable() == 1
                ? "可投运" : "不可投运");
        vo.setStatus(joint.getStatus());
        vo.setStatusText(joint.getStatus() != null && joint.getStatus() == 1 ? "已作废" : "在档");
        vo.setVoidReason(joint.getVoidReason());
        vo.setVoidTime(joint.getVoidTime());
        vo.setRemark(joint.getRemark());
        vo.setCreateTime(joint.getCreateTime());
        vo.setUpdateTime(joint.getUpdateTime());
        return vo;
    }
}
