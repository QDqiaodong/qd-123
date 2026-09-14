-- 厂区安防布线配件存放分区归类系统 - 幂等数据库迁移脚本
-- 使用 CREATE TABLE IF NOT EXISTS + INSERT IGNORE，避免旧数据卷 schema 不一致导致启动失败

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 库房分区标签表（幂等建表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `zone_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tag_name` varchar(100) NOT NULL COMMENT '分区标签名称',
  `tag_code` varchar(50) NOT NULL COMMENT '分区标签编码',
  `sort_order` int DEFAULT 0 COMMENT '排序号',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_code` (`tag_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库房分区标签表';

-- ----------------------------
-- 安防配件档案表（幂等建表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `accessory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `accessory_name` varchar(200) NOT NULL COMMENT '配件名称',
  `model` varchar(200) NOT NULL COMMENT '型号',
  `material` varchar(100) DEFAULT NULL COMMENT '材质',
  `scene` varchar(200) DEFAULT NULL COMMENT '适配布线场景',
  `spec_min` decimal(10,2) DEFAULT NULL COMMENT '规格最小值',
  `spec_max` decimal(10,2) DEFAULT NULL COMMENT '规格最大值',
  `spec_unit` varchar(20) DEFAULT NULL COMMENT '规格单位',
  `zone_tag_id` bigint DEFAULT NULL COMMENT '所属分区标签ID',
  `stock_quantity` int NOT NULL DEFAULT 0 COMMENT '现存量（库存数量）',
  `safety_stock` int DEFAULT NULL COMMENT '安全库存下限：NULL 表示未设下限，不进安全库存台账；非空且现存量低于该值即列入台账',
  `replenish_order_id` bigint DEFAULT NULL COMMENT '待补补货单ID：非空表示已被一张已提交补货单占用；作废时清空',
  `replenish_order_no` varchar(40) DEFAULT NULL COMMENT '待补补货单号（提交时快照，档案直接展示）',
  `replenish_pending_quantity` int DEFAULT NULL COMMENT '待补数量（提交补货单时快照的补货数量），作废时清空',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记：0-正常，1-已删除（软删除，仍可在方案明细中展示但不可核销）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_zone_tag_id` (`zone_tag_id`),
  UNIQUE KEY `uk_model` (`model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安防配件档案表';

-- ----------------------------
-- 旧数据卷结构迁移：幂等补充现存量、软删除与安全库存下限字段
-- 全新数据卷建表时已包含这些列；旧卷通过存储过程判断 information_schema 后再 ADD COLUMN
-- ----------------------------
DROP PROCEDURE IF EXISTS `add_accessory_columns`;
DELIMITER //
CREATE PROCEDURE `add_accessory_columns`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'accessory'
                   AND COLUMN_NAME = 'stock_quantity') THEN
    ALTER TABLE `accessory`
      ADD COLUMN `stock_quantity` int NOT NULL DEFAULT 0 COMMENT '现存量（库存数量）' AFTER `zone_tag_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'accessory'
                   AND COLUMN_NAME = 'safety_stock') THEN
    ALTER TABLE `accessory`
      ADD COLUMN `safety_stock` int DEFAULT NULL COMMENT '安全库存下限：NULL 未设下限' AFTER `stock_quantity`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'accessory'
                   AND COLUMN_NAME = 'deleted') THEN
    ALTER TABLE `accessory`
      ADD COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记：0-正常，1-已删除（软删除）' AFTER `safety_stock`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'accessory'
                   AND COLUMN_NAME = 'replenish_order_id') THEN
    ALTER TABLE `accessory`
      ADD COLUMN `replenish_order_id` bigint DEFAULT NULL COMMENT '待补补货单ID：非空表示已被一张已提交补货单占用' AFTER `safety_stock`,
      ADD COLUMN `replenish_order_no` varchar(40) DEFAULT NULL COMMENT '待补补货单号（提交时快照）' AFTER `replenish_order_id`,
      ADD COLUMN `replenish_pending_quantity` int DEFAULT NULL COMMENT '待补数量（提交补货单时快照）' AFTER `replenish_order_no`,
      ADD KEY `idx_replenish_order_id` (`replenish_order_id`);
  END IF;
END //
DELIMITER ;
CALL `add_accessory_columns`();
DROP PROCEDURE IF EXISTS `add_accessory_columns`;

-- ----------------------------
-- 初始化分区标签数据（幂等插入，按唯一键 tag_code 去重）
-- ----------------------------
INSERT IGNORE INTO `zone_tag` (`tag_name`, `tag_code`, `sort_order`, `remark`) VALUES
('弱电桥架区', 'ZONE-BRIDGE-01', 1, '弱电桥架及配件存放区'),
('线缆布线区', 'ZONE-CABLE-02', 2, '各类安防线缆存放区'),
('接头终端区', 'ZONE-CONNECTOR-03', 3, '接头、端子、终端设备存放区'),
('辅材工具区', 'ZONE-TOOLS-04', 4, '布线辅材及工具存放区'),
('监控设备区', 'ZONE-CAMERA-05', 5, '监控摄像头及配套设备区');

-- ----------------------------
-- 初始化配件数据（幂等插入，按唯一索引 model 去重，避免重复导入）
-- 现存量按库房常备数量初始化，部分故意低于方案需求以体现库存缺口
-- safety_stock 为安全库存下限：仅部分配件设置，留空（NULL）表示不设下限、不进安全库存台账；
-- 部分设置项故意让现存量低于下限（如六类网线），以便台账直接体现采购缺口
-- ----------------------------
INSERT IGNORE INTO `accessory`
  (`accessory_name`, `model`, `material`, `scene`, `spec_min`, `spec_max`, `spec_unit`, `zone_tag_id`, `stock_quantity`, `safety_stock`, `remark`)
VALUES
('镀锌桥架', 'XQJ-C-200', '镀锌钢板', '弱电主干布线', 100, 500, 'mm', 1, 80, 100, '标准弱电桥架'),
('槽式桥架', 'XQJ-C-300', '冷轧钢板', '机房布线', 50, 600, 'mm', 1, 30, NULL, '机房专用槽式桥架'),
('超五类网线', 'CAT5e-UTP', '铜芯', '网络布线', 0.5, 1.0, 'mm', 2, 1000, 800, '非屏蔽双绞线'),
('六类网线', 'CAT6-UTP', '铜芯', '千兆网络布线', 0.5, 1.5, 'mm', 2, 600, 800, '千兆网线'),
('RVV电源线', 'RVV-2*1.0', '铜芯', '设备供电', 0.5, 2.5, 'mm²', 2, 500, NULL, '两芯电源线'),
('BNC接头', 'BNC-75-5', '铜镀金', '同轴视频连接', 5, 9, 'mm', 3, 200, 200, '视频线接头'),
('水晶头', 'RJ45-8P8C', '镀金', '网线终端', 0.5, 1.0, 'mm', 3, 80, 100, '超五类水晶头'),
('接线端子', 'UK-2.5', '铜', '线缆连接', 1.5, 4.0, 'mm²', 3, 500, NULL, '导轨式接线端子'),
('扎带', 'ZD-4*200', '尼龙', '线缆绑扎', 100, 500, 'mm', 4, 400, 300, '自锁式尼龙扎带'),
('线卡', 'XK-10', 'PVC', '线缆固定', 5, 20, 'mm', 4, 150, 200, '圆形线卡'),
('防爆摄像头', 'DS-2CD3T46', '铝合金', '厂区外围监控', 2, 8, 'MP', 5, 12, 15, '400万像素防爆摄像机'),
('半球摄像机', 'DS-2CD3346', '塑料', '室内监控', 2, 6, 'MP', 5, 20, NULL, '400万像素半球摄像机');

-- 旧数据卷的存量配件在迁移补列后现存量为 0，按型号回填演示库存（仅对仍是 0 值的行生效，不覆盖人工调整或核销扣减后的结果）
UPDATE `accessory` SET `stock_quantity` = 80   WHERE `model` = 'XQJ-C-200'  AND `stock_quantity` = 0;
UPDATE `accessory` SET `stock_quantity` = 30   WHERE `model` = 'XQJ-C-300'  AND `stock_quantity` = 0;
UPDATE `accessory` SET `stock_quantity` = 1000 WHERE `model` = 'CAT5e-UTP'  AND `stock_quantity` = 0;
UPDATE `accessory` SET `stock_quantity` = 600  WHERE `model` = 'CAT6-UTP'   AND `stock_quantity` = 0;
UPDATE `accessory` SET `stock_quantity` = 500  WHERE `model` = 'RVV-2*1.0'  AND `stock_quantity` = 0;
UPDATE `accessory` SET `stock_quantity` = 200  WHERE `model` = 'BNC-75-5'   AND `stock_quantity` = 0;
UPDATE `accessory` SET `stock_quantity` = 80   WHERE `model` = 'RJ45-8P8C'  AND `stock_quantity` = 0;
UPDATE `accessory` SET `stock_quantity` = 500  WHERE `model` = 'UK-2.5'     AND `stock_quantity` = 0;
UPDATE `accessory` SET `stock_quantity` = 400  WHERE `model` = 'ZD-4*200'   AND `stock_quantity` = 0;
UPDATE `accessory` SET `stock_quantity` = 150  WHERE `model` = 'XK-10'      AND `stock_quantity` = 0;
UPDATE `accessory` SET `stock_quantity` = 12   WHERE `model` = 'DS-2CD3T46' AND `stock_quantity` = 0;
UPDATE `accessory` SET `stock_quantity` = 20   WHERE `model` = 'DS-2CD3346' AND `stock_quantity` = 0;

-- 安全库存下限刻意不做旧卷回填：NULL 同时表示“从未设置”与“人工清空”，回填会把用户主动清空的下限
-- 重新写回、让本已移出台账的配件再次报警。旧卷补列后默认 NULL（不监控），由库房在档案中按需设置；
-- 全新数据卷的 INSERT 数据已自带演示下限。

-- ----------------------------
-- 布线方案表（幂等建表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `wiring_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_name` varchar(200) NOT NULL COMMENT '方案名称',
  `scene` varchar(200) DEFAULT NULL COMMENT '适用场景',
  `description` varchar(500) DEFAULT NULL COMMENT '方案说明',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态：0-停用，1-启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_name` (`plan_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='布线方案表';

-- ----------------------------
-- 布线方案配件明细表（幂等建表），同一方案内配件唯一
-- ----------------------------
CREATE TABLE IF NOT EXISTS `wiring_plan_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_id` bigint NOT NULL COMMENT '布线方案ID',
  `accessory_id` bigint NOT NULL COMMENT '配件ID',
  `quantity` int NOT NULL COMMENT '需求数量',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_accessory` (`plan_id`, `accessory_id`),
  KEY `idx_accessory_id` (`accessory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='布线方案配件明细表';

-- ----------------------------
-- 方案出库核销记录表（幂等建表）
-- 每个方案至多一条核销记录（uk_plan_id），出库时一次性按明细扣减配件现存量；
-- 领料人、领料说明为对账必填项：核销出库即领料出库，缺领料人对账时无法对应是谁领的
-- ----------------------------
CREATE TABLE IF NOT EXISTS `stock_writeoff` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_id` bigint NOT NULL COMMENT '布线方案ID',
  `plan_name` varchar(200) NOT NULL COMMENT '方案名称（出库时快照）',
  `receiver` varchar(100) NOT NULL DEFAULT '' COMMENT '领料人（核销出库时必填）',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '领料说明（核销出库时必填，说明领用用途等）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '核销时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_id` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方案出库核销记录表';

-- ----------------------------
-- 旧数据卷结构迁移：幂等补充领料人、领料说明列
-- 全新数据卷建表时已包含这些列；旧卷通过存储过程判断 information_schema 后再 ADD COLUMN。
-- 历史核销记录的领料信息无法追溯，以空串占位；服务层会对新核销强制非空校验
-- ----------------------------
DROP PROCEDURE IF EXISTS `add_stock_writeoff_columns`;
DELIMITER //
CREATE PROCEDURE `add_stock_writeoff_columns`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'stock_writeoff'
                   AND COLUMN_NAME = 'receiver') THEN
    ALTER TABLE `stock_writeoff`
      ADD COLUMN `receiver` varchar(100) NOT NULL DEFAULT '' COMMENT '领料人（核销出库时必填）' AFTER `plan_name`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'stock_writeoff'
                   AND COLUMN_NAME = 'remark') THEN
    ALTER TABLE `stock_writeoff`
      ADD COLUMN `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '领料说明（核销出库时必填）' AFTER `receiver`;
  END IF;
END //
DELIMITER ;
CALL `add_stock_writeoff_columns`();
DROP PROCEDURE IF EXISTS `add_stock_writeoff_columns`;

-- ----------------------------
-- 配件分区调整流水表（幂等建表）
-- 每次通过“调整分区”操作都写一条流水：原分区、目标分区、调整原因、时间；
-- 配件档案当前所属分区以最近一条流水为准，刷新后二者必须一致
-- ----------------------------
CREATE TABLE IF NOT EXISTS `zone_adjust_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `accessory_id` bigint NOT NULL COMMENT '配件ID',
  `accessory_name` varchar(200) NOT NULL COMMENT '配件名称（调整时快照）',
  `from_zone_tag_id` bigint DEFAULT NULL COMMENT '原分区标签ID，NULL 表示原未分配分区',
  `from_zone_name` varchar(100) DEFAULT NULL COMMENT '原分区名称（调整时快照）',
  `to_zone_tag_id` bigint DEFAULT NULL COMMENT '目标分区标签ID，NULL 表示调整为未分配分区',
  `to_zone_name` varchar(100) DEFAULT NULL COMMENT '目标分区名称（调整时快照）',
  `reason` varchar(500) NOT NULL COMMENT '调整原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '调整时间',
  PRIMARY KEY (`id`),
  KEY `idx_accessory_id` (`accessory_id`),
  KEY `idx_from_zone` (`from_zone_tag_id`),
  KEY `idx_to_zone` (`to_zone_tag_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配件分区调整流水表';

-- ----------------------------
-- 分区盘点单表（幂等建表）
-- 按分区开盘：zone_tag_id 为 NULL 表示“未分配分区”；同一分区允许重复盘点，
-- 但同一分区同时只允许一张待确认盘点单（uk_pending_zone 仅作用于 status=0）。
-- status=0 待确认（可反复登记实盘数，不动库存）；status=1 已确认（库存已一次性回写，单据只读）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `stock_check` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `check_no` varchar(40) NOT NULL COMMENT '盘点单号（业务编号，快照展示）',
  `zone_tag_id` bigint DEFAULT NULL COMMENT '盘点分区标签ID，NULL 表示未分配分区',
  `zone_name` varchar(100) NOT NULL COMMENT '分区名称（开盘时快照，分区标签删除后仍可展示）',
  `unassigned_zone` tinyint NOT NULL DEFAULT 0 COMMENT '是否未分配分区：0-否，1-是',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待确认（可登记实盘数），1-已确认（库存已回写，单据只读）',
  `item_count` int NOT NULL DEFAULT 0 COMMENT '明细配件种数（含已删除配件）',
  `diff_count` int NOT NULL DEFAULT 0 COMMENT '盘盈盘亏配件种数（已删除配件不参与）',
  `confirm_remark` varchar(500) DEFAULT NULL COMMENT '确认备注',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认回写时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  -- 生成列：仅待确认单取分区ID（未分配分区用 0 占位），已确认单恒为 NULL；
  -- 配合唯一索引实现“同一分区同时只允许一张待确认盘点单”，MySQL 唯一索引允许多个 NULL，
  -- 故同一分区历史上可保留多张已确认盘点单
  `pending_zone_key` bigint GENERATED ALWAYS AS (IF(`status` = 0, COALESCE(`zone_tag_id`, 0), NULL)) VIRTUAL COMMENT '待确认单分区唯一键（生成列）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_check_no` (`check_no`),
  UNIQUE KEY `uk_pending_zone` (`pending_zone_key`),
  KEY `idx_status` (`status`),
  KEY `idx_zone_tag_id` (`zone_tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分区盘点单表';

-- ----------------------------
-- 分区盘点单明细表（幂等建表）
-- 开盘时按分区把配件（含已软删除配件）全部带入并快照账面现存量；
-- 实盘数可反复登记，差异 = 实盘 - 账面；已删除配件只展示，确认时不回写库存
-- ----------------------------
CREATE TABLE IF NOT EXISTS `stock_check_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `check_id` bigint NOT NULL COMMENT '盘点单ID',
  `accessory_id` bigint NOT NULL COMMENT '配件ID',
  `accessory_name` varchar(200) NOT NULL COMMENT '配件名称（开盘时快照）',
  `model` varchar(200) NOT NULL COMMENT '型号（开盘时快照）',
  `spec_unit` varchar(20) DEFAULT NULL COMMENT '规格单位（开盘时快照）',
  `book_quantity` int NOT NULL COMMENT '账面现存量（开盘时快照）',
  `actual_quantity` int DEFAULT NULL COMMENT '实盘数量，NULL 表示尚未登记',
  `accessory_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '配件是否已删除：0-正常，1-已删除（只展示不回写）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_check_accessory` (`check_id`, `accessory_id`),
  KEY `idx_check_id` (`check_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分区盘点单明细表';

-- ----------------------------
-- 补货单表（幂等建表）
-- 仓管在安全库存台账勾选低于下限的配件生成：status=0 待提交草稿（可调整补货数量、可删除，
-- 不占用配件待补标记）；status=1 已提交（档案回填补货单号与待补数量，数量锁定不可再改）；
-- status=2 已作废（清除档案待补标记，单据只读留档）。单据按分区汇总缺口件数
-- ----------------------------
CREATE TABLE IF NOT EXISTS `replenish_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `replenish_no` varchar(40) NOT NULL COMMENT '补货单号（业务编号，快照展示）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待提交，1-已提交，2-已作废',
  `item_count` int NOT NULL DEFAULT 0 COMMENT '明细配件种数',
  `total_quantity` int NOT NULL DEFAULT 0 COMMENT '补货件数合计（随草稿调整实时回写）',
  `cancel_reason` varchar(500) DEFAULT NULL COMMENT '作废原因',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '作废时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_replenish_no` (`replenish_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补货单表';

-- ----------------------------
-- 补货单明细表（幂等建表）
-- 生成时从台账带入勾选的低位配件，快照配件名称、型号、单位、分区与实时缺口，
-- 补货数量默认等于缺口；草稿期间可调整（不得超过缺口），提交后锁定。
-- 单据按分区汇总各明细补货数量；同一补货单内配件唯一
-- ----------------------------
CREATE TABLE IF NOT EXISTS `replenish_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `replenish_id` bigint NOT NULL COMMENT '补货单ID',
  `accessory_id` bigint NOT NULL COMMENT '配件ID',
  `accessory_name` varchar(200) NOT NULL COMMENT '配件名称（生成时快照）',
  `model` varchar(200) NOT NULL COMMENT '型号（生成时快照）',
  `spec_unit` varchar(20) DEFAULT NULL COMMENT '规格单位（生成时快照）',
  `zone_tag_id` bigint DEFAULT NULL COMMENT '分区标签ID，NULL 表示未分配分区（生成时快照）',
  `zone_name` varchar(100) NOT NULL COMMENT '分区名称（生成时快照，分区标签删除后仍可展示）',
  `unassigned_zone` tinyint NOT NULL DEFAULT 0 COMMENT '是否未分配分区：0-否，1-是',
  `stock_quantity` int NOT NULL DEFAULT 0 COMMENT '生成时现存量快照',
  `safety_stock` int NOT NULL COMMENT '生成时安全库存下限快照',
  `gap_quantity` int NOT NULL COMMENT '生成时缺口（下限-现存量），恒大于0',
  `replenish_quantity` int NOT NULL COMMENT '补货数量：默认等于缺口，草稿可改，提交后锁定',
  `accessory_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '配件是否已删除：0-正常，1-已删除（随单展示，提交时拒绝）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_replenish_accessory` (`replenish_id`, `accessory_id`),
  KEY `idx_replenish_id` (`replenish_id`),
  KEY `idx_zone_tag_id` (`zone_tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补货单明细表';

-- ----------------------------
-- 整盘电源线（线缆盘）档案表（幂等建表）
-- 整盘电源线按盘号建档：盘号唯一、绑定一个配件（如 RVV 电源线）、登记盘上剩余米数。
-- 建档即“未开盘”（status=0），此时只登记不动配件米数；只有“开盘确认”后（status=1）
-- 才把整盘米数一次性落到配件档案现存量（米），之后才能从该盘扣米；未开过的盘一律不能扣米。
-- 同一配件同时只允许一个已开盘：打开的盘正是该配件在档案里的米数来源，
-- 靠生成列唯一索引 uk_open_accessory（仅 status=1 时取 accessory_id）兜底并发
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cable_reel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reel_no` varchar(60) NOT NULL COMMENT '盘号（业务编号，唯一，同一盘号不能建两次）',
  `accessory_id` bigint NOT NULL COMMENT '绑定的配件ID（按米计的线缆配件）',
  `accessory_name` varchar(200) NOT NULL COMMENT '配件名称（建档时快照）',
  `model` varchar(200) NOT NULL COMMENT '型号（建档时快照）',
  `spec_unit` varchar(20) DEFAULT NULL COMMENT '规格单位（建档时快照，通常为 m）',
  `remaining_meters` int NOT NULL COMMENT '盘上剩余米数（非负整数，建档即整盘米数）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-未开盘（仅建档，不能扣米），1-已开盘（已确认，可扣米）',
  `open_time` datetime DEFAULT NULL COMMENT '开盘确认时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  -- 生成列：仅已开盘取配件ID，未开盘恒为 NULL；配合唯一索引实现
  -- “同一配件同时只允许一个已开盘”，MySQL 唯一索引允许多个 NULL，故未开盘盘不限数量
  `open_accessory_key` bigint GENERATED ALWAYS AS (IF(`status` = 1, `accessory_id`, NULL)) VIRTUAL COMMENT '已开盘配件唯一键（生成列）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reel_no` (`reel_no`),
  UNIQUE KEY `uk_open_accessory` (`open_accessory_key`),
  KEY `idx_status` (`status`),
  KEY `idx_accessory_id` (`accessory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='整盘电源线（线缆盘）档案表';

-- ----------------------------
-- 光纤熔接接头登记表（幂等建表，仓管单独建账）
-- 记下接头编号、所属分区、盘留米数、是否过 OTDR：接头编号全局唯一（uk_splice_no）。
-- 未过 OTDR（otdr_passed=0）不能标记可投运（commissionable 必须为 0），
-- “可投运 ⇒ 已过 OTDR”由服务层在标记与编辑两处强制。
-- status=0 在档、1 已作废：作废只把状态置为已作废留档（记录作废原因/时间），
-- 系统不提供物理删除入口，作废行仍可在列表按“已作废”筛选查档
-- ----------------------------
CREATE TABLE IF NOT EXISTS `fiber_splice_joint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `splice_no` varchar(60) NOT NULL COMMENT '接头编号（业务编号，全局唯一）',
  `zone_tag_id` bigint NOT NULL COMMENT '所属分区标签ID（登记/调整时绑定）',
  `zone_name` varchar(100) NOT NULL COMMENT '分区名称（登记时快照，分区标签删除后仍可展示）',
  `reserve_meters` int NOT NULL COMMENT '盘留米数（非负整数）',
  `otdr_passed` tinyint NOT NULL DEFAULT 0 COMMENT '是否过 OTDR：0-未过，1-已过；未过不能标记可投运',
  `commissionable` tinyint NOT NULL DEFAULT 0 COMMENT '是否可投运：0-不可投运，1-可投运；仅已过 OTDR 才允许置1',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-在档，1-已作废（作废只留档，不物理删除，作废后只读）',
  `void_reason` varchar(500) DEFAULT NULL COMMENT '作废原因',
  `void_time` datetime DEFAULT NULL COMMENT '作废时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_splice_no` (`splice_no`),
  KEY `idx_zone_tag_id` (`zone_tag_id`),
  KEY `idx_status` (`status`),
  KEY `idx_otdr_passed` (`otdr_passed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='光纤熔接接头登记表';

-- ----------------------------
-- 初始化布线方案数据（幂等插入，按唯一键 plan_name 去重）
-- ----------------------------
INSERT IGNORE INTO `wiring_plan` (`id`, `plan_name`, `scene`, `description`, `status`) VALUES
(1, '厂区外围监控布线方案', '厂区外围监控', '外围周界监控点位的标准布线方案，含视频与供电线缆及配套辅材', 1),
(2, '机房网络布线方案', '机房布线', '机房内部千兆网络布线方案，含桥架、网线及终端配件', 1);

-- ----------------------------
-- 初始化布线方案明细数据（幂等插入，按唯一键 plan_id+accessory_id 去重）
-- ----------------------------
INSERT IGNORE INTO `wiring_plan_detail` (`plan_id`, `accessory_id`, `quantity`) VALUES
(1, 11, 12),
(1, 5, 600),
(1, 6, 24),
(1, 9, 300),
(2, 2, 50),
(2, 4, 800),
(2, 7, 100),
(2, 10, 200);

SET FOREIGN_KEY_CHECKS = 1;
