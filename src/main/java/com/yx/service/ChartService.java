package com.yx.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yx.model.dto.chart.ChartQueryRequest;
import com.yx.model.dto.chart.GenChartByAiRequest;
import com.yx.model.entity.Chart;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yx.model.entity.User;
import com.yx.model.vo.BiResponse;
import org.springframework.web.multipart.MultipartFile;

/**
* 图表服务
*/
public interface ChartService extends IService<Chart> {
     /**
      * 智能分析
      * @param multipartFile
      * @param genChartByAiRequest
      * @param loginUser
      * @return
      */
     public BiResponse genChartByAi(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, User loginUser);

     /**
      * 智能分析（异步）
      *
      * @param multipartFile
      * @param genChartByAiRequest
      * @param loginUser
      * @return
      */
     public BiResponse genChartByAiAsync(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, User loginUser);

     /**
      * 智能分析（异步消息队列）
      *
      * @param multipartFile
      * @param genChartByAiRequest
      * @param loginUser
      * @return
      */
     public BiResponse genChartByAiAsyncMq(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, User loginUser);

     /**
      * 处理图表失败状态
      * @param chartId
      * @param execMessage
      */
     void handleChartUpdateError(long chartId, String execMessage);

     /**
      * 构造用户输入
      * @param chart
      * @return
      */
     String buildUserInput(Chart chart);

     /**
      * 获取图表查询包装类
      *
      * @param chartQueryRequest
      * @return
      */
     QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest);
}
