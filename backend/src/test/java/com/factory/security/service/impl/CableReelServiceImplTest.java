package com.factory.security.service.impl;

import com.factory.security.dto.CableReelCreateDTO;
import com.factory.security.dto.CableReelDeductDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.CableReel;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.CableReelMapper;
import com.factory.security.vo.CableReelVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CableReelServiceImplTest {

    // 名称与 ServiceImpl 继承的 baseMapper 字段一致，保证 @InjectMocks 按名注入
    @Mock
    private CableReelMapper baseMapper;

    @Mock
    private AccessoryMapper accessoryMapper;

    @InjectMocks
    private CableReelServiceImpl cableReelService;

    @BeforeEach
    void injectBaseMapper() throws Exception {
        // 与 StockCheckServiceImplTest 相同：按字段名显式注入 ServiceImpl#baseMapper
        java.lang.reflect.Field baseMapperField =
                com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                        .getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(cableReelService, baseMapper);
    }

    private Accessory accessory(Long id, String name, int stock) {
        Accessory accessory = new Accessory();
        accessory.setId(id);
        accessory.setAccessoryName(name);
        accessory.setModel(name + "-M");
        accessory.setSpecUnit("m");
        accessory.setStockQuantity(stock);
        accessory.setDeleted(0);
        return accessory;
    }

    private CableReel reel(Long id, String reelNo, Long accessoryId, int remaining, int status) {
        CableReel reel = new CableReel();
        reel.setId(id);
        reel.setReelNo(reelNo);
        reel.setAccessoryId(accessoryId);
        reel.setAccessoryName("RVV电源线");
        reel.setModel("RVV-2*1.0");
        reel.setSpecUnit("m");
        reel.setRemainingMeters(remaining);
        reel.setStatus(status);
        return reel;
    }

    private CableReelCreateDTO createDTO(String reelNo, Long accessoryId, int meters) {
        CableReelCreateDTO dto = new CableReelCreateDTO();
        dto.setReelNo(reelNo);
        dto.setAccessoryId(accessoryId);
        dto.setRemainingMeters(meters);
        return dto;
    }

    private CableReelDeductDTO deductDTO(int meters) {
        CableReelDeductDTO dto = new CableReelDeductDTO();
        dto.setMeters(meters);
        return dto;
    }

    // -------------------- 建档 --------------------

    @Test
    void createSavesUnopenedReelWithSnapshotAndDoesNotTouchAccessoryStock() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(accessoryMapper.selectById(5L)).thenReturn(accessory(5L, "RVV电源线", 500));

        cableReelService.create(createDTO(" P-001 ", 5L, 200));

        org.mockito.ArgumentCaptor<CableReel> captor =
                org.mockito.ArgumentCaptor.forClass(CableReel.class);
        verify(baseMapper).insert(captor.capture());
        CableReel saved = captor.getValue();
        // 盘号去空格、配件信息快照、初始为未开盘
        assertEquals("P-001", saved.getReelNo());
        assertEquals(5L, saved.getAccessoryId());
        assertEquals("RVV电源线", saved.getAccessoryName());
        assertEquals(200, saved.getRemainingMeters());
        assertEquals(0, saved.getStatus());
        // 建档阶段绝不动配件档案米数
        verify(accessoryMapper, never()).addStock(anyLong(), anyInt());
        verify(accessoryMapper, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    void createRejectsDuplicateReelNo() {
        when(baseMapper.selectCount(any())).thenReturn(1L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cableReelService.create(createDTO("P-001", 5L, 200)));
        assertTrue(ex.getMessage().contains("同一盘号不能建两次"));
        verify(baseMapper, never()).insert(any(CableReel.class));
        verify(accessoryMapper, never()).selectById(anyLong());
    }

    @Test
    void createRejectsMissingOrDeletedAccessory() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(accessoryMapper.selectById(404L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cableReelService.create(createDTO("P-002", 404L, 200)));
        assertTrue(ex.getMessage().contains("不存在或已删除"));
        verify(baseMapper, never()).insert(any(CableReel.class));
    }

    @Test
    void createRejectsNegativeMeters() {
        // 负数在 Service 防御校验处拦截，不查库
        assertThrows(RuntimeException.class,
                () -> cableReelService.create(createDTO("P-003", 5L, -1)));
        verify(baseMapper, never()).insert(any(CableReel.class));
    }

    @Test
    void createAllowsZeroMeterReel() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(accessoryMapper.selectById(5L)).thenReturn(accessory(5L, "RVV电源线", 500));

        cableReelService.create(createDTO("P-000", 5L, 0));

        org.mockito.ArgumentCaptor<CableReel> captor =
                org.mockito.ArgumentCaptor.forClass(CableReel.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getRemainingMeters());
    }

    @Test
    void createConvertsUniqueIndexRaceToDuplicateMessage() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(accessoryMapper.selectById(5L)).thenReturn(accessory(5L, "RVV电源线", 500));
        when(baseMapper.insert(any(CableReel.class))).thenThrow(new DuplicateKeyException(
                "Duplicate entry 'P-001' for key 'cable_reel.uk_reel_no'"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cableReelService.create(createDTO("P-001", 5L, 200)));
        assertTrue(ex.getMessage().contains("同一盘号不能建两次"));
        verify(accessoryMapper, never()).addStock(anyLong(), anyInt());
    }

    // -------------------- 开盘确认 --------------------

    @Test
    void openAddsFullMetersToAccessoryStockAndMarksOpened() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 0));
        // 绑定配件档案现存为 0：整盘是其米数的唯一来源，开盘后剩余 == 档案米数
        when(accessoryMapper.selectById(5L)).thenReturn(accessory(5L, "RVV电源线", 0));
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(accessoryMapper.addStock(5L, 200)).thenReturn(1);

        cableReelService.open(1L);

        // 整盘 200 米一次性入账到配件档案
        verify(accessoryMapper).addStock(5L, 200);
        verify(baseMapper).update(any(), any());
    }

    @Test
    void openRejectsWhenAccessoryAlreadyHasStock() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 0));
        // 档案已有 500 米其他来源库存：开盘会让“盘上剩余 == 档案米数”恒不成立
        when(accessoryMapper.selectById(5L)).thenReturn(accessory(5L, "RVV电源线", 500));
        when(baseMapper.selectCount(any())).thenReturn(0L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> cableReelService.open(1L));
        assertTrue(ex.getMessage().contains("档案现存"));
        verify(baseMapper, never()).update(any(), any());
        verify(accessoryMapper, never()).addStock(anyLong(), anyInt());
    }

    @Test
    void openRejectsDeletedAccessoryBeforeUpdating() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 0));
        when(accessoryMapper.selectById(5L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> cableReelService.open(1L));
        assertTrue(ex.getMessage().contains("配件已删除"));
        verify(baseMapper, never()).update(any(), any());
        verify(accessoryMapper, never()).addStock(anyLong(), anyInt());
    }

    @Test
    void openRejectsAlreadyOpenedReel() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 1));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> cableReelService.open(1L));
        assertTrue(ex.getMessage().contains("已开盘"));
        verify(baseMapper, never()).update(any(), any());
        verify(accessoryMapper, never()).addStock(anyLong(), anyInt());
    }

    @Test
    void openConvertsUniqueAccessoryRaceToBusinessMessage() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 0));
        when(accessoryMapper.selectById(5L)).thenReturn(accessory(5L, "RVV电源线", 0));
        // 显式判重未发现已开盘盘，但并发下另一请求先抢占：条件更新撞 uk_open_accessory
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.update(any(), any())).thenThrow(new DuplicateKeyException(
                "Duplicate entry '5' for key 'cable_reel.uk_open_accessory'"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> cableReelService.open(1L));
        assertTrue(ex.getMessage().contains("同一配件同时只能开一个盘"));
        // 抢占失败：米数绝不能入账（事务保证整体回滚）
        verify(accessoryMapper, never()).addStock(anyLong(), anyInt());
    }

    @Test
    void openRejectsWhenAnotherReelOfSameAccessoryAlreadyOpened() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 0));
        when(accessoryMapper.selectById(5L)).thenReturn(accessory(5L, "RVV电源线", 0));
        // 显式判重已发现该配件的另一个已开盘盘
        when(baseMapper.selectCount(any())).thenReturn(1L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> cableReelService.open(1L));
        assertTrue(ex.getMessage().contains("同一配件同时只能开一个盘"));
        verify(baseMapper, never()).update(any(), any());
        verify(accessoryMapper, never()).addStock(anyLong(), anyInt());
    }

    // -------------------- 扣米 --------------------

    @Test
    void deductCutsBothReelAndAccessoryStockInSameFlow() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 1));
        when(accessoryMapper.selectById(5L)).thenReturn(accessory(5L, "RVV电源线", 200));
        when(baseMapper.deductMeters(1L, 30)).thenReturn(1);
        when(accessoryMapper.deductStock(5L, 30)).thenReturn(1);

        cableReelService.deduct(1L, deductDTO(30));

        // 盘上剩余与配件档案现存量同步扣减，刷新后二者一致
        verify(baseMapper).deductMeters(1L, 30);
        verify(accessoryMapper).deductStock(5L, 30);
    }

    @Test
    void deductRejectsUnopenedReel() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 0));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cableReelService.deduct(1L, deductDTO(30)));
        // 没开过的盘不能拿去扣米
        assertTrue(ex.getMessage().contains("尚未开盘"));
        verify(baseMapper, never()).deductMeters(anyLong(), anyInt());
        verify(accessoryMapper, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    void deductRejectsZeroOrNegativeMeters() {
        assertThrows(RuntimeException.class, () -> cableReelService.deduct(1L, deductDTO(0)));
        assertThrows(RuntimeException.class, () -> cableReelService.deduct(1L, deductDTO(-5)));
        verify(baseMapper, never()).selectById(anyLong());
    }

    @Test
    void deductRejectsWhenReelRemainingInsufficient() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 20, 1));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cableReelService.deduct(1L, deductDTO(30)));
        assertTrue(ex.getMessage().contains("不足扣减"));
        verify(baseMapper, never()).deductMeters(anyLong(), anyInt());
    }

    @Test
    void deductRejectsWhenAccessoryStockInsufficient() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 1));
        // 档案米数被外部调整拉低到 10，与盘上 200 不一致：拒绝扣米
        when(accessoryMapper.selectById(5L)).thenReturn(accessory(5L, "RVV电源线", 10));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cableReelService.deduct(1L, deductDTO(30)));
        assertTrue(ex.getMessage().contains("档案现存"));
        verify(baseMapper, never()).deductMeters(anyLong(), anyInt());
    }

    @Test
    void deductRejectsDeletedAccessory() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 1));
        when(accessoryMapper.selectById(5L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cableReelService.deduct(1L, deductDTO(30)));
        assertTrue(ex.getMessage().contains("配件已删除"));
        verify(baseMapper, never()).deductMeters(anyLong(), anyInt());
    }

    @Test
    void deductRollsBackWhenReelConditionLosesRace() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 1));
        when(accessoryMapper.selectById(5L)).thenReturn(accessory(5L, "RVV电源线", 200));
        when(baseMapper.deductMeters(1L, 30)).thenReturn(0);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cableReelService.deduct(1L, deductDTO(30)));
        assertTrue(ex.getMessage().contains("请刷新"));
        // 盘上条件更新失败（0 行）：不得继续扣配件档案
        verify(accessoryMapper, never()).deductStock(anyLong(), anyInt());
    }

    // -------------------- 删除 --------------------

    @Test
    void deleteAllowedOnlyForUnopenedReel() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 0));
        when(baseMapper.deleteById(1L)).thenReturn(1);

        cableReelService.delete(1L);

        verify(baseMapper).deleteById(1L);
    }

    @Test
    void deleteRejectedForOpenedReel() {
        when(baseMapper.selectById(1L)).thenReturn(reel(1L, "P-001", 5L, 200, 1));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> cableReelService.delete(1L));
        assertTrue(ex.getMessage().contains("已开盘"));
        verify(baseMapper, never()).deleteById(anyLong());
    }

    @Test
    void missingReelThrows() {
        when(baseMapper.selectById(404L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> cableReelService.open(404L));
        assertTrue(ex.getMessage().contains("线缆盘不存在"));
    }

    // -------------------- 列表装配一致性 --------------------

    @Test
    void pageMarksOpenedReelMatchedWhenStockEqualsRemaining() {
        CableReel opened = reel(1L, "P-001", 5L, 170, 1);
        CableReel unopened = reel(2L, "P-002", 5L, 300, 0);
        Page<CableReel> resultPage = new Page<>(1, 10, 2);
        resultPage.setRecords(Arrays.asList(opened, unopened));
        when(baseMapper.selectPage(any(), any())).thenReturn(resultPage);
        when(accessoryMapper.selectAllByIdsIncludingDeleted(any()))
                .thenReturn(Collections.singletonList(accessory(5L, "RVV电源线", 170)));

        Page<CableReelVO> page = cableReelService.page(1, 10, null, null, null);
        List<CableReelVO> records = page.getRecords();

        // 已开盘且档案米数 == 盘上剩余：一致
        CableReelVO openedVO = records.get(0);
        assertEquals("已开盘", openedVO.getStatusText());
        assertTrue(openedVO.getStockMatched());
        assertFalse(Boolean.TRUE.equals(openedVO.getAccessoryDeleted()));
        // 未开盘盘不参与一致性判定，档案未入账
        CableReelVO unopenedVO = records.get(1);
        assertEquals("未开盘", unopenedVO.getStatusText());
        assertNull(unopenedVO.getStockMatched());
    }

    @Test
    void pageMarksMismatchWhenStockDivergesAndDeletedAccessory() {
        CableReel opened = reel(1L, "P-001", 5L, 200, 1);
        Page<CableReel> resultPage = new Page<>(1, 10, 1);
        resultPage.setRecords(Collections.singletonList(opened));
        when(baseMapper.selectPage(any(), any())).thenReturn(resultPage);
        // 档案现存 100 与盘上 200 不一致（如盘点回写把档案改成了 100）
        when(accessoryMapper.selectAllByIdsIncludingDeleted(any()))
                .thenReturn(Collections.singletonList(accessory(5L, "RVV电源线", 100)));

        Page<CableReelVO> page = cableReelService.page(1, 10, null, null, null);
        CableReelVO vo = page.getRecords().get(0);
        assertFalse(vo.getStockMatched());
        assertEquals(100, vo.getAccessoryStockQuantity());
        assertEquals(200, vo.getRemainingMeters());
    }

    @Test
    void pageMarksAccessoryDeletedWithoutThrowing() {
        CableReel opened = reel(1L, "P-001", 99L, 200, 1);
        Page<CableReel> resultPage = new Page<>(1, 10, 1);
        resultPage.setRecords(Collections.singletonList(opened));
        when(baseMapper.selectPage(any(), any())).thenReturn(resultPage);
        Accessory deleted = accessory(99L, "旧线缆", 200);
        deleted.setDeleted(1);
        when(accessoryMapper.selectAllByIdsIncludingDeleted(any()))
                .thenReturn(Collections.singletonList(deleted));

        Page<CableReelVO> page = cableReelService.page(1, 10, null, null, null);
        CableReelVO vo = page.getRecords().get(0);
        assertTrue(vo.getAccessoryDeleted());
        assertNull(vo.getStockMatched());
    }
}
