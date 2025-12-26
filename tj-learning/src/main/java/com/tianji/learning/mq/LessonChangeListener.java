package com.tianji.learning.mq;


import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * @description: 课程变化监听类
 * @author: TanH
 * @date: 2025-12-18
 */
@Slf4j
@Component
public class LessonChangeListener {

    private final ILearningLessonService lessonService;

    public LessonChangeListener(ILearningLessonService lessonService) {
        this.lessonService = lessonService;
    }

    /**
     * @description: 监听课程支付消息，该方法监听来自订单服务的支付完成消息，处理与学习课程相关业务逻辑
     * @param order 订单基础信息，包含订单ID、用户ID、课程ID列表和订单完成时间等信息
     * @return void
     * @author: TanH
     * @date: 2025/12/21
     **/
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonPay(OrderBasicDTO order){
        // 健壮性保证
        if (order == null || order.getUserId() == null || CollUtils.isEmpty(order.getCourseIds())) {
            log.error("接收到MQ消息异常，订单数据为空");
            return;
        }

        lessonService.addUserLessons(order.getUserId(), order.getCourseIds());
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.refund.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void listenLessonRefund(OrderBasicDTO order) {
        if (order == null || order.getUserId() == null || CollUtils.isEmpty(order.getCourseIds())) {
            log.error("接收到MQ消息异常，订单数据为空");
            return;
        }
        lessonService.removeInvalidCoursesFromMQ(order.getUserId(), order.getCourseIds());

    }

}