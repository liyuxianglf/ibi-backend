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
    UserService userService;

    @Resource
    ChartService chartService;

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
        ThrowUtils.throwIf(multipartFile==null,ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(genChartByAiRequest==null,ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request==null,ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        BiResponse biResponse = chartService.genChartByAi(multipartFile, genChartByAiRequest, loginUser);
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
        ThrowUtils.throwIf(multipartFile==null,ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(genChartByAiRequest==null,ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request==null,ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        BiResponse biResponse = chartService.genChartByAiAsync(multipartFile, genChartByAiRequest, loginUser);
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
        ThrowUtils.throwIf(multipartFile==null,ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(genChartByAiRequest==null,ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request==null,ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        BiResponse biResponse = chartService.genChartByAiAsyncMq(multipartFile, genChartByAiRequest, loginUser);
        return ResultUtils.success(biResponse);
    }


    /**
     * 分页获取当前用户创建的包装类
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
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
                chartService.getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }
}
