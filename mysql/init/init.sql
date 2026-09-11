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
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记：0-正常，1-已删除（软删除，仍可在方案明细中展示但不可核销）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_zone_tag_id` (`zone_tag_id`),
  UNIQUE KEY `uk_model` (`model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安防配件档案表';

-- ----------------------------
-- 旧数据卷结构迁移：幂等补充现存量与软删除字段
-- 全新数据卷建表时已包含这两列；旧卷通过存储过程判断 information_schema 后再 ADD COLUMN
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
                   AND COLUMN_NAME = 'deleted') THEN
    ALTER TABLE `accessory`
      ADD COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记：0-正常，1-已删除（软删除）' AFTER `stock_quantity`;
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
-- ----------------------------
INSERT IGNORE INTO `accessory`
  (`accessory_name`, `model`, `material`, `scene`, `spec_min`, `spec_max`, `spec_unit`, `zone_tag_id`, `stock_quantity`, `remark`)
VALUES
('镀锌桥架', 'XQJ-C-200', '镀锌钢板', '弱电主干布线', 100, 500, 'mm', 1, 80, '标准弱电桥架'),
('槽式桥架', 'XQJ-C-300', '冷轧钢板', '机房布线', 50, 600, 'mm', 1, 30, '机房专用槽式桥架'),
('超五类网线', 'CAT5e-UTP', '铜芯', '网络布线', 0.5, 1.0, 'mm', 2, 1000, '非屏蔽双绞线'),
('六类网线', 'CAT6-UTP', '铜芯', '千兆网络布线', 0.5, 1.5, 'mm', 2, 600, '千兆网线'),
('RVV电源线', 'RVV-2*1.0', '铜芯', '设备供电', 0.5, 2.5, 'mm²', 2, 500, '两芯电源线'),
('BNC接头', 'BNC-75-5', '铜镀金', '同轴视频连接', 5, 9, 'mm', 3, 200, '视频线接头'),
('水晶头', 'RJ45-8P8C', '镀金', '网线终端', 0.5, 1.0, 'mm', 3, 80, '超五类水晶头'),
('接线端子', 'UK-2.5', '铜', '线缆连接', 1.5, 4.0, 'mm²', 3, 500, '导轨式接线端子'),
('扎带', 'ZD-4*200', '尼龙', '线缆绑扎', 100, 500, 'mm', 4, 400, '自锁式尼龙扎带'),
('线卡', 'XK-10', 'PVC', '线缆固定', 5, 20, 'mm', 4, 150, '圆形线卡'),
('防爆摄像头', 'DS-2CD3T46', '铝合金', '厂区外围监控', 2, 8, 'MP', 5, 12, '400万像素防爆摄像机'),
('半球摄像机', 'DS-2CD3346', '塑料', '室内监控', 2, 6, 'MP', 5, 20, '400万像素半球摄像机');

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
-- 每个方案至多一条核销记录（uk_plan_id），出库时一次性按明细扣减配件现存量
-- ----------------------------
CREATE TABLE IF NOT EXISTS `stock_writeoff` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_id` bigint NOT NULL COMMENT '布线方案ID',
  `plan_name` varchar(200) NOT NULL COMMENT '方案名称（出库时快照）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '核销时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_id` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方案出库核销记录表';

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
