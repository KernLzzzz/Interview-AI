package com.iflytek.interview.interview.controller;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.interview.entity.Scenario;
import com.iflytek.interview.interview.service.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
@Tag(name = "面试场景", description = "场景查询（所有角色）与维护（管理员）")
public class ScenarioController {

    @Autowired
    private ScenarioService scenarioService;

    /**
     * 场景列表，支持按技术领域筛选
     * 无筛选走 listAll()（缓存 key 带角色维度），有筛选走 listByField()
     * GET /api/scenarios?techField=AI
     */
    @PreAuthorize("hasAuthority('scenario:view')")
    @GetMapping
    @Operation(summary = "场景列表", description = "支持按技术领域筛选，候选人要选场景，所以只读也需登录+权限")
    public Result<List<Scenario>> list(@RequestParam(required = false) String techField) {
        List<Scenario> list = (techField == null || techField.isEmpty())
                ? scenarioService.listAll()
                : scenarioService.listByField(techField);
        return Result.success(list);
    }

    /**
     * 场景详情：下架场景仅 admin 可看（Service 内校验）
     */
    @PreAuthorize("hasAuthority('scenario:view')")
    @GetMapping("/{id}")
    @Operation(summary = "场景详情", description = "下架场景仅 admin 可见（Service 内校验）")
    public Result<Scenario> getScenario(@PathVariable Long id) {
        return Result.success(scenarioService.getScenario(id));
    }

    /**
     * 新增场景：管理端操作，需 scenario:create 权限
     */
    @PreAuthorize("hasAuthority('scenario:create')")
    @PostMapping
    @Operation(summary = "新增场景", description = "管理员操作，写库后自动清场景缓存")
    public Result<Scenario> create(@RequestBody Scenario scenario) {
        scenarioService.create(scenario);
        return Result.success(scenario);
    }

    /**
     * 修改场景：管理端操作，需 scenario:update 权限
     */
    @PreAuthorize("hasAuthority('scenario:update')")
    @PutMapping("/{id}")
    @Operation(summary = "修改场景", description = "管理员操作，写库后自动清场景缓存")
    public Result<Scenario> update(@PathVariable Long id, @RequestBody Scenario scenario) {
        if (scenarioService.getById(id) == null) {
            throw new BusinessException(ErrorCode.SCENARIO_NOT_FOUND);
        }
        scenario.setId(id);
        scenarioService.update(scenario);
        return Result.success(scenario);
    }

    /**
     * 删除场景：管理端操作，需 scenario:delete 权限
     */
    @PreAuthorize("hasAuthority('scenario:delete')")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除场景", description = "管理员操作，写库后自动清场景缓存")
    public Result<Void> delete(@PathVariable Long id) {
        if (scenarioService.getById(id) == null) {
            throw new BusinessException(ErrorCode.SCENARIO_NOT_FOUND);
        }
        scenarioService.delete(id);
        return Result.success();
    }
}
