package com.iflytek.interview.interview.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.entity.Scenario;
import com.iflytek.interview.interview.mapper.ScenarioMapper;
import com.iflytek.interview.interview.service.ScenarioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ScenarioServiceImpl extends ServiceImpl<ScenarioMapper, Scenario>
        implements ScenarioService {

    // 场景列表：命中缓存就直接返回，未命中才查库并缓存
    // key 带角色维度（scenarios::true / scenarios::false），防止"admin 先访问后，候选人命中 admin 那份含下架场景的全量列表"
    @Cacheable(value = "scenarios",
            key = "T(com.iflytek.interview.common.security.SecurityUtil).hasRole('admin')")
    public List<Scenario> listAll() {
        log.info("【缓存未命中】查数据库：场景列表");
        boolean isAdmin = SecurityUtil.hasRole("admin");
        return this.lambdaQuery()
                .eq(!isAdmin, Scenario::getStatus, 1)   // admin 看全部（含下架），普通用户只看上架
                .orderByAsc(Scenario::getSortOrder)
                .list();
    }

    // 按领域查：所有角色结果一致，key 用参数即可
    @Cacheable(value = "scenarios", key = "#field")
    public List<Scenario> listByField(String field) {
        log.info("【缓存未命中】查数据库：场景列表 field={}", field);
        return this.lambdaQuery()
                .eq(Scenario::getStatus, 1)
                .eq(Scenario::getTechField, field)
                .list();
    }

    // 场景详情：下架场景仅 admin 可看（Controller 必须走这里，不能绕成 getById）
    public Scenario getScenario(Long id) {
        Scenario scenario = this.getById(id);
        if (scenario == null) {
            throw new BusinessException(ErrorCode.SCENARIO_NOT_FOUND);
        }
        if (scenario.getStatus() == 0 && !SecurityUtil.hasRole("admin")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return scenario;
    }

    // 新增场景：写库后清掉"scenarios"缓存空间的全部 key（否则旧数据一直命中缓存）
    @CacheEvict(value = "scenarios", allEntries = true)
    public Scenario create(Scenario scenario) {
        this.save(scenario);
        return scenario;
    }

    // 修改场景：写库后清缓存（同 create）
    @CacheEvict(value = "scenarios", allEntries = true)
    public Scenario update(Scenario scenario) {
        this.updateById(scenario);
        return scenario;
    }

    // 删除场景：写库后清缓存（同 create）
    @CacheEvict(value = "scenarios", allEntries = true)
    public void delete(Long id) {
        this.removeById(id);
    }
}
