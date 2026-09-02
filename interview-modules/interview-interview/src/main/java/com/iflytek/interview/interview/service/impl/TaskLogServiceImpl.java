package com.iflytek.interview.interview.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.interview.entity.TaskLog;
import com.iflytek.interview.interview.mapper.TaskLogMapper;
import com.iflytek.interview.interview.service.TaskLogService;
import org.springframework.stereotype.Service;

@Service
public class TaskLogServiceImpl extends ServiceImpl<TaskLogMapper, TaskLog> implements TaskLogService {
}
