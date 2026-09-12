package com.factory.security.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.factory.security.dto.AccessoryDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.vo.SafetyStockVO;

import java.util.List;

public interface AccessoryService extends IService<Accessory> {

    Page<Accessory> page(Integer pageNum, Integer pageSize, String keyword, Long zoneTagId);

    boolean add(AccessoryDTO dto);

    boolean update(AccessoryDTO dto);

    boolean updateZone(Long id, Long zoneTagId);

    boolean delete(Long id);

    /**
     * 安全库存台账：仅返回未删除、已设安全库存下限且现存量低于下限的配件。
     * 未设下限（null）、现存量不低于下限、已删除的配件不进台账；
     * 未分配分区的低位配件同样列出。改下限或现存量后实时重算。
     * 排序：分区排序号升序、未分配分区殿后；同一分区内缺口从大到小。
     * 紧急标记（urgent）随缺口实时计算：缺口达到下限一半及以上为紧急。
     *
     * @param zoneTagId     按库房分区筛选；null 且 unassignedOnly=false 时返回全集
     * @param unassignedOnly true 时只返回未分配分区（无分区或分区标签已缺失）的低位配件
     */
    List<SafetyStockVO> listSafetyStockShortages(Long zoneTagId, boolean unassignedOnly);
}
