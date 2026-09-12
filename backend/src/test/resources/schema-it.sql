-- 集成测试专用 schema（H2 MySQL 兼容模式），仅覆盖盘点回写链路涉及的表
CREATE TABLE IF NOT EXISTS `zone_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tag_name` varchar(100) NOT NULL,
  `tag_code` varchar(50) NOT NULL,
  `sort_order` int DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `accessory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `accessory_name` varchar(200) NOT NULL,
  `model` varchar(200) NOT NULL,
  `material` varchar(100) DEFAULT NULL,
  `scene` varchar(200) DEFAULT NULL,
  `spec_min` decimal(10,2) DEFAULT NULL,
  `spec_max` decimal(10,2) DEFAULT NULL,
  `spec_unit` varchar(20) DEFAULT NULL,
  `zone_tag_id` bigint DEFAULT NULL,
  `stock_quantity` int NOT NULL DEFAULT 0,
  `safety_stock` int DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `stock_check` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `check_no` varchar(40) NOT NULL,
  `zone_tag_id` bigint DEFAULT NULL,
  `zone_name` varchar(100) NOT NULL,
  `unassigned_zone` tinyint NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0,
  `item_count` int NOT NULL DEFAULT 0,
  `diff_count` int NOT NULL DEFAULT 0,
  `confirm_remark` varchar(500) DEFAULT NULL,
  `confirm_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_check_no` (`check_no`)
);

CREATE TABLE IF NOT EXISTS `stock_check_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `check_id` bigint NOT NULL,
  `accessory_id` bigint NOT NULL,
  `accessory_name` varchar(200) NOT NULL,
  `model` varchar(200) NOT NULL,
  `spec_unit` varchar(20) DEFAULT NULL,
  `book_quantity` int NOT NULL,
  `actual_quantity` int DEFAULT NULL,
  `accessory_deleted` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_check_accessory` (`check_id`, `accessory_id`)
);
