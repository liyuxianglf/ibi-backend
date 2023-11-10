package com.yx.controller;

import java.util.Date;


import cn.hutool.core.io.FileUtil;
import cn.hutool.poi.excel.ExcelUtil;
import com.yx.common.BaseResponse;
import com.yx.common.ErrorCode;
import com.yx.common.ResultUtils;
import com.yx.constant.CommonConstant;
import com.yx.exception.BusinessException;
import com.yx.exception.ThrowUtils;
import com.yx.manager.AiManager;
import com.yx.manager.RedisLimiterManager;
import com.yx.model.dto.chart.GenChartByAiRequest;
import com.yx.model.entity.Chart;
import com.yx.model.entity.User;
import com.yx.model.enums.ChartStatusEnum;
import com.yx.model.vo.BiResponse;
import com.yx.service.ChartService;
import com.yx.service.UserService;
import com.yx.utils.ExcelUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
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

    /**
     * 智能分析
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
                handleChartUpdateError(chart.getId(), "系统异常");
                return null;
            }
        });

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }


    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus(ChartStatusEnum.WAIT.getValue());
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }
}
