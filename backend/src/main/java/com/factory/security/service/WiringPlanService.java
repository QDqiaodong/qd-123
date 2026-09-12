package com.factory.security.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.factory.security.dto.WiringPlanDTO;
import com.factory.security.entity.WiringPlan;
import com.factory.security.vo.StockGapVO;
import com.factory.security.vo.StockGapZoneSummaryVO;
import com.factory.security.vo.WiringPlanExportRowVO;
import com.factory.security.vo.WiringPlanVO;

import java.util.List;

public interface WiringPlanService extends IService<WiringPlan> {

    Page<WiringPlanVO> page(Integer pageNum, Integer pageSize, String keyword, Integer status);

    WiringPlanVO getDetailById(Long id);

    boolean add(WiringPlanDTO dto);

    boolean update(WiringPlanDTO dto);

    boolean delete(Long id);

    boolean updateStatus(Long id, Integer status);

    /**
     * 查询当前筛选条件下的全部导出行（不分页）。
     * 同一方案的配件按库房分区排序号排序（无分区排最后），方案边界通过逐行重复方案信息保留；
     * 没有配件的方案输出一条仅含方案信息的占位行，空筛选结果返回空列表。
     */
    List<WiringPlanExportRowVO> listExportRows(String keyword, Integer status);

    /**
     * 库存缺口列表：一个配件一行，需求合计只统计“已启用且未核销”方案；
     * 现存量不足时 shortage=true（前端标红），未分配分区单独成组，已删除配件仍展示但不可核销。
     * 与方案明细、配件档案共用同一套库存数据，刷新后三处保持一致。
     */
    List<StockGapVO> listStockGaps();

    /**
     * 库存缺口按分区汇总：一个分区一行（缺口件数、涉及配件种数），未分配分区单独一行。
     * 与缺口列表共用同一批数据归组，分区顺序与缺口列表一致（分区排序号升序、未分配分区最后），
     * 页面合计、分区小计与导出文件取自同一口径，刷新后保持一致。
     */
    List<StockGapZoneSummaryVO> listStockGapZoneSummary();

    /**
     * 按方案核销出库：校验方案启用、未核销过、无已删除配件、现存量充足后，
     * 在同一事务内逐条扣减配件现存量并写入核销记录。同一方案不可重复核销。
     */
    boolean writeoff(Long id);
}
