package com.factory.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.factory.security.dto.CableReelCreateDTO;
import com.factory.security.dto.CableReelDeductDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.CableReel;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.CableReelMapper;
import com.factory.security.service.CableReelService;
import com.factory.security.vo.CableReelVO;
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
public class CableReelServiceImpl extends ServiceImpl<CableReelMapper, CableReel> implements CableReelService {

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Override
    public Page<CableReelVO> page(Integer pageNum, Integer pageSize, String keyword,
                                  Integer status, Long accessoryId) {
        Page<CableReel> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<CableReel> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(CableReel::getReelNo, keyword.trim());
        }
        if (status != null) {
            wrapper.eq(CableReel::getStatus, status);
        }
        if (accessoryId != null) {
            wrapper.eq(CableReel::getAccessoryId, accessoryId);
        }
        wrapper.orderByDesc(CableReel::getCreateTime);
        wrapper.orderByDesc(CableReel::getId);
        Page<CableReel> reelPage = page(page, wrapper);

        List<CableReel> reels = reelPage.getRecords();
        // 含已删除配件一起查：盘绑定的配件被删后盘档案仍要展示，只是一致性标记为“已删除”
        Map<Long, Accessory> accessoryMap = loadAccessoriesIncludingDeleted(
                reels.stream().map(CableReel::getAccessoryId).collect(Collectors.toSet()));

        Page<CableReelVO> voPage = new Page<>(reelPage.getCurrent(), reelPage.getSize(), reelPage.getTotal());
        voPage.setRecords(reels.stream()
                .map(reel -> buildVO(reel, accessoryMap.get(reel.getAccessoryId())))
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CableReelCreateDTO dto) {
        String reelNo = StringUtils.trimWhitespace(dto.getReelNo());
        if (!StringUtils.hasText(reelNo)) {
            throw new RuntimeException("请填写盘号");
        }
        if (dto.getRemainingMeters() == null || dto.getRemainingMeters() < 0) {
            throw new RuntimeException("盘上剩余米数必须为非负整数");
        }
        // 同一盘号不能建两次：先显式判重给出明确提示，数据库 uk_reel_no 再兜底并发
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<CableReel>()
                .eq(CableReel::getReelNo, reelNo));
        if (count != null && count > 0) {
            throw new RuntimeException(String.format("盘号「%s」已建档，同一盘号不能建两次", reelNo));
        }
        // 绑定配件必须存在且未删除（MyBatis-Plus 逻辑删除自动过滤已删除配件）
        Accessory accessory = accessoryMapper.selectById(dto.getAccessoryId());
        if (accessory == null) {
            throw new RuntimeException("绑定的配件不存在或已删除，无法建档");
        }

        CableReel reel = new CableReel();
        reel.setReelNo(reelNo);
        reel.setAccessoryId(accessory.getId());
        reel.setAccessoryName(accessory.getAccessoryName());
        reel.setModel(accessory.getModel());
        reel.setSpecUnit(accessory.getSpecUnit());
        reel.setRemainingMeters(dto.getRemainingMeters());
        reel.setStatus(0);
        try {
            save(reel);
        } catch (DuplicateKeyException e) {
            // 并发建档同一盘号：uk_reel_no 唯一索引兜底
            throw new RuntimeException(String.format("盘号「%s」已建档，同一盘号不能建两次", reelNo));
        }
        return reel.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void open(Long id) {
        CableReel reel = getRequiredReel(id);
        if (reel.getStatus() != null && reel.getStatus() == 1) {
            throw new RuntimeException("该盘已开盘确认，不能重复开盘");
        }
        Accessory accessory = accessoryMapper.selectById(reel.getAccessoryId());
        if (accessory == null) {
            throw new RuntimeException("绑定的配件已删除，无法开盘确认，请改绑正常配件后重试");
        }
        // 同一配件同时只允许一个已开盘（打开的盘正是该配件在档案里的米数来源）：
        // 先显式判重给出明确提示，数据库 uk_open_accessory 唯一索引再兜底并发
        Long openedCount = baseMapper.selectCount(new LambdaQueryWrapper<CableReel>()
                .eq(CableReel::getAccessoryId, reel.getAccessoryId())
                .eq(CableReel::getStatus, 1)
                .ne(CableReel::getId, id));
        if (openedCount != null && openedCount > 0) {
            throw new RuntimeException(String.format(
                    "配件「%s」已有一个开盘确认的盘，同一配件同时只能开一个盘，请先扣完并处理后再开盘",
                    accessory.getAccessoryName()));
        }
        int currentStock = accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity();
        if (currentStock != 0) {
            // 开盘后盘上剩余即配件档案米数（扣米两边等量同步）。档案已有其他来源米数时开盘，
            // 会让“盘上剩余 == 档案米数”恒不成立，故拒绝，保证刷新后二者必然一致
            throw new RuntimeException(String.format(
                    "配件「%s」档案现存 %d 米（含其他来源米数），开盘后盘上剩余必须与档案米数一致，"
                            + "请先把档案米数清零或改绑尚无库存的配件后再开盘",
                    accessory.getAccessoryName(), currentStock));
        }

        // 先条件置为已开盘：借助 uk_open_accessory 唯一索引抢占“该配件当前已开盘”的唯一名额，
        // 两个盘对同一配件并发开盘时只有一个成功；失败方整体回滚，不发生米数入账
        UpdateWrapper<CableReel> openWrapper = new UpdateWrapper<>();
        openWrapper.eq("id", id)
                .eq("status", 0)
                .set("status", 1)
                .set("open_time", LocalDateTime.now());
        int affected;
        try {
            affected = baseMapper.update(null, openWrapper);
        } catch (DuplicateKeyException e) {
            throw new RuntimeException(String.format(
                    "配件「%s」已有一个开盘确认的盘，同一配件同时只能开一个盘，请先扣完并处理后再开盘",
                    reel.getAccessoryName()));
        }
        if (affected != 1) {
            throw new RuntimeException("该盘已开盘确认，不能重复开盘");
        }

        // 抢占成功后把整盘米数一次性入账到配件档案现存量；档案此前为 0，入账后档案米数 == 盘上剩余，
        // 此后每次扣米两边等量同步，刷新后二者恒一致。
        // 极端情况下建档后配件才被删除：入账 0 行，抛错回滚开盘（盘回到未开盘，不污染库存）
        int added = accessoryMapper.addStock(reel.getAccessoryId(), reel.getRemainingMeters());
        if (added != 1) {
            throw new RuntimeException("绑定的配件已删除，无法开盘确认，请改绑正常配件后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deduct(Long id, CableReelDeductDTO dto) {
        Integer meters = dto == null ? null : dto.getMeters();
        if (meters == null || meters < 1) {
            throw new RuntimeException("扣减米数必须为正整数");
        }
        CableReel reel = getRequiredReel(id);
        if (reel.getStatus() == null || reel.getStatus() != 1) {
            // 没开过的盘不能拿去扣米
            throw new RuntimeException("该盘尚未开盘确认，不能扣米，请先开盘确认");
        }
        if (reel.getRemainingMeters() == null || reel.getRemainingMeters() < meters) {
            throw new RuntimeException(String.format(
                    "盘上剩余 %d 米，不足扣减 %d 米",
                    reel.getRemainingMeters() == null ? 0 : reel.getRemainingMeters(), meters));
        }
        Accessory accessory = accessoryMapper.selectById(reel.getAccessoryId());
        if (accessory == null) {
            throw new RuntimeException("绑定的配件已删除，无法扣米");
        }
        int accessoryStock = accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity();
        if (accessoryStock < meters) {
            // 开盘后盘与档案同源，正常不会出现；一旦档案被盘点等外部调整拉低则拒绝，避免扣成负数
            throw new RuntimeException(String.format(
                    "配件「%s」档案现存 %d 米，不足扣减 %d 米，请核对后重试",
                    accessory.getAccessoryName(), accessoryStock, meters));
        }

        // 先扣盘上剩余：条件 status=1 且剩余充足，行级更新防并发扣成负数
        int reelAffected = baseMapper.deductMeters(id, meters);
        if (reelAffected != 1) {
            throw new RuntimeException("盘上剩余米数已变动或该盘未开盘，请刷新后重试");
        }
        // 同事务同步扣减配件档案现存量：开盘后两处必须保持一致，任一失败整体回滚
        int accessoryAffected = accessoryMapper.deductStock(reel.getAccessoryId(), meters);
        if (accessoryAffected != 1) {
            throw new RuntimeException("配件档案米数已变动且不足扣减，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CableReel reel = getRequiredReel(id);
        if (reel.getStatus() != null && reel.getStatus() == 1) {
            // 已开盘盘的米数已计入配件档案并可能已扣米，删除会破坏盘与档案的米数一致
            throw new RuntimeException("该盘已开盘确认，米数已计入配件档案，不能删除");
        }
        removeById(id);
    }

    // -------------------- 辅助方法 --------------------

    private CableReel getRequiredReel(Long id) {
        CableReel reel = getById(id);
        if (reel == null) {
            throw new RuntimeException("线缆盘不存在或已被删除");
        }
        return reel;
    }

    private Map<Long, Accessory> loadAccessoriesIncludingDeleted(Set<Long> accessoryIds) {
        if (accessoryIds == null || accessoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return accessoryMapper.selectAllByIdsIncludingDeleted(accessoryIds).stream()
                .collect(Collectors.toMap(Accessory::getId, Function.identity(), (a, b) -> a));
    }

    private CableReelVO buildVO(CableReel reel, Accessory accessory) {
        CableReelVO vo = new CableReelVO();
        vo.setId(reel.getId());
        vo.setReelNo(reel.getReelNo());
        vo.setAccessoryId(reel.getAccessoryId());
        vo.setAccessoryName(reel.getAccessoryName());
        vo.setModel(reel.getModel());
        vo.setSpecUnit(reel.getSpecUnit());
        vo.setRemainingMeters(reel.getRemainingMeters());
        vo.setStatus(reel.getStatus());
        vo.setStatusText(reel.getStatus() != null && reel.getStatus() == 1 ? "已开盘" : "未开盘");
        vo.setOpenTime(reel.getOpenTime());
        vo.setCreateTime(reel.getCreateTime());

        boolean deleted = accessory == null
                || (accessory.getDeleted() != null && accessory.getDeleted() == 1);
        vo.setAccessoryDeleted(deleted);
        vo.setAccessoryStockQuantity(accessory == null ? null : accessory.getStockQuantity());
        // 一致性只对“已开盘且配件正常”判定：开盘入账后剩余 == 档案现存量，扣米时两边同步扣减
        boolean opened = reel.getStatus() != null && reel.getStatus() == 1;
        if (opened && !deleted) {
            int stock = accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity();
            int remaining = reel.getRemainingMeters() == null ? 0 : reel.getRemainingMeters();
            vo.setStockMatched(stock == remaining);
        } else {
            vo.setStockMatched(null);
        }
        return vo;
    }
}
