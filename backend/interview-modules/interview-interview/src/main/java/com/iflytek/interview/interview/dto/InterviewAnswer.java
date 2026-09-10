package com.iflytek.interview.interview.dto;

/** 服务端根据快照生成的标准答题数据，题目内容不能由客户端伪造。 */
public record InterviewAnswer(Long id, String content, String answer) {
}
