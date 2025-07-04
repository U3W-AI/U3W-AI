/*
 *  Copyright (c) 2023. felord.cn
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *       https://www.apache.org/licenses/LICENSE-2.0
 *  Website:
 *       https://felord.cn
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.cube.wechat.selfapp.corpchat.service.callback;

import cn.felord.callbacks.CallbackEventBody;
import cn.felord.enumeration.CallbackEvent;
import com.cube.wechat.selfapp.corpchat.util.AESUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;

/**
 * 兜底回调处理事件，不可注册为Spring Bean
 *
 * @author dax
 * @since 2023/6/10 10:49
 */
@Slf4j
public final class NotMatchedCallbackEventBodyConsumer implements CallbackEventBodyConsumer {


    public static final CallbackEventBodyConsumer INSTANCE = new NotMatchedCallbackEventBodyConsumer();

    private NotMatchedCallbackEventBodyConsumer() {
    }

    @Override
    public boolean matched(CallbackEvent event) {
        throw new IllegalArgumentException("NotMatchedCallbackEventBodyConsumer 不得自动加入消费链");
    }

    @Override
    public void consume(CallbackEventBody body) {
       byte[] AESKey = Base64.getDecoder().decode("0ectVftLe76LrtbPGbzJnwqNuXo8rLpMeFaBjPcZJ8J=");
       String signature = body.getMsgSignature();
       String timestamps = body.getTimeStamp();
       String nonce = body.getNonce();
       String msg_encrypt = body.getEncrypt();
       String msg = AESUtil.decrypt(msg_encrypt);
//       System.out.println("消息事件回调");




    }

}
