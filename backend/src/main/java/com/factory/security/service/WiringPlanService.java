package com.factory.security.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.factory.security.dto.WiringPlanDTO;
import com.factory.security.entity.WiringPlan;
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
}
