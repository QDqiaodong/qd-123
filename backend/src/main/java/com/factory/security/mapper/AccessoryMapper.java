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
}
