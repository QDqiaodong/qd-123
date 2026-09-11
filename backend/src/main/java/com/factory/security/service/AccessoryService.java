package com.factory.security.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.factory.security.dto.AccessoryDTO;
import com.factory.security.entity.Accessory;

public interface AccessoryService extends IService<Accessory> {

    Page<Accessory> page(Integer pageNum, Integer pageSize, String keyword, Long zoneTagId);

    boolean add(AccessoryDTO dto);

    boolean update(AccessoryDTO dto);

    /**
     * 调整配件所属分区：校验配件存在且未删除、原因非空、目标分区与当前分区不同后，
     * 在同一事务内更新配件分区并写入一条调区流水（原分区、目标分区、原因、时间）。
     */
    boolean adjustZone(Long id, Long zoneTagId, String reason);

    boolean delete(Long id);
}
