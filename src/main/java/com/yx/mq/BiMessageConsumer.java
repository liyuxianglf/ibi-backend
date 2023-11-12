package com.yx.mq;

import com.rabbitmq.client.Channel;
import com.yx.common.ErrorCode;
import com.yx.constant.CommonConstant;
import com.yx.exception.BusinessException;
import com.yx.manager.AiManager;
import com.yx.model.entity.Chart;
import com.yx.model.enums.ChartStatusEnum;
import com.yx.service.ChartService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

@Component
@Slf4j
public class BiMessageConsumer {
    @Resource
    ChartService chartService;

    @Resource
    AiManager aiManager;

    @RabbitListener(queues = {BiMqConstant.BI_QUEUE_NAME}, ackMode = "MANUAL", concurrency = "4")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("receiveMessage message = {}", message);
        if (StringUtils.isBlank(message)) {
            // 如果失败，消息拒绝
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException e) {
                log.error("RabbitMQ客户端channel出现io错误",e);
                return;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException e) {
                log.error("RabbitMQ客户端channel出现io错误",e);
                return;
            }
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图表为空");
        }
        // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。
        Chart updateChart = new Chart();
        updateChart.setId(chart.getId());
        updateChart.setStatus(ChartStatusEnum.RUNNING.getValue());
        boolean b = chartService.updateById(updateChart);
        if (!b) {
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException e) {
                log.error("RabbitMQ客户端channel出现io错误",e);
                return;
            }
            chartService.handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
            return;
        }

        //调用AI
        String result = aiManager.doChat(CommonConstant.BI_MODEL_ID, chartService.buildUserInput(chart));
        String[] splits = result.split("【【【【【");
        if (splits.length < 3) {
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException e) {
                log.error("RabbitMQ客户端channel出现io错误",e);
                return;
            }
            chartService.handleChartUpdateError(chart.getId(), "AI 生成错误");
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
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException e) {
                log.error("RabbitMQ客户端channel出现io错误",e);
                return;
            }
            chartService.handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
            return;
        }
        // 消息确认
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("RabbitMQ客户端channel出现io错误",e);
        }
    }
}
