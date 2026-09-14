package com.factory.security.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.factory.security.dto.FiberSpliceCommissionDTO;
import com.factory.security.dto.FiberSpliceJointCreateDTO;
import com.factory.security.dto.FiberSpliceJointUpdateDTO;
import com.factory.security.dto.FiberSpliceVoidDTO;
import com.factory.security.entity.FiberSpliceJoint;
import com.factory.security.vo.FiberSpliceJointVO;

public interface FiberSpliceJointService extends IService<FiberSpliceJoint> {

    /**
     * 光纤熔接接头分页：可按接头编号关键词、所属分区、是否过 OTDR、可投运状态、在档/作废筛选，
     * 默认只列在档接头，按创建时间倒序
     */
    Page<FiberSpliceJointVO> page(Integer pageNum, Integer pageSize, String keyword,
                                  Long zoneTagId, Integer otdrPassed,
                                  Integer commissionable, Integer status);

    /** 登记新接头：接头编号全局唯一，登记时不可投运，需通过 OTDR 后再单独标记 */
    Long create(FiberSpliceJointCreateDTO dto);

    /**
     * 编辑在档接头：可改所属分区、盘留米数、是否过 OTDR、备注；接头编号不可改。
     * 已标记可投运的接头不允许把 OTDR 改成未过（需先取消可投运标记）
     */
    void update(Long id, FiberSpliceJointUpdateDTO dto);

    /**
     * 标记/取消可投运：仅在档且已过 OTDR 的接头允许标记可投运，
     * 未过 OTDR 不能标可投运（前后端双层强制）
     */
    void commission(Long id, FiberSpliceCommissionDTO dto);

    /** 作废：在档接头置为“已作废”只读留档，不做物理删除；可填作废原因 */
    void voidJoint(Long id, FiberSpliceVoidDTO dto);
}
