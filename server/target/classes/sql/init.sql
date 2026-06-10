CREATE DATABASE IF NOT EXISTS step_counter DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE step_counter;

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt加密后的密码',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称，排行榜上用的名字',
    `token_version` INT NOT NULL DEFAULT 0 COMMENT 'token版本号，改密码时+1使旧token失效',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `step_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '关联用户ID',
    `step_date` DATE NOT NULL COMMENT '步数日期',
    `step_count` INT NOT NULL DEFAULT 0 COMMENT '当天总步数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次写入时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `step_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='步数记录表';
