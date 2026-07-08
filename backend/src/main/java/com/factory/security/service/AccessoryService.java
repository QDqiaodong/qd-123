package com.factory.security.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.factory.security.dto.AccessoryDTO;
import com.factory.security.entity.Accessory;

public interface AccessoryService extends IService<Accessory> {

    Page<Accessory> page(Integer pageNum, Integer pageSize, String keyword, Long zoneTagId);

    boolean add(AccessoryDTO dto);

    boolean update(AccessoryDTO dto);

    boolean updateZone(Long id, Long zoneTagId);
}
