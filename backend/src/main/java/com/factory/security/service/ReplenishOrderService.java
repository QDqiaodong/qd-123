package com.factory.security.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.factory.security.dto.ReplenishCancelDTO;
import com.factory.security.dto.ReplenishCreateDTO;
import com.factory.security.dto.ReplenishItemDTO;
import com.factory.security.entity.ReplenishOrder;
import com.factory.security.vo.ReplenishOrderDetailVO;
import com.factory.security.vo.ReplenishOrderVO;

public interface ReplenishOrderService extends IService<ReplenishOrder> {

    /** 补货单分页：可按状态筛选，按创建时间倒序 */
    Page<ReplenishOrderVO> page(Integer pageNum, Integer pageSize, Integer status);

    /**
     * 生成补货单（待提交草稿）：服务端按配件档案重新校验勾选项
     * （未删除、已设下限、现存确实低于下限、未被其他有效补货单占用），
     * 快照配件、分区与实时缺口，补货数量默认等于缺口。草稿不占用配件待补标记
     *
     * @return 新建补货单ID
     */
    Long create(ReplenishCreateDTO dto);

    /** 补货单详情：明细与按分区汇总缺口件数实时装配 */
    ReplenishOrderDetailVO getDetailById(Long id);

    /**
     * 调整补货数量：仅待提交草稿可改，可只提交部分行、反复覆盖；
     * 已提交、已作废单据数量锁定，拒绝修改
     */
    void updateItems(Long id, ReplenishItemDTO dto);

    /**
     * 提交补货单：在同一事务内把单号与待补数量回写到每个配件档案；
     * 行级条件更新保证同一配件不会被两张有效单同时占用，任一冲突整体回滚。
     * 提交后单据数量锁定，不可再改、不可删除、不可重复提交
     */
    void submit(Long id);

    /**
     * 作废补货单：仅已提交单可作废，同一事务内清除全部配件档案待补标记；
     * 作废后单据只读留档。待提交草稿不需要作废，直接删除即可
     */
    void cancel(Long id, ReplenishCancelDTO dto);

    /** 删除补货单：仅待提交草稿可删除（已提交单作废旧标记、已作废单需留档） */
    void delete(Long id);
}
