# 性能验证

目录内提供两个可直接参数化执行的 JMeter 5.6 测试计划：

- `question-cache.jmx`：预热后混合访问题库列表、难度筛选和随机抽题接口。
- `evaluation-submit.jmx`：使用 CSV 中互不重复的进行中面试记录，并发提交答案，验证接口只负责持久化与入队。

## 题库缓存

先准备管理员 JWT，然后执行：

```bash
jmeter -n -t performance/question-cache.jmx \
  -JBASE_URL=http://localhost:8080 \
  -JTOKEN=replace-with-admin-jwt \
  -JSCENARIO_ID=1 -JTHREADS=50 -JLOOPS=100 \
  -JRESULT_FILE=performance/results/question-cache.jtl
```

压测前后分别记录 MySQL 会话的 `Questions`、慢查询或 APM SQL 调用计数，并通过
`GET /api/questions/cache/stats` 核对命中数。验收口径：

```text
数据库查询下降率 = (baseline_selects - optimized_selects) / baseline_selects × 100%
目标：混合高频题库流量下降约 50% 或更多；错误率 < 0.1%。
```

纯热点读场景通常会明显高于 50%；将写后失效和多个冷门场景纳入混合流量，结果更接近生产环境。

## 异步评测

复制 `evaluation-submit.csv.example` 为 `evaluation-submit.csv`，每行准备一条不同的
`ongoing` 记录及其真实题目 ID，再执行：

```bash
jmeter -n -t performance/evaluation-submit.jmx \
  -JBASE_URL=http://localhost:8080 \
  -JDATA_FILE=performance/evaluation-submit.csv \
  -JTHREADS=20 -JRESULT_FILE=performance/results/evaluation-submit.jtl
```

基线版本为主线程同步执行评测，优化版本以接口返回 `taskId` 为完成点。验收口径：平均响应时间
提升约 40% 或更多，且 `task_log` 中任务数与成功提交数一致，没有静默丢失；失败任务的
`retry_count` 不超过 3。

不同硬件、题量和模型服务会改变绝对数值，提交报告时应保存 JTL、JMeter HTML 报告、测试参数
与数据库/APM 截图，避免只保留无法复现的百分比。
