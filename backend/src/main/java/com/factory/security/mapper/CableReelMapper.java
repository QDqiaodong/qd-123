package com.factory.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.factory.security.entity.CableReel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CableReelMapper extends BaseMapper<CableReel> {

    /**
     * 扣减盘上剩余米数：仅在剩余米数充足时更新成功，返回影响行数 1。
     * 借助数据库行条件更新避免并发扣米把盘上剩余扣成负数。
     */
    @Update("UPDATE cable_reel SET remaining_meters = remaining_meters - #{meters}, update_time = NOW() "
            + "WHERE id = #{id} AND status = 1 AND remaining_meters >= #{meters}")
    int deductMeters(@Param("id") Long id, @Param("meters") Integer meters);
}
