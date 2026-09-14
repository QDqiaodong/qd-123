package com.factory.security.service.impl;

import com.factory.security.dto.StockCheckConfirmDTO;
import com.factory.security.dto.StockCheckCreateDTO;
import com.factory.security.dto.StockCheckItemDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.StockCheck;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.StockCheckMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.StockCheckService;
import com.factory.security.vo.StockCheckDetailVO;
import com.factory.security.vo.StockCheckItemVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 真实 MySQL 8 复现：分区盘点确认后，是否每个配件的现存量都被回写。
 * 默认不跑（无 -Dmysql.repro 时在容器里跳过）；需要显式开启
 */
@SpringBootTest
class StockCheckMysqlReproIT {

    @Autowired
    private StockCheckService stockCheckService;

    @Autowired
    private StockCheckMapper stockCheckMapper;

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Autowired
    private ZoneTagMapper zoneTagMapper;

    @Test
    void confirmWritesBackEveryAccessoryOnRealMysql() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getProperty("mysql.repro")), "跳过：仅 MySQL 复现时运行");

        ZoneTag zone = new ZoneTag();
        zone.setTagName("复现分区");
        zone.setTagCode("ZONE-REPRO-" + System.nanoTime());
        zone.setSortOrder(99);
        zoneTagMapper.insert(zone);

        int total = 12;
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            Accessory a = new Accessory();
            a.setAccessoryName("复现配件" + i);
            a.setModel("REPRO-MODEL-" + System.nanoTime() + "-" + i);
            a.setZoneTagId(zone.getId());
            a.setStockQuantity(100);
            accessoryMapper.insert(a);
            ids.add(a.getId());
        }

        StockCheckCreateDTO createDTO = new StockCheckCreateDTO();
        createDTO.setZoneTagId(zone.getId());
        Long checkId = stockCheckService.create(createDTO);

        // 以明细行（后端按配件名称排序）的真实顺序计算期望：accessoryId -> 实盘数
        StockCheckDetailVO detail = stockCheckService.getDetailById(checkId);
        Map<Long, Integer> expectedByAccessoryId = new HashMap<>();
        List<StockCheckItemDTO.StockCheckActualDTO> batch1 = new ArrayList<>();
        List<StockCheckItemDTO.StockCheckActualDTO> batch2 = new ArrayList<>();
        int idx = 0;
        for (StockCheckItemVO item : detail.getItems()) {
            int actual = 100 + (idx % 3 == 0 ? -10 : idx % 3 == 1 ? 10 : 0);
            expectedByAccessoryId.put(item.getAccessoryId(), actual);
            StockCheckItemDTO.StockCheckActualDTO dto = new StockCheckItemDTO.StockCheckActualDTO();
            dto.setItemId(item.getId());
            dto.setActualQuantity(actual);
            // 模拟仓管分批保存：前 4 个一批，中间 4 个一批，其余最后一批
            if (idx < 4) batch1.add(dto);
            else if (idx < 8) batch2.add(dto);
            else {
                // 最后一批在第二批保存后一起提交
                batch2.add(dto);
            }
            idx++;
        }

        StockCheckItemDTO d1 = new StockCheckItemDTO();
        d1.setItems(batch1);
        stockCheckService.recordItems(checkId, d1);

        StockCheckItemDTO d2 = new StockCheckItemDTO();
        d2.setItems(batch2);
        stockCheckService.recordItems(checkId, d2);

        // 重新打开盘点单（模拟用户关掉抽屉再进），确认详情里实盘值齐全后再确认
        StockCheckDetailVO reopened = stockCheckService.getDetailById(checkId);
        List<StockCheckItemDTO.StockCheckActualDTO> allFromReopened = new ArrayList<>();
        for (StockCheckItemVO item : reopened.getItems()) {
            if (Boolean.TRUE.equals(item.getAccessoryDeleted())) continue;
            StockCheckItemDTO.StockCheckActualDTO dto = new StockCheckItemDTO.StockCheckActualDTO();
            dto.setItemId(item.getId());
            dto.setActualQuantity(item.getActualQuantity());
            allFromReopened.add(dto);
        }

        StockCheckConfirmDTO confirmDTO = new StockCheckConfirmDTO();
        confirmDTO.setConfirmRemark("MySQL 真实链路盘点：差异逐笔核对确认");
        stockCheckService.confirm(checkId, confirmDTO);

        StockCheck check = stockCheckMapper.selectById(checkId);
        System.out.println(">>> 盘点单状态 status=" + check.getStatus() + " diffCount=" + check.getDiffCount());

        int stale = 0;
        for (Long accessoryId : ids) {
            Accessory a = accessoryMapper.selectById(accessoryId);
            int expected = expectedByAccessoryId.get(accessoryId);
            String mark = a.getStockQuantity().equals(expected) ? "OK" : "STALE";
            if (!"OK".equals(mark)) stale++;
            System.out.printf(">>> id=%d %s 档案现存=%d 实盘=%d %s%n",
                    accessoryId, a.getAccessoryName(), a.getStockQuantity(), expected, mark);
        }
        System.out.println(">>> 未回写配件种数=" + stale);

        // 再打开已确认单据：账面快照 vs 档案现存
        StockCheckDetailVO confirmed = stockCheckService.getDetailById(checkId);
        int mismatch = 0;
        for (StockCheckItemVO item : confirmed.getItems()) {
            Accessory a = accessoryMapper.selectById(item.getAccessoryId());
            if (a != null && !a.getStockQuantity().equals(item.getActualQuantity())) {
                mismatch++;
                System.out.printf(">>> 账实不符 id=%d 档案=%d 实盘=%d%n",
                        item.getAccessoryId(), a.getStockQuantity(), item.getActualQuantity());
            }
        }
        System.out.println(">>> 已确认单账实不符种数=" + mismatch);
    }
}
