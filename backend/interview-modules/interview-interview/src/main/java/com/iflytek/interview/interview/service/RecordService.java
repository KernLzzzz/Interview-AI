package com.iflytek.interview.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.dto.SubmitInterviewDTO;
import com.iflytek.interview.interview.dto.CreateInterviewDTO;
import com.iflytek.interview.interview.vo.EvaluationTaskVO;

import java.util.List;

public interface RecordService extends IService<InterviewRecord> {

    /** 创建面试记录（pending） */
    InterviewRecord createRecord(Long userId, CreateInterviewDTO request);

    /** 开始面试：pending → ongoing */
    InterviewRecord startInterview(Long recordId, Long userId);

    /** 提交面试：ongoing → completed，落答案 */
    EvaluationTaskVO submitInterview(Long recordId, Long userId, SubmitInterviewDTO submission);

    /** 取消面试：pending → cancelled */
    void cancelInterview(Long recordId, Long userId);

    /** 列表：按数据权限返回（管理员看全部，其他角色只看自己的） */
    List<InterviewRecord> listForCurrentUser();
}
