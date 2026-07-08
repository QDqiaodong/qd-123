package com.factory.security.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.factory.security.dto.ZoneTagDTO;
import com.factory.security.entity.ZoneTag;

import java.util.List;

public interface ZoneTagService extends IService<ZoneTag> {

    List<ZoneTag> listAll();

    boolean add(ZoneTagDTO dto);

    boolean update(ZoneTagDTO dto);

    boolean delete(Long id);
}
