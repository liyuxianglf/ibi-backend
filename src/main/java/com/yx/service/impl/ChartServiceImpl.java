package com.yx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yx.model.entity.Chart;
import com.yx.model.enums.ChartStatusEnum;
import com.yx.service.ChartService;
import com.yx.mapper.ChartMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
* @author lige
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
* @createDate 2023-10-25 20:06:48
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService{


    public void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus(ChartStatusEnum.FAILED.getValue());
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = this.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }

    public String buildUserInput(Chart chart) {
        String goal = chart.getGoal();
        String chartType = chart.getChartType();
        String csvData = chart.getChartData();

        // 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        userInput.append(csvData).append("\n");
        return userInput.toString();
    }

}




