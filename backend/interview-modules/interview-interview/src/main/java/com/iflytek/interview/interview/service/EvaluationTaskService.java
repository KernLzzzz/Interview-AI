package com.iflytek.interview.interview.service;

import com.iflytek.interview.interview.dto.EvaluationCallbackDTO;
import com.iflytek.interview.interview.entity.TaskLog;
import com.iflytek.interview.interview.vo.EvaluationMetricsVO;

public interface EvaluationTaskService {
    TaskLog submit(Long recordId, String callbackUrl);
    TaskLog get(Long taskId);
    TaskLog latestForRecord(Long recordId);
    void completeFromCallback(String token, EvaluationCallbackDTO callback);
    int retryDueTasks();
    EvaluationMetricsVO metrics();
}
