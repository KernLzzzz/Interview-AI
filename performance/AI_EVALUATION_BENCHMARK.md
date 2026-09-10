# AI 评测基准

评测系统采用两层验证：离线引擎使用固定黄金样本防止评分规则回归；远程模型网关使用模拟的 OpenAI-compatible HTTP 服务验证请求、结构校验与结果归一化，不消耗真实 API Key。

## 执行

```bash
cd backend
./mvnw -Dtest=LocalEvaluationBenchmarkTest,RemoteAiEvaluationGatewayTest test
```

黄金样本位于 `interview-modules/interview-interview/src/test/resources/evaluation-benchmark.json`，当前覆盖完整回答、部分回答和空回答。每条样本声明允许分数区间，修改评分策略后必须先解释区间变化，再更新数据集。

接入真实模型后，应扩展为至少 50 条匿名样本，并由两名面试官独立评分。推荐记录：

- 模型与提示词版本；
- 与人工平均分的 MAE、Spearman 相关系数；
- JSON 合规率、请求成功率与 P95 延迟；
- 单次评测 Token 数和成本；
- 不同岗位、难度与回答长度的分层结果。

未经真实数据验证，不应在简历或 README 中宣称模型准确率达到某个百分比。
