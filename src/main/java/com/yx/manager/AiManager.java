package com.yx.manager;

import com.yupi.yucongming.dev.client.YuCongMingClient;
import com.yupi.yucongming.dev.common.BaseResponse;
import com.yupi.yucongming.dev.model.DevChatRequest;
import com.yupi.yucongming.dev.model.DevChatResponse;
import com.yx.common.ErrorCode;
import com.yx.exception.BusinessException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 提供通用服务：用于对接AI平台
 */
@Service
public class AiManager {

    @Resource
    YuCongMingClient yuCongMingClient;

    /**
     * AI对话
     * @param modeId    AI模型ID
     * @param message   等同于在AI聊天窗口发送给AI的聊天消息
     * @return
     */
    public String doChat(long modeId,String message){
        DevChatRequest devChatRequest = new DevChatRequest();
        devChatRequest.setModelId(modeId);
        devChatRequest.setMessage(message);
        BaseResponse<DevChatResponse> response = yuCongMingClient.doChat(devChatRequest);
        if (response==null){
            throw  new BusinessException(ErrorCode.SYSTEM_ERROR,"AI响应错误");
        }
        return response.getData().getContent();
    }
}
