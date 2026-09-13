package com.factory.security.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.factory.security.dto.CableReelCreateDTO;
import com.factory.security.dto.CableReelDeductDTO;
import com.factory.security.entity.CableReel;
import com.factory.security.vo.CableReelVO;

public interface CableReelService extends IService<CableReel> {

    /**
     * 线缆盘分页：可按盘号关键词、状态、绑定配件筛选，按创建时间倒序
     */
    Page<CableReelVO> page(Integer pageNum, Integer pageSize, String keyword,
                           Integer status, Long accessoryId);

    /** 盘号全局唯一建档：初始状态恒为“未开盘”，盘上米数此时不进配件档案 */
    Long create(CableReelCreateDTO dto);

    /**
     * 开盘确认：仅未开盘盘可确认。同一事务内把整盘剩余米数一次性加到配件档案现存量，
     * 并把盘置为已开盘；同一配件同时只允许一个已开盘（uk_open_accessory 兜底）
     */
    void open(Long id);

    /**
     * 已开盘盘扣米：在同一事务内按米数同步扣减盘上剩余与配件档案现存量，
     * 米数为正、不超过盘上剩余（条件更新不会扣成负数）；未开过的盘不能扣米
     */
    void deduct(Long id, CableReelDeductDTO dto);

    /** 删除线缆盘：仅未开盘盘可删除；已开盘盘的米数已计入档案，是库存凭证需保留 */
    void delete(Long id);
}
