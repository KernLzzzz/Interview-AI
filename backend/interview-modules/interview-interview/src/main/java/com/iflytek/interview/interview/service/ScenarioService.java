package com.iflytek.interview.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.interview.entity.Scenario;

import java.util.List;

public interface ScenarioService extends IService<Scenario> {

    /** 场景全量列表：缓存 key 带角色维度 */
    List<Scenario> listAll();

    /** 按技术领域查列表：所有角色结果一致，key 用参数 */
    List<Scenario> listByField(String field);

    /** 场景详情：下架场景仅 admin 可看 */
    Scenario getScenario(Long id);

    /** 新增场景：写库并清空场景缓存 */
    Scenario create(Scenario scenario);

    /** 修改场景：写库并清空场景缓存 */
    Scenario update(Scenario scenario);

    /** 删除场景：写库并清空场景缓存 */
    void delete(Long id);
}
