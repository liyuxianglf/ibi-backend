package com.yx.controller;

import java.util.Date;


import cn.hutool.core.io.FileUtil;
import cn.hutool.poi.excel.ExcelUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yx.common.BaseResponse;
import com.yx.common.ErrorCode;
import com.yx.common.ResultUtils;
import com.yx.constant.CommonConstant;
import com.yx.exception.BusinessException;
import com.yx.exception.ThrowUtils;
import com.yx.manager.AiManager;
import com.yx.manager.RedisLimiterManager;
import com.yx.model.dto.chart.ChartQueryRequest;
import com.yx.model.dto.chart.GenChartByAiRequest;
import com.yx.model.entity.Chart;
import com.yx.model.entity.User;
import com.yx.model.enums.ChartStatusEnum;
import com.yx.model.vo.BiResponse;
import com.yx.mq.BiMessageProducer;
import com.yx.service.ChartService;
import com.yx.service.UserService;
import com.yx.utils.ExcelUtils;
import com.yx.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;

/**
 * 图表接口
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    RedisLimiterManager redisLimiterManager;

    @Resource
    AiManager aiManager;
    @Resource
    UserService userService;

    @Resource
    ChartService chartService;

    @Resource
    ThreadPoolExecutor threadPoolExecutor;


    @Resource
    BiMessageProducer biMessageProducer;


    /**
     * 分页获取当前用户创建的包装类
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest, HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 智能分析
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        String goal = genChartByAiRequest.getGoal();
        String name = genChartByAiRequest.getName();
        String chartType = genChartByAiRequest.getChartType();
        //对参数进行校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isBlank(name), ErrorCode.PARAMS_ERROR, "名称为空");
        ThrowUtils.throwIf(name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        ThrowUtils.throwIf(StringUtils.isBlank(chartType), ErrorCode.PARAMS_ERROR, "图表类型为空");

        //对文件进行校验
        long size = multipartFile.getSize();
        final long ONE_MB = 1024 * 1024;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过1M");
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        //使用限流器对每个用户进行限流
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        String limiterKey = String.format("ibi:chart:genchartbyai:%s", userId);
        redisLimiterManager.doRateLimit(limiterKey);

        //使用easyExcel将原始数据压缩为csv
        String csvData = ExcelUtils.excelToCsv(multipartFile);

        long biModelId = CommonConstant.BI_MODEL_ID;

        //构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求:").append("\n");

        //拼接分析目标
        String userGoal = goal += "，请使用" + chartType;
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        userInput.append(csvData).append("\n");

        //对接AI
        String result = aiManager.doChat(biModelId, userInput.toString());
        String[] splits = result.split("【【【【【");
        if (splits.length < 3) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI生成错误");
        }
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();

        //插入到数据库
        Chart chart = new Chart();
        chart.setGoal(goal);
        chart.setName(name);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(userId);
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "图表保存失败");
        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }


    /**
     * 智能分析（异步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String goal = genChartByAiRequest.getGoal();
        String name = genChartByAiRequest.getName();
        String chartType = genChartByAiRequest.getChartType();
        //对参数进行校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isBlank(name), ErrorCode.PARAMS_ERROR, "名称为空");
        ThrowUtils.throwIf(name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        ThrowUtils.throwIf(StringUtils.isBlank(chartType), ErrorCode.PARAMS_ERROR, "图表类型为空");

        //对文件进行校验
        long size = multipartFile.getSize();
        final long ONE_MB = 1024 * 1024;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过1M");
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        //使用限流器对每个用户进行限流
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        String limiterKey = String.format("ibi:chart:genchartbyai:%s", userId);
        redisLimiterManager.doRateLimit(limiterKey);

        //使用easyExcel将原始数据压缩为csv
        String csvData = ExcelUtils.excelToCsv(multipartFile);

        long biModelId = CommonConstant.BI_MODEL_ID;

        //构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求:").append("\n");

        //拼接分析目标
        String userGoal = goal += "，请使用" + chartType;
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        userInput.append(csvData).append("\n");

        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus(ChartStatusEnum.WAIT.getValue());
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");


        try {
            CompletableFuture.runAsync(() -> {
                Chart updateChart = new Chart();
                updateChart.setId(chart.getId());
                updateChart.setStatus(ChartStatusEnum.RUNNING.getValue());
                boolean b = chartService.updateById(updateChart);
                if (!b) {
                    handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
                    return;
                }
                // 调用 AI
                String result = aiManager.doChat(biModelId, userInput.toString());
                String[] splits = result.split("【【【【【");
                if (splits.length < 3) {
                    handleChartUpdateError(chart.getId(), "AI 生成错误");
                    return;
                }
                String genChart = splits[1].trim();
                String genResult = splits[2].trim();
                Chart updateChartResult = new Chart();
                updateChartResult.setId(chart.getId());
                updateChartResult.setGenChart(genChart);
                updateChartResult.setGenResult(genResult);
                updateChartResult.setStatus(ChartStatusEnum.SUCCESS.getValue());
                boolean updateResult = chartService.updateById(updateChartResult);
                if (!updateResult) {
                    handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
                }
            }, threadPoolExecutor).exceptionally(new Function<Throwable, Void>() {
                @Override
                public Void apply(Throwable throwable) {
                    handleChartUpdateError(chart.getId(), "系统异常导致任务失败");
                    return null;
                }
            });
        } catch (Exception e) {
            handleChartUpdateError(chart.getId(), "队列已满导致任务失败");
        }

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }

    /**
     * 智能分析（异步消息队列）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String goal = genChartByAiRequest.getGoal();
        String name = genChartByAiRequest.getName();
        String chartType = genChartByAiRequest.getChartType();
        //对参数进行校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isBlank(name), ErrorCode.PARAMS_ERROR, "名称为空");
        ThrowUtils.throwIf(name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        ThrowUtils.throwIf(StringUtils.isBlank(chartType), ErrorCode.PARAMS_ERROR, "图表类型为空");

        //对文件进行校验
        long size = multipartFile.getSize();
        final long ONE_MB = 1024 * 1024;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过1M");
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        //使用限流器对每个用户进行限流
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        String limiterKey = String.format("ibi:chart:genchartbyai:%s", userId);
        redisLimiterManager.doRateLimit(limiterKey);

        //使用easyExcel将原始数据压缩为csv
        String csvData = ExcelUtils.excelToCsv(multipartFile);

        long biModelId = CommonConstant.BI_MODEL_ID;

        //构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求:").append("\n");

        //拼接分析目标
        String userGoal = goal += "，请使用" + chartType;
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        userInput.append(csvData).append("\n");

        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus(ChartStatusEnum.WAIT.getValue());
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        Long chartId = chart.getId();
        try {
            biMessageProducer.sendMessage(String.valueOf(chartId));
        } catch (Exception e) {
            log.info("向mq中添加消息失败");
            handleChartUpdateError(chartId,"向mq中添加消息失败");
        }
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chartId);
        return ResultUtils.success(biResponse);
    }


    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus(ChartStatusEnum.FAILED.getValue());
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }


    /**
     * 获取图表查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }
}
