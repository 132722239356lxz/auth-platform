package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * <p>分页响应</p>
 *
 * @param <T> 数据项类型
 * @author auth-platform
 * @since 1.2.0
 */
@Data
@Schema(description = "分页响应")
public class PageResponse<T> {

    @Schema(description = "当前页数据列表")
    private List<T> records;

    @Schema(description = "总记录数", example = "100")
    private long total;

    @Schema(description = "当前页码", example = "1")
    private int page;

    @Schema(description = "每页条数", example = "10")
    private int pageSize;

    public static <T> PageResponse<T> empty(int page, int pageSize) {
        PageResponse<T> r = new PageResponse<>();
        r.records = Collections.emptyList();
        r.total = 0;
        r.page = page;
        r.pageSize = pageSize;
        return r;
    }

    public static <T> PageResponse<T> of(List<T> records, long total, int page, int pageSize) {
        PageResponse<T> r = new PageResponse<>();
        r.records = records;
        r.total = total;
        r.page = page;
        r.pageSize = pageSize;
        return r;
    }
}
