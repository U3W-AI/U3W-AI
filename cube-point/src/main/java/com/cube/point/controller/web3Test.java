package com.cube.point.controller;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年10月25日 15:00
 */

import com.alibaba.fastjson2.JSONObject;
import com.cube.point.domain.EthConstant;
import io.github.novacrypto.bip39.MnemonicGenerator;
import io.github.novacrypto.bip39.SeedCalculator;
import io.github.novacrypto.bip39.Words;
import io.github.novacrypto.bip39.wordlists.English;
import io.github.novacrypto.hashing.Sha256;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

public class web3Test {
    //主账号
    private static String ownerAddress ="0x8376637313b37b0abc97a401aa214d022ef3ab94";
    //发送者私钥（加0x前缀）
    private static String privateKey ="a61fb81ada2c4710e52de726a513ec35bfa6b6e2d45f65b49b83983cea102612";
    //杨航行模拟接收者地址密码：123456
    private static String toAddress ="0x2edc4228a84d672affe8a594033cb84a029bcafc";
    //交易的附加信息
    private static String data = "交易的附加信息";
    //这笔交易的gas价格
    private static BigInteger price = BigInteger.valueOf(5);
    //这笔交易的gas上限
    private static BigInteger limit = BigInteger.valueOf(100000);



    public static void getAccount(String from) {
        //连接到私链
        Web3j web3 = Web3j.build(new HttpService("http://101.34.87.103:8545"));
        //获取一些区块链信息
        try {
            //余额
            EthGetBalance ethbalanceT = web3.ethGetBalance(from, DefaultBlockParameterName.LATEST).send();
            BigDecimal balanceInEtherT = Convert.fromWei(ethbalanceT.getBalance().toString(), Convert.Unit.ETHER);

            //打印信息
            System.out.println("账户的余额："+balanceInEtherT);

        } catch (IOException ex) {
            throw new RuntimeException("Error whilst sending json-rpc requests", ex);
        }
    }

    public static String ethTran(String from,String to, BigInteger num) throws Exception {

        BigDecimal etherAmount = new BigDecimal(num); // 使用 BigDecimal 表示 2000 Ether
        BigInteger weiAmount = Convert.toWei(etherAmount, Convert.Unit.ETHER).toBigInteger();

        Web3j web3 = Web3j.build(new HttpService("http://101.34.87.103:8545"));

        Credentials credentials = Credentials.create("f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");

        EthGetTransactionCount ethGetTransactionCount = web3.ethGetTransactionCount(
                from, DefaultBlockParameterName.LATEST).send();

        BigInteger nonce = ethGetTransactionCount.getTransactionCount();

        RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
                nonce, BigInteger.ZERO,
                Convert.toWei(EthConstant.GAS_LIMIT, Convert.Unit.WEI).toBigInteger(), to, weiAmount);
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);

        EthSendTransaction ethSendTransaction = web3.ethSendRawTransaction(hexValue).send();

        if (ethSendTransaction.hasError()) {
            System.out.println("transfer error:"+ethSendTransaction.getError().getMessage());
            throw new Exception(ethSendTransaction.getError().getMessage());
        } else {
            String transactionHash = ethSendTransaction.getTransactionHash();
            System.out.println("Transfer transactionHash:" + transactionHash);
            return transactionHash;
        }
    }

    public static void sendtrans(){
        try{
            Web3j web3 = Web3j.build(new HttpService("http://101.34.87.103:8545"));
            //获取nonce

            BigInteger nonce = web3.ethGetTransactionCount(ownerAddress,DefaultBlockParameterName.PENDING).send().getTransactionCount();
            //设置你的转账金额
            BigInteger value = Convert.toWei("0.000000000000001", Convert.Unit.ETHER).toBigInteger();
            //签名交易
            RawTransaction rawTransaction =RawTransaction.createTransaction(nonce, price, limit, toAddress, value, data);
            Credentials credentials = Credentials.create(privateKey);
            byte[] signedMessage =TransactionEncoder.signMessage(rawTransaction, credentials);
            //广播交易
            String hash =  web3.ethSendRawTransaction(Numeric.toHexString(signedMessage)).sendAsync().get().getTransactionHash();
            //获取交易的哈希值，之后可以根据交易的哈希值查询交易细节
            System.out.println("hash:"+hash);
            // 在确认交易成功后，停止挖矿
        }catch (Exception e){
            e.printStackTrace();
        }
    }



    public static void main(String[] args) throws Exception {
//        sendtrans();
//        getAccount("0x2edc4228a84d672affe8a594033cb84a029bcafc");
        getAccount("0x24833ca3d1dd6f679ff836403a3abe5d3eebe582");

    }



    public static void main2(String[] args) {

        String filePath = "/Users/yanghangxing/Downloads/logs/";
        StringBuilder sb = new StringBuilder();
        byte[] entropy = new byte[Words.TWELVE.byteLength()];
        new SecureRandom().nextBytes(entropy);
        new MnemonicGenerator(English.INSTANCE).createMnemonic(entropy, sb::append);
        String mnemonic = sb.toString();
        List mnemonicList = Arrays.asList(mnemonic.split(" "));
        byte[] seed = new SeedCalculator().withWordsFromWordList(English.INSTANCE).calculateSeed(mnemonicList, "123456");
        ECKeyPair ecKeyPair = ECKeyPair.create(Sha256.sha256(seed));
        String privateKey = ecKeyPair.getPrivateKey().toString(16);
        String publicKey = ecKeyPair.getPublicKey().toString(16);
        String address = "0x" + Keys.getAddress(publicKey);
        //创建钱包地址与密钥
        String fileName = null;
        try { fileName = WalletUtils.generateWalletFile("123456", ecKeyPair, new File(filePath), true);
        } catch (Exception e) {  e.printStackTrace(); }
        if (fileName == null) {
            System.out.println("名称为空");
        }
        String accountFilePath = filePath + File.separator + fileName;
        JSONObject json = new JSONObject();
        json.put("privateKey", privateKey);
        json.put("publicKey", publicKey);
        json.put("address", address);
        System.out.println("privateKey:"+privateKey);
        System.out.println("publicKey:"+publicKey);
        System.out.println("address:"+address);
    }

}
