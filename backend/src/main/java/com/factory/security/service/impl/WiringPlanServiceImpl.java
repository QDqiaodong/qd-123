package com.factory.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.factory.security.dto.WiringPlanDTO;
import com.factory.security.dto.WiringPlanDetailDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.WiringPlan;
import com.factory.security.entity.WiringPlanDetail;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.WiringPlanDetailMapper;
import com.factory.security.mapper.WiringPlanMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.WiringPlanService;
import com.factory.security.vo.WiringPlanDetailVO;
import com.factory.security.vo.WiringPlanVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WiringPlanServiceImpl extends ServiceImpl<WiringPlanMapper, WiringPlan> implements WiringPlanService {

    @Autowired
    private WiringPlanDetailMapper wiringPlanDetailMapper;

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Autowired
    private ZoneTagMapper zoneTagMapper;

    @Override
    public Page<WiringPlanVO> page(Integer pageNum, Integer pageSize, String keyword, Integer status) {
        Page<WiringPlan> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WiringPlan> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(WiringPlan::getPlanName, keyword)
                    .or().like(WiringPlan::getScene, keyword));
        }

        if (status != null) {
            wrapper.eq(WiringPlan::getStatus, status);
        }

        wrapper.orderByDesc(WiringPlan::getCreateTime);
        Page<WiringPlan> planPage = page(page, wrapper);

        Page<WiringPlanVO> voPage = new Page<>(planPage.getCurrent(), planPage.getSize(), planPage.getTotal());
        List<WiringPlanVO> voList = planPage.getRecords().stream().map(plan -> {
            WiringPlanVO vo = new WiringPlanVO();
            BeanUtils.copyProperties(plan, vo);
            vo.setDetailCount(countDetails(plan.getId()));
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public WiringPlanVO getDetailById(Long id) {
        WiringPlan plan = getById(id);
        if (plan == null) {
            throw new RuntimeException("布线方案不存在");
        }

        WiringPlanVO vo = new WiringPlanVO();
        BeanUtils.copyProperties(plan, vo);

        List<WiringPlanDetailVO> details = listDetailVOs(id);
        vo.setDetails(details);
        vo.setDetailCount(details.size());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean add(WiringPlanDTO dto) {
        validateDetails(dto.getDetails());

        WiringPlan plan = new WiringPlan();
        BeanUtils.copyProperties(dto, plan);
        plan.setId(null);
        boolean result = save(plan);
        if (result) {
            saveDetails(plan.getId(), dto.getDetails());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(WiringPlanDTO dto) {
        if (dto.getId() == null || getById(dto.getId()) == null) {
            throw new RuntimeException("布线方案不存在");
        }
        validateDetails(dto.getDetails());

        WiringPlan plan = new WiringPlan();
        BeanUtils.copyProperties(dto, plan);
        boolean result = updateById(plan);
        if (result) {
            LambdaQueryWrapper<WiringPlanDetail> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(WiringPlanDetail::getPlanId, dto.getId());
            wiringPlanDetailMapper.delete(wrapper);
            saveDetails(dto.getId(), dto.getDetails());
        }
        return result;
    }

    @Override
    public boolean delete(Long id) {
        int count = countDetails(id);
        if (count > 0) {
            throw new RuntimeException("该方案存在关联配件明细，无法直接删除，请先移除方案内的配件明细");
        }
        return removeById(id);
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new RuntimeException("启用状态不合法");
        }
        WiringPlan existing = getById(id);
        if (existing == null) {
            throw new RuntimeException("布线方案不存在或已被删除");
        }
        WiringPlan plan = new WiringPlan();
        plan.setId(id);
        plan.setStatus(status);
        boolean result = updateById(plan);
        if (!result) {
            throw new RuntimeException("布线方案状态更新失败，请刷新后重试");
        }
        return true;
    }

    private int countDetails(Long planId) {
        LambdaQueryWrapper<WiringPlanDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WiringPlanDetail::getPlanId, planId);
        return Math.toIntExact(wiringPlanDetailMapper.selectCount(wrapper));
    }

    private List<WiringPlanDetailVO> listDetailVOs(Long planId) {
        LambdaQueryWrapper<WiringPlanDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WiringPlanDetail::getPlanId, planId);
        List<WiringPlanDetail> details = wiringPlanDetailMapper.selectList(wrapper);
        if (details.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> accessoryIds = details.stream()
                .map(WiringPlanDetail::getAccessoryId)
                .collect(Collectors.toSet());
        Map<Long, Accessory> accessoryMap = accessoryMapper.selectBatchIds(accessoryIds).stream()
                .collect(Collectors.toMap(Accessory::getId, Function.identity()));

        Set<Long> zoneTagIds = accessoryMap.values().stream()
                .map(Accessory::getZoneTagId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ZoneTag> zoneTagMap = zoneTagIds.isEmpty()
                ? Collections.emptyMap()
                : zoneTagMapper.selectBatchIds(zoneTagIds).stream()
                        .collect(Collectors.toMap(ZoneTag::getId, Function.identity()));

        return details.stream().map(detail -> {
            WiringPlanDetailVO vo = new WiringPlanDetailVO();
            BeanUtils.copyProperties(detail, vo);
            Accessory accessory = accessoryMap.get(detail.getAccessoryId());
            if (accessory != null) {
                vo.setAccessoryName(accessory.getAccessoryName());
                vo.setModel(accessory.getModel());
                vo.setSpecUnit(accessory.getSpecUnit());
                vo.setZoneTagId(accessory.getZoneTagId());
                ZoneTag zoneTag = zoneTagMap.get(accessory.getZoneTagId());
                if (zoneTag != null) {
                    vo.setZoneTagName(zoneTag.getTagName());
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private void validateDetails(List<WiringPlanDetailDTO> details) {
        if (details == null || details.isEmpty()) {
            return;
        }

        Set<Long> accessoryIds = new HashSet<>();
        for (WiringPlanDetailDTO detail : details) {
            if (detail.getAccessoryId() == null) {
                throw new RuntimeException("配件不能为空");
            }
            if (detail.getQuantity() == null || detail.getQuantity() < 1) {
                throw new RuntimeException("需求数量必须为正整数");
            }
            if (!accessoryIds.add(detail.getAccessoryId())) {
                throw new RuntimeException("同一方案内配件不可重复");
            }
        }

        List<Accessory> accessories = accessoryMapper.selectBatchIds(accessoryIds);
        if (accessories.size() != accessoryIds.size()) {
            throw new RuntimeException("所选配件不存在或已删除");
        }
    }

    private void saveDetails(Long planId, List<WiringPlanDetailDTO> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (WiringPlanDetailDTO dto : details) {
            WiringPlanDetail detail = new WiringPlanDetail();
            detail.setPlanId(planId);
            detail.setAccessoryId(dto.getAccessoryId());
            detail.setQuantity(dto.getQuantity());
            wiringPlanDetailMapper.insert(detail);
        }
    }
}
