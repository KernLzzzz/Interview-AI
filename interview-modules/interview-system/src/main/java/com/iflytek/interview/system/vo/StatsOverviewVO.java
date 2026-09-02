package com.iflytek.interview.system.vo;

import lombok.Data;

import java.util.List;

/**
 * 仪表盘统计概览（管理员看全局，其他角色看自己的）
 */
@Data
public class StatsOverviewVO {

    private long total;
    private long pending;
    private long ongoing;
    private long completed;
    private long cancelled;

    /** 已完成且已评分的平均分（0-100），无则 null */
    private Integer avgScore;

    /** 通过率：评分 ≥60 占已完成且已评分的百分比 */
    private Integer passRate;

    /** 今天完成的场次 */
    private long completedToday;

    /** 候选人总数（经 user_role 关联 role.code=candidate） */
    private long totalCandidates;

    /** 近 7 天趋势（含今天） */
    private List<TrendPoint> trend;

    /** 最近 5 条记录 */
    private List<RecentRecord> recent;

    @Data
    public static class TrendPoint {
        private String date;      // yyyy-MM-dd
        private long count;       // 当天创建的面试数
        private Integer avgScore; // 当天 completed 平均分，无则 null
    }

    @Data
    public static class RecentRecord {
        private Long id;
        private Long scenarioId;
        private String status;
        private Integer score;
        private String createdAt;
    }
}
