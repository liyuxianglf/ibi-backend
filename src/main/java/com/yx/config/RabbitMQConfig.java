package com.yx.config;

import com.yx.mq.BiMqConstant;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置
 */
@Configuration
public class RabbitMQConfig {
    /**
     * 创建队列
     * @return
     */
    @Bean
    public Queue biQueue(){
        return new Queue(BiMqConstant.BI_QUEUE_NAME,true,false,false);
    }

    /**
     * 创建交换机
     * @return
     */
    @Bean
    public Exchange biExchange(){
        return new DirectExchange(BiMqConstant.BI_EXCHANGE_NAME,true,false);
    }

    /**
     * 将队列与交换机通过路由key进行绑定
     * @param exchange
     * @param queue
     * @return
     */
    @Bean
    public  Binding binding(@Qualifier("biExchange") Exchange exchange, @Qualifier("biQueue")Queue queue){
        return BindingBuilder.bind(queue).to(exchange).with(BiMqConstant.BI_ROUTING_KEY).noargs();
    }




}
