package com.factory.security.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.factory.security.dto.StockCheckConfirmDTO;
import com.factory.security.dto.StockCheckCreateDTO;
import com.factory.security.dto.StockCheckItemDTO;
import com.factory.security.entity.StockCheck;
import com.factory.security.vo.StockCheckDetailVO;
import com.factory.security.vo.StockCheckVO;

public interface StockCheckService extends IService<StockCheck> {

    /**
     * 盘点单分页：可按状态、分区筛选，按创建时间倒序。
     * unassigned=true 只看未分配分区盘点单（zoneTagId IS NULL），与 zoneTagId 互斥。
     * hasRemark 非空时只在已确认单（status=1）中按“是否填写差异说明”筛选：
     * true=有说明，false=无说明；待确认单天然没有说明，不参与该筛选
     */
    Page<StockCheckVO> page(Integer pageNum, Integer pageSize, Integer status,
                            Long zoneTagId, boolean unassigned, Boolean hasRemark);

    /**
     * 按分区开盘：快照分区内全部配件（含已删除配件、空分区也允许开盘）。
     * 同一分区同时只允许一张待确认盘点单
     *
     * @return 新建盘点单ID
     */
    Long create(StockCheckCreateDTO dto);

    /** 盘点单详情：账面快照、实盘登记值与差异实时装配 */
    StockCheckDetailVO getDetailById(Long id);

    /** 登记实盘数：仅待确认单可改，可部分登记、反复覆盖；不动库存 */
    void recordItems(Long id, StockCheckItemDTO dto);

    /**
     * 确认盘点：在同一事务内按实盘数一次性回写配件现存量（已删除配件不回写），
     * 确认后盘点单锁定只读，不可再登记或删除
     */
    void confirm(Long id, StockCheckConfirmDTO dto);

    /** 删除盘点单：仅待确认单可删除（已确认单是库存回写凭证需保留） */
    void delete(Long id);

    /**
     * 已确认盘点单的差异明细：仅含盘盈/盘亏（已登记且差异非 0）的正常配件，
     * 已删除配件与账实一致配件不导出。与详情接口同一口径实时装配，
     * 刷新后合计行的差异种数、盈亏件数与页面一致。
     * 待确认单差异尚未定稿，拒绝导出
     */
    StockCheckDetailVO getConfirmedDetailForExport(Long id);
}
