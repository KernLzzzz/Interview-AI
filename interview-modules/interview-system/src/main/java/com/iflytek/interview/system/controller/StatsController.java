package com.iflytek.interview.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.entity.UserRole;
import com.iflytek.interview.system.mapper.RoleMapper;
import com.iflytek.interview.system.mapper.UserRoleMapper;
import com.iflytek.interview.system.vo.StatsOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "仪表盘统计", description = "管理员看全局，其他角色看自己的")
public class StatsController {

    @Autowired
    private InterviewRecordMapper interviewRecordMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private RoleMapper roleMapper;

    /**
     * 仪表盘概览：按角色分数据权限
     */
    @GetMapping("/overview")
    @Operation(summary = "仪表盘概览", description = "管理员返回全量统计，其他角色只看自己的面试记录统计")
    public Result<StatsOverviewVO> overview() {
        boolean admin = SecurityUtil.hasRole("admin");

        List<InterviewRecord> records;
        if (admin) {
            records = interviewRecordMapper.selectList(null);
        } else {
            Long userId = SecurityUtil.getCurrentUserId();
            records = interviewRecordMapper.selectList(
                    new LambdaQueryWrapper<InterviewRecord>().eq(InterviewRecord::getUserId, userId));
        }

        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setTotal(records.size());
        vo.setPending(count(records, "pending"));
        vo.setOngoing(count(records, "ongoing"));
        vo.setCompleted(count(records, "completed"));
        vo.setCancelled(count(records, "cancelled"));

        // 平均分 / 通过率（completed 且已评分）
        List<InterviewRecord> scored = records.stream()
                .filter(r -> "completed".equals(r.getStatus()) && r.getScore() != null)
                .collect(Collectors.toList());
        if (!scored.isEmpty()) {
            double avg = scored.stream().mapToInt(r -> r.getScore().intValue()).average().orElse(0);
            vo.setAvgScore((int) Math.round(avg));
            long pass = scored.stream().filter(r -> r.getScore().intValue() >= 60).count();
            vo.setPassRate((int) Math.round(pass * 100.0 / scored.size()));
        }

        // 今日完成
        LocalDate today = LocalDate.now();
        vo.setCompletedToday(records.stream()
                .filter(r -> "completed".equals(r.getStatus())
                        && r.getCompletedAt() != null
                        && r.getCompletedAt().toLocalDate().equals(today))
                .count());

        // 候选人总数（注册用户不写 sys_user.role 列，走 user_role 关联表）
        Role candidateRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, "candidate"));
        if (candidateRole != null) {
            vo.setTotalCandidates(userRoleMapper.selectCount(
                    new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, candidateRole.getId())));
        }

        vo.setTrend(buildTrend(records));

        vo.setRecent(records.stream()
                .sorted(Comparator.comparing(InterviewRecord::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(r -> {
                    StatsOverviewVO.RecentRecord rr = new StatsOverviewVO.RecentRecord();
                    rr.setId(r.getId());
                    rr.setScenarioId(r.getScenarioId());
                    rr.setStatus(r.getStatus());
                    rr.setScore(r.getScore() == null ? null : r.getScore().intValue());
                    rr.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
                    return rr;
                })
                .collect(Collectors.toList()));

        return Result.success(vo);
    }

    private long count(List<InterviewRecord> records, String status) {
        return records.stream().filter(r -> status.equals(r.getStatus())).count();
    }

    /** 近 7 天（含今天）按创建日期分组：当天面试数 + 当天 completed 平均分 */
    private List<StatsOverviewVO.TrendPoint> buildTrend(List<InterviewRecord> records) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate today = LocalDate.now();
        List<StatsOverviewVO.TrendPoint> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<InterviewRecord> dayRecords = records.stream()
                    .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().toLocalDate().equals(day))
                    .collect(Collectors.toList());
            StatsOverviewVO.TrendPoint tp = new StatsOverviewVO.TrendPoint();
            tp.setDate(day.format(fmt));
            tp.setCount(dayRecords.size());
            List<InterviewRecord> scored = dayRecords.stream()
                    .filter(r -> "completed".equals(r.getStatus()) && r.getScore() != null)
                    .collect(Collectors.toList());
            if (!scored.isEmpty()) {
                tp.setAvgScore((int) Math.round(scored.stream()
                        .mapToInt(r -> r.getScore().intValue()).average().orElse(0)));
            }
            trend.add(tp);
        }
        return trend;
    }
}
