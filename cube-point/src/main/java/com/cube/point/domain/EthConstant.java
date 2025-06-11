package com.cube.point.domain;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年11月01日 18:11
 */
public class EthConstant {

    //redis key 存储用户的账户地址集
    public static final String USER_ETH_ADDRESS_JSON_KEY = "user.eth.address.json.key";

    //redis key 块的高度
    public static final String ETH_BLOCK_NUMBER_KEY = "eth.block.number.key";

    //ERC20_USDT合约地址
    public static final String CONTRACTA_DDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7";

    //geth客户端
    public static final String SERVER = "http://xx.xx.xx.xx:8545";

    //gas price
    public static final String GAS_PRICE = "0";

    //gas limit
    public static final String GAS_LIMIT = "100000";

    //入账账户
    public static final String IN_ADDRESS = "0x000";

    //出账账户
    public static final String OUT_ADDRESS = "0x000";

    //出账账户私钥
    public static final String OUT_KEY = "your private key";
}
