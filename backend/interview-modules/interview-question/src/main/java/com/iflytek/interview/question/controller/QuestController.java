package com.iflytek.interview.question.controller;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.question.dto.QuestionDTO;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.service.QuestionService;
import com.iflytek.interview.question.service.QuestionBankCacheService;
import com.iflytek.interview.question.vo.AdminQuestionVO;
import com.iflytek.interview.question.vo.QuestionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/questions")
@Tag(name = "题库管理", description = "题目增删改查与随机抽题（候选人只读，管理员维护）")
public class QuestController {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private QuestionBankCacheService questionBankCacheService;

    private QuestionVO toVO(Question question) {
        QuestionVO vo = new QuestionVO();
        BeanUtils.copyProperties(question, vo);
        return vo;
    }

    private AdminQuestionVO toAdminVO(Question question) {
        AdminQuestionVO vo = new AdminQuestionVO();
        BeanUtils.copyProperties(question, vo);
        return vo;
    }

    /**
     * 题目列表，支持按场景、难度筛选（走 @Cacheable 缓存）
     * GET /api/questions?scenarioId=1&difficulty=2
     */
    @PreAuthorize("hasAuthority('question:view')")
    @GetMapping
    @Operation(summary = "题目列表", description = "支持按场景、难度筛选，屏蔽标准答案字段；二次查询走 Redis 缓存")
    public Result<List<QuestionVO>> list(
            @RequestParam(required = false) Long scenarioId,
            @RequestParam(required = false) Integer difficulty) {
        // 转 VO：屏蔽 expectedAnswer / keywords，标准答案不出边界
        return Result.success(questionService.listQuestions(scenarioId, difficulty)
                .stream().map(this::toVO).collect(Collectors.toList()));
    }

    /**
     * 题目详情
     */
    @PreAuthorize("hasAuthority('question:view')")
    @GetMapping("/{id}")
    @Operation(summary = "题目详情", description = "返回 QuestionVO，不含 expectedAnswer/keywords")
    public Result<QuestionVO> get(@PathVariable Long id) {
        Question question = questionService.getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        return Result.success(toVO(question));
    }

    /** 管理端编辑详情。候选人接口永远不返回参考答案与评分关键词。 */
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/admin/{id}")
    @Operation(summary = "管理端题目详情", description = "仅管理员可见参考答案与评分关键词")
    public Result<AdminQuestionVO> getForAdmin(@PathVariable Long id) {
        Question question = questionService.getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        return Result.success(toAdminVO(question));
    }

    @PreAuthorize("hasRole('admin')")
    @GetMapping("/cache/stats")
    @Operation(summary = "题库缓存统计", description = "返回进程内累计命中、未命中与降级次数")
    public Result<Map<String, Long>> cacheStats() {
        return Result.success(questionBankCacheService.stats());
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping("/cache/warmup")
    @Operation(summary = "手动预热题库缓存")
    public Result<Map<String, Integer>> warmup(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(Map.of("scenarioCount", questionService.warmupHotQuestionBanks(limit)));
    }

    /**
     * 随机抽题（3:5:2 比例）：面试开始时按难度比例抽 count 道，某难度不足自动补齐
     */
    @PreAuthorize("hasAuthority('question:view')")
    @GetMapping("/random")
    @Operation(summary = "随机抽题", description = "按难度比例 3:5:2（简单:中等:困难）从场景抽 count 道（默认5），不足自动补齐，返回 VO 不带答案")
    public Result<List<QuestionVO>> random(
            @RequestParam Long scenarioId,
            @RequestParam(defaultValue = "5") int count) {
        return Result.success(questionService.getRandomQuestionsByRatio(scenarioId, count)
                .stream().map(this::toVO).collect(Collectors.toList()));
    }

    /**
     * 新增题目：管理端操作，需 question:create 权限
     */
    @PreAuthorize("hasAuthority('question:create')")
    @PostMapping
    @Operation(summary = "新增题目", description = "管理员维护题库；含 expectedAnswer/keywords 入参")
    public Result<QuestionVO> create(@Valid @RequestBody QuestionDTO dto) {
        Question question = new Question();
        BeanUtils.copyProperties(dto, question);
        question.setId(null);   // 自增主键，防止客户端指定 id
        question.setStatus(1);  // 默认启用
        // Service 内 @CacheEvict 清题目缓存
        return Result.success(toVO(questionService.saveQuestion(question)));
    }

    /**
     * 修改题目：管理端操作，需 question:update 权限
     */
    @PreAuthorize("hasAuthority('question:update')")
    @PutMapping("/{id}")
    @Operation(summary = "修改题目", description = "管理员维护题库")
    public Result<QuestionVO> update(@PathVariable Long id, @Valid @RequestBody QuestionDTO dto) {
        if (questionService.getById(id) == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        Question question = new Question();
        BeanUtils.copyProperties(dto, question);
        question.setId(id);
        // Service 内 @CacheEvict 清题目缓存
        return Result.success(toVO(questionService.updateQuestion(question)));
    }

    /**
     * 删除题目：管理端操作，需 question:delete 权限
     */
    @PreAuthorize("hasAuthority('question:delete')")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除题目", description = "管理员维护题库")
    public Result<Void> delete(@PathVariable Long id) {
        if (questionService.getById(id) == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        // Service 内 @CacheEvict 清题目缓存
        questionService.deleteQuestion(id);
        return Result.success();
    }
}
