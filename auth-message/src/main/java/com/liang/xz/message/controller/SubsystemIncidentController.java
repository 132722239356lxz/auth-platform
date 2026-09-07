package com.liang.xz.message.controller;

import com.liang.xz.message.dto.R;
import com.liang.xz.message.dto.SubsystemIncidentReport;
import com.liang.xz.message.entity.SubsystemIncident;
import com.liang.xz.message.service.SubsystemIncidentService;
import com.liang.xz.resource.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>子系统异常反馈接口</p>
 *
 * <p>子系统出问题时调用 {@code /report} 上报异常, 门户通过 {@code /list}、{@code /stats} 查看并展示红点告警。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Tag(name = "子系统异常反馈", description = "子系统异常上报与门户告警")
@RestController
@RequestMapping("/api/subsystem/incident")
@RequiredArgsConstructor
public class SubsystemIncidentController {

    private final SubsystemIncidentService incidentService;

    @Operation(summary = "子系统异常上报")
    @RequirePermission("message:incident:report")
    @PostMapping("/report")
    public R<Long> report(@Valid @RequestBody SubsystemIncidentReport report) {
        return R.ok(incidentService.report(report));
    }

    @Operation(summary = "查询异常列表", description = "门户告警页使用, 可按子系统/级别/状态过滤, 支持分页")
    @RequirePermission("message:incident:list")
    @GetMapping("/list")
    public R<Map<String, Object>> list(
            @RequestParam(required = false) String subsystem,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        SubsystemIncidentService.IncidentQueryResult result =
                incidentService.list(subsystem, level, status, page, size);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", result.getTotal());
        map.put("list", result.getList());
        return R.ok(map);
    }

    @Operation(summary = "异常统计", description = "门户红点: 返回未处理数与紧急未处理数")
    @RequirePermission("message:incident:list")
    @GetMapping("/stats")
    public R<SubsystemIncidentService.SubsystemIncidentStats> stats() {
        return R.ok(incidentService.stats());
    }

    @Operation(summary = "标记已处理")
    @RequirePermission("message:incident:resolve")
    @PutMapping("/{id}/resolve")
    public R<Void> resolve(@PathVariable Long id,
                           @RequestParam(required = false, defaultValue = "admin") String resolver,
                           @RequestParam(required = false) String note) {
        incidentService.resolve(id, resolver, note);
        return R.ok();
    }

    @Operation(summary = "忽略异常(标记为IGNORED)")
    @RequirePermission("message:incident:resolve")
    @PutMapping("/{id}/ignore")
    public R<Void> ignore(@PathVariable Long id,
                          @RequestParam(required = false, defaultValue = "admin") String resolver,
                          @RequestParam(required = false) String note) {
        incidentService.ignore(id, resolver, note);
        return R.ok();
    }

    @Operation(summary = "删除异常记录")
    @RequirePermission("message:incident:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        incidentService.delete(id);
        return R.ok();
    }
}
