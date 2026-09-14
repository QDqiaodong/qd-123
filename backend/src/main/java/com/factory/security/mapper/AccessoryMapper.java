package com.factory.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.factory.security.entity.Accessory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface AccessoryMapper extends BaseMapper<Accessory> {

    /**
     * 出库核销扣减现存量：仅在现存量充足（含未删除配件）时更新成功，返回影响行数 1。
     * 借助数据库行条件更新避免并发核销导致库存扣成负数。
     */
    @Update("UPDATE accessory SET stock_quantity = stock_quantity - #{quantity}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND stock_quantity >= #{quantity}")
    int deductStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 线缆盘开盘确认入账：把整盘米数一次性加到绑定配件的现存量（米）。
     * 仅对未删除配件生效；已删除配件影响行数为 0，由调用方据此拒绝开盘
     */
    @Update("UPDATE accessory SET stock_quantity = stock_quantity + #{quantity}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int addStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 显式更新可空列“安全库存下限”。
     * MyBatis-Plus 默认 updateById 字段策略为 NOT_NULL，会把 null 字段直接忽略，
     * 导致编辑配件时“清空下限（置为 NULL）”无法落库，故更新后按 DTO 值显式同步一次，
     * 保证“设过下限后清空 = 不设下限、移出台账”确实生效。
     */
    @Update("UPDATE accessory SET safety_stock = #{safetyStock}, update_time = NOW() WHERE id = #{id}")
    int updateSafetyStock(@Param("id") Long id, @Param("safetyStock") Integer safetyStock);

    /**
     * 按 ID 批量查询配件（含已软删除的配件）。
     * 库存缺口列表需要把被启用方案引用的已删除配件一并列出（仍显示、不可核销），
     * MyBatis-Plus 逻辑删除会过滤内置 selectBatchIds，故使用自定义 SQL。
     */
    List<Accessory> selectAllByIdsIncludingDeleted(@Param("ids") Collection<Long> ids);

    /**
     * 按分区查询全部配件（含已软删除配件）：分区盘点开盘时据此把配件带入盘点单。
     * zoneTagId 为 null 时查询未分配分区（zone_tag_id IS NULL）。
     * 已删除配件仍在原分区下展示，但确认盘点时不回写库存
     */
    List<Accessory> selectByZoneIncludingDeleted(@Param("zoneTagId") Long zoneTagId);

    /**
     * 盘点确认回写现存量：直接以实盘数覆盖账面（与核销的条件扣减不同，盘盈盘亏都允许）。
     * 仅回写未删除配件，已删除配件影响行数为 0，由调用方据此跳过；
     * 按主键行级更新，并发确认不会丢失更新
     */
    @Update("UPDATE accessory SET stock_quantity = #{actualQuantity}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int resetStock(@Param("id") Long id, @Param("actualQuantity") Integer actualQuantity);

    /**
     * 提交补货单：把配件标记为待补，回填补货单ID、单号（快照）与待补数量。
     * 行级条件 {@code replenish_order_id IS NULL} 保证同一配件同时只能挂在一张有效补货单上：
     * 并发提交两张单时只有一单能抢占成功，失败方影响行数 0，由调用方整单回滚；
     * 已删除配件同样不允许占用待补标记（deleted = 0）
     */
    @Update("UPDATE accessory SET replenish_order_id = #{orderId}, replenish_order_no = #{orderNo}, "
            + "replenish_pending_quantity = #{quantity}, update_time = NOW() "
            + "WHERE id = #{accessoryId} AND deleted = 0 AND replenish_order_id IS NULL")
    int markReplenishPending(@Param("accessoryId") Long accessoryId,
                             @Param("orderId") Long orderId,
                             @Param("orderNo") String orderNo,
                             @Param("quantity") Integer quantity);

    /**
     * 作废补货单：清除该单在配件档案上的全部待补标记。
     * 仅在标记仍指向被作废单据时清空（带 orderId 条件），避免误清新单——
     * 极端情况下旧单作废与新单提交交错，不能把后来新单的待补标记一并抹掉。
     * 不限制 deleted：配件即便在补货期间被软删除，其待补标记也要随作废一并清掉
     */
    @Update("UPDATE accessory SET replenish_order_id = NULL, replenish_order_no = NULL, "
            + "replenish_pending_quantity = NULL, update_time = NOW() "
            + "WHERE replenish_order_id = #{orderId}")
    int clearReplenishPending(@Param("orderId") Long orderId);
}
