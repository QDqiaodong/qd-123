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
}
