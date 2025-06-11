package com.cube.wechat.selfapp.app.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2025年03月26日 16:12
 */
@Data
public class JsonRpcRequest {

    private String jsonrpc;

    private String id;

    private String method;

    private JsonNode params;

}
