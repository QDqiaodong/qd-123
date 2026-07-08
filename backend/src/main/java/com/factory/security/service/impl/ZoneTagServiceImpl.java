package com.factory.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.factory.security.dto.ZoneTagDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.AccessoryService;
import com.factory.security.service.ZoneTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ZoneTagServiceImpl extends ServiceImpl<ZoneTagMapper, ZoneTag> implements ZoneTagService {

    private static final String ZONE_TAG_CACHE_KEY = "zone:tag:all";
    private static final long CACHE_EXPIRE_HOURS = 24;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessoryService accessoryService;

    @Override
    public List<ZoneTag> listAll() {
        try {
            String cacheValue = redisTemplate.opsForValue().get(ZONE_TAG_CACHE_KEY);
            if (cacheValue != null) {
                return objectMapper.readValue(cacheValue, new TypeReference<List<ZoneTag>>() {});
            }
        } catch (Exception e) {
            // 缓存读取失败，从数据库读取
        }

        LambdaQueryWrapper<ZoneTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ZoneTag::getSortOrder);
        List<ZoneTag> list = list(wrapper);

        try {
            redisTemplate.opsForValue().set(ZONE_TAG_CACHE_KEY,
                    objectMapper.writeValueAsString(list),
                    CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            // 缓存写入失败不影响主流程
        }

        return list;
    }

    @Override
    public boolean add(ZoneTagDTO dto) {
        ZoneTag zoneTag = new ZoneTag();
        org.springframework.beans.BeanUtils.copyProperties(dto, zoneTag);
        if (zoneTag.getSortOrder() == null) {
            zoneTag.setSortOrder(0);
        }
        boolean result = save(zoneTag);
        if (result) {
            clearCache();
        }
        return result;
    }

    @Override
    public boolean update(ZoneTagDTO dto) {
        ZoneTag zoneTag = new ZoneTag();
        org.springframework.beans.BeanUtils.copyProperties(dto, zoneTag);
        boolean result = updateById(zoneTag);
        if (result) {
            clearCache();
        }
        return result;
    }

    @Override
    public boolean delete(Long id) {
        LambdaQueryWrapper<Accessory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Accessory::getZoneTagId, id);
        long count = accessoryService.count(wrapper);
        if (count > 0) {
            throw new RuntimeException("该分区下存在配件，无法删除");
        }

        boolean result = removeById(id);
        if (result) {
            clearCache();
        }
        return result;
    }

    private void clearCache() {
        try {
            redisTemplate.delete(ZONE_TAG_CACHE_KEY);
        } catch (Exception e) {
            // 缓存清除失败不影响主流程
        }
    }
}
