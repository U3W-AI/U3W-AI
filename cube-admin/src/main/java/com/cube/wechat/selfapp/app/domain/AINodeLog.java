package com.cube.wechat.selfapp.app.domain;

import lombok.Data;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年10月31日 11:07
 */
@Data
public class AINodeLog {

   private String userId;

   private String nodeName;

   private String userPrompt;

   private Object res;


}
