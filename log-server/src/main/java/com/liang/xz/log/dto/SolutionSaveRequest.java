package com.liang.xz.log.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保存/更新错误解决方案的请求
 *
 * @author liang
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolutionSaveRequest {

    /** 关联的错误指纹 */
    private String errorFingerprint;

    /** 错误特征描述 */
    private String errorPattern;

    /** 根因分析 */
    private String rootCause;

    /** 解决方案 */
    private String solution;

    /** 详细步骤(逗号分隔) */
    private String steps;

    /** 参考链接 */
    private String referenceUrl;

    /** 状态 DRAFT / PUBLISHED */
    private String status;
}
