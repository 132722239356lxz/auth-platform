-- 用户反馈表
CREATE TABLE IF NOT EXISTS sys_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL COMMENT '提交用户',
    content TEXT NOT NULL COMMENT '反馈内容',
    contact VARCHAR(50) DEFAULT NULL COMMENT '联系方式',
    type VARCHAR(20) DEFAULT 'suggestion' COMMENT '类型: bug/suggestion/other',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/resolved',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    INDEX idx_username (username),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈表';

-- 用户表添加头像字段
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS avatar MEDIUMTEXT DEFAULT NULL COMMENT '头像(Base64/URL)' AFTER last_login_ip;
