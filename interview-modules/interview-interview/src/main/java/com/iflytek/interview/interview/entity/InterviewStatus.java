package com.iflytek.interview.interview.entity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试状态机：定义合法的状态转换
 */
public enum InterviewStatus {

    PENDING("pending", "待开始"),
    ONGOING("ongoing", "进行中"),
    COMPLETED("completed", "已完成"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String desc;

    // 合法转换表：from → 允许的 to 列表
    private static final Map<String, List<String>> TRANSITIONS = new HashMap<>();

    static {
        TRANSITIONS.put("pending",   Arrays.asList("ongoing", "cancelled"));
        TRANSITIONS.put("ongoing",   Arrays.asList("completed"));
        TRANSITIONS.put("completed", Collections.emptyList());  // 终态
        TRANSITIONS.put("cancelled", Collections.emptyList());  // 终态
    }

    InterviewStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 是否允许 from → to */
    public static boolean canTransition(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Collections.emptyList()).contains(to);
    }
}
