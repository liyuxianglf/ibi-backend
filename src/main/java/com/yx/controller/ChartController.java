package com.yx.controller;


import cn.hutool.core.io.FileUtil;
import cn.hutool.poi.excel.ExcelUtil;
import com.yx.common.BaseResponse;
import com.yx.common.ErrorCode;
import com.yx.constant.CommonConstant;
import com.yx.exception.ThrowUtils;
import com.yx.manager.RedisLimiterManager;
import com.yx.model.dto.chart.GenChartByAiRequest;
import com.yx.model.entity.User;
import com.yx.model.vo.BiResponse;
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
    UserService userService;
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String goal = genChartByAiRequest.getGoal();
        String name = genChartByAiRequest.getName();
        String chartType = genChartByAiRequest.getChartType();
        //对参数进行校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR,"目标为空");
        ThrowUtils.throwIf(StringUtils.isBlank(name), ErrorCode.PARAMS_ERROR,"名称为空");
        ThrowUtils.throwIf(name.length()>100, ErrorCode.PARAMS_ERROR,"名称过长");
        ThrowUtils.throwIf(StringUtils.isBlank(chartType), ErrorCode.PARAMS_ERROR,"图表类型为空");

        //对文件进行校验
        long size = multipartFile.getSize();
        final long ONE_MB = 1024*1024;
        ThrowUtils.throwIf(size>ONE_MB,ErrorCode.PARAMS_ERROR,"文件超过1M");
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
final List<String> validFileSuffixList = Arrays.asList("xlsx");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix),ErrorCode.PARAMS_ERROR,"文件后缀非法");

        //使用限流器对每个用户进行限流
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        String limiterKey = String.format("ibi:chart:genchartbyai:%s",userId);
        redisLimiterManager.doRateLimit(limiterKey);

        //使用easyExcel将原始数据压缩为csv
        String csvData = ExcelUtils.excelToCsv(multipartFile);

        long biModelId = CommonConstant.BI_MODEL_ID;

        //构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求:").append("\n");

        //拼接分析目标
        String userGoal =goal+= "，请使用" + chartType;
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        userInput.append(csvData).append("\n");

        //

    }


}
