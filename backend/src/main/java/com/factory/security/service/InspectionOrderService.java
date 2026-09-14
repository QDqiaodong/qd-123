package com.factory.security.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.factory.security.dto.InspectionCreateDTO;
import com.factory.security.dto.InspectionQualifyDTO;
import com.factory.security.dto.InspectionResultDTO;
import com.factory.security.entity.InspectionOrder;
import com.factory.security.vo.InspectionOrderVO;

public interface InspectionOrderService extends IService<InspectionOrder> {

    /**
     * 送检单分页：可按是否已回样、是否合格、送检单号/配件名称关键词筛选，按创建时间倒序
     */
    Page<InspectionOrderVO> page(Integer pageNum, Integer pageSize, Integer sampleReturned,
                                 Integer qualified, String keyword);

    /**
     * 新建送检单：按已建档且未删除的配件送检，送检批次与实验室名称必须填完整；
     * 新建后一律待回样、未判定合格
     */
    Long create(InspectionCreateDTO dto);

    /**
     * 实验室写回结论：仅待回样单可写回，结论非空白；写回后单据变为已回样，才能标记合格。
     * 已回样单结论留档不可改写
     */
    void writeResult(Long id, InspectionResultDTO dto);

    /**
     * 标记/取消合格：只有已回样（已写回结论）的送检单才能标记合格，
     * 未回样不能标合格（前后端双层强制）
     */
    void qualify(Long id, InspectionQualifyDTO dto);
}
