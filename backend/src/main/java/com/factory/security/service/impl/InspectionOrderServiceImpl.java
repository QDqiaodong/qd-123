package com.factory.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.factory.security.dto.InspectionCreateDTO;
import com.factory.security.dto.InspectionQualifyDTO;
import com.factory.security.dto.InspectionResultDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.InspectionOrder;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.InspectionOrderMapper;
import com.factory.security.service.InspectionOrderService;
import com.factory.security.vo.InspectionOrderVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class InspectionOrderServiceImpl
        extends ServiceImpl<InspectionOrderMapper, InspectionOrder>
        implements InspectionOrderService {

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Override
    public Page<InspectionOrderVO> page(Integer pageNum, Integer pageSize, Integer sampleReturned,
                                        Integer qualified, String keyword) {
        Page<InspectionOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InspectionOrder> wrapper = new LambdaQueryWrapper<>();
        if (sampleReturned != null) {
            // 列表按是否已回样筛选
            wrapper.eq(InspectionOrder::getSampleReturned, sampleReturned);
        }
        if (qualified != null) {
            wrapper.eq(InspectionOrder::getQualified, qualified);
        }
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            // 送检单号或配件名称命中关键词
            wrapper.and(w -> w.like(InspectionOrder::getInspectionNo, trimmed)
                    .or().like(InspectionOrder::getAccessoryName, trimmed));
        }
        wrapper.orderByDesc(InspectionOrder::getCreateTime);
        wrapper.orderByDesc(InspectionOrder::getId);
        Page<InspectionOrder> orderPage = page(page, wrapper);

        Page<InspectionOrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(),
                orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream()
                .map(this::buildVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(InspectionCreateDTO dto) {
        // 送检批次与实验室名称：Bean Validation 已强制非空白，Service 层再 trim 兜底，纯空格不能提交
        String batchNo = StringUtils.trimWhitespace(dto.getBatchNo());
        String labName = StringUtils.trimWhitespace(dto.getLabName());
        if (!StringUtils.hasText(batchNo)) {
            throw new RuntimeException("请填写送检批次");
        }
        if (!StringUtils.hasText(labName)) {
            throw new RuntimeException("请填写实验室名称");
        }

        // 只能按已建档配件送检：selectById 经 @TableLogic 过滤，已删除/不存在的配件一律拒绝
        Accessory accessory = accessoryMapper.selectById(dto.getAccessoryId());
        if (accessory == null) {
            throw new RuntimeException("送检配件不存在或已删除，请刷新配件列表后重新选择");
        }

        InspectionOrder order = new InspectionOrder();
        order.setAccessoryId(accessory.getId());
        order.setAccessoryName(accessory.getAccessoryName());
        order.setModel(accessory.getModel());
        order.setSpecUnit(accessory.getSpecUnit());
        order.setBatchNo(batchNo);
        order.setLabName(labName);
        // 新建即“待回样”：实验室写回结论前一直等待回样，未回样不能标合格
        order.setSampleReturned(0);
        order.setQualified(0);
        insertWithUniqueOrderNo(order);
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void writeResult(Long id, InspectionResultDTO dto) {
        InspectionOrder order = getRequiredOrder(id);
        if (order.getSampleReturned() != null && order.getSampleReturned() == 1) {
            // 回样结论只由实验室写回一次，写回后留档不可改写
            throw new RuntimeException("该送检单已回样，结论已留档不能重复写回");
        }
        String conclusion = StringUtils.trimWhitespace(dto.getLabConclusion());
        if (!StringUtils.hasText(conclusion)) {
            throw new RuntimeException("请填写实验室回样结论");
        }

        // 条件更新 sample_returned=0：并发回样只有一个请求成功，防止结论被覆盖
        UpdateWrapper<InspectionOrder> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .eq("sample_returned", 0)
                .set("sample_returned", 1)
                .set("lab_conclusion", conclusion)
                .set("sample_return_time", LocalDateTime.now());
        int affected = baseMapper.update(null, wrapper);
        if (affected != 1) {
            throw new RuntimeException("该送检单已回样，请勿重复写回结论");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void qualify(Long id, InspectionQualifyDTO dto) {
        Integer qualified = dto == null ? null : dto.getQualified();
        if (qualified == null || (qualified != 0 && qualified != 1)) {
            throw new RuntimeException("合格标记取值非法");
        }
        InspectionOrder order = getRequiredOrder(id);
        if (qualified == 1) {
            // 未回样不能标合格：Service 层再次强制，不信任前端按钮禁用
            if (order.getSampleReturned() == null || order.getSampleReturned() != 1) {
                throw new RuntimeException("该送检单尚未回样，实验室写回结论前不能标记合格");
            }
            // 条件更新 sample_returned=1 兜底并发：合格恒建立在已回样之上
            UpdateWrapper<InspectionOrder> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", id)
                    .eq("sample_returned", 1)
                    .eq("qualified", 0)
                    .set("qualified", 1)
                    .set("qualified_time", LocalDateTime.now());
            int affected = baseMapper.update(null, wrapper);
            if (affected != 1) {
                throw new RuntimeException("该送检单尚未回样或已标记合格，标记失败，请刷新后重试");
            }
            return;
        }

        // 取消合格标记：待回样单本就不可能合格，仅已回样单可取消
        if (order.getSampleReturned() == null || order.getSampleReturned() != 1) {
            throw new RuntimeException("该送检单尚未回样，没有合格标记可取消");
        }
        UpdateWrapper<InspectionOrder> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .eq("sample_returned", 1)
                .set("qualified", 0)
                .set("qualified_time", null);
        baseMapper.update(null, wrapper);
    }

    // -------------------- 辅助方法 --------------------

    private InspectionOrder getRequiredOrder(Long id) {
        InspectionOrder order = getById(id);
        if (order == null) {
            throw new RuntimeException("送检单不存在");
        }
        return order;
    }

    private InspectionOrderVO buildVO(InspectionOrder order) {
        InspectionOrderVO vo = new InspectionOrderVO();
        vo.setId(order.getId());
        vo.setInspectionNo(order.getInspectionNo());
        vo.setAccessoryId(order.getAccessoryId());
        vo.setAccessoryName(order.getAccessoryName());
        vo.setModel(order.getModel());
        vo.setSpecUnit(order.getSpecUnit());
        vo.setBatchNo(order.getBatchNo());
        vo.setLabName(order.getLabName());
        vo.setSampleReturned(order.getSampleReturned());
        vo.setSampleReturnedText(order.getSampleReturned() != null && order.getSampleReturned() == 1
                ? "已回样" : "待回样");
        vo.setQualified(order.getQualified());
        vo.setQualifiedText(resolveQualifiedText(order));
        vo.setLabConclusion(order.getLabConclusion());
        vo.setSampleReturnTime(order.getSampleReturnTime());
        vo.setQualifiedTime(order.getQualifiedTime());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

    /** 合格文案：未回样时即使历史脏数据 qualified=1 也不展示“合格”，维持“合格 ⇒ 已回样”口径 */
    private String resolveQualifiedText(InspectionOrder order) {
        boolean returned = order.getSampleReturned() != null && order.getSampleReturned() == 1;
        if (returned && order.getQualified() != null && order.getQualified() == 1) {
            return "合格";
        }
        return returned ? "未判定合格" : "待回样，不可判定";
    }

    /**
     * 送检单号：SJ + 时间戳 + 3 位随机数；仅单号唯一键（uk_inspection_no）冲突时换随机后缀重试
     */
    private void insertWithUniqueOrderNo(InspectionOrder order) {
        DuplicateKeyException lastConflict = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            order.setInspectionNo("SJ" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000)));
            try {
                save(order);
                return;
            } catch (DuplicateKeyException e) {
                lastConflict = e;
            }
        }
        throw new RuntimeException("送检单编号生成冲突，请稍后重试", lastConflict);
    }
}
