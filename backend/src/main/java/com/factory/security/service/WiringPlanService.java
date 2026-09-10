package com.factory.security.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.factory.security.dto.WiringPlanDTO;
import com.factory.security.entity.WiringPlan;
import com.factory.security.vo.WiringPlanVO;

public interface WiringPlanService extends IService<WiringPlan> {

    Page<WiringPlanVO> page(Integer pageNum, Integer pageSize, String keyword, Integer status);

    WiringPlanVO getDetailById(Long id);

    boolean add(WiringPlanDTO dto);

    boolean update(WiringPlanDTO dto);

    boolean delete(Long id);

    boolean updateStatus(Long id, Integer status);
}
