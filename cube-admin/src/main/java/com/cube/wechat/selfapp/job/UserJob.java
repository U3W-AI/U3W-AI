package com.cube.wechat.selfapp.job;

import com.cube.wechat.selfapp.app.mapper.UserInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2025年06月11日 15:50
 */
@Component("userJob")
public class UserJob {

    @Autowired
    private UserInfoMapper userInfoMapper;

    public void updateUserInfo(){

        List<Map> users = userInfoMapper.getAllUserInfo();
        for (Map user : users) {
            //连接到私链
            Web3j web3 = Web3j.build(new HttpService("http://101.34.87.103:8545"));
            //获取一些区块链信息
            try {
                //余额
                EthGetBalance ethbalance = web3.ethGetBalance(user.get("address")+"", DefaultBlockParameterName.LATEST).send();
                BigDecimal balanceInEther = Convert.fromWei(ethbalance.getBalance().toString(), Convert.Unit.ETHER);

                //打印信息
                System.out.println(user.get("user_id")+"账户的余额："+balanceInEther);
                userInfoMapper.updateUserInfo(user.get("user_id")+"",balanceInEther);

            } catch (IOException ex) {
                throw new RuntimeException("Error whilst sending json-rpc requests", ex);
            }
        }


    }

}
