package com.tencent.wework;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

/**
 * @usage 写文件存取数据
 *        每个记录使用一个文件，文件名为notifyId
 *        文件内容为响应数据，单行
 *        过期文件(10min)的定期(20min)清理使用独立线程完成，仅供参考
 */
public class DataBaseUtils {
    private static final String DATA_DIR = "/mnt/data/callback_notify/";

    private static final int SCAN_INTERVAL = 10;  // 扫描间隔，单位为分钟

    private static final int EXPIRE_TIME = 20;  // 过期时间，单位为分钟

    /**
     * 初始化代码块，作用如下：
     * 1. 创建数据目录
     * 2. 启动清理过期文件的线程
     */
    static {
        Path dirPath = Paths.get(DATA_DIR);
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
            } catch (IOException e) {
                LogUtil.LogError(null, "create directory " + DATA_DIR + " failed");
                e.printStackTrace();
            }
            LogUtil.LogInfo(null, "create directory " + DATA_DIR + " success");
        }

        Thread cleanerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        File folder = new File(DATA_DIR);
                        File[] files = folder.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                if (file.isFile() && isFileOld(file)) {
                                    LogUtil.LogInfo(null, "Deleted expired file: " + file.getName());
                                    file.delete();
                                }
                            }
                        }
                        TimeUnit.MINUTES.sleep(SCAN_INTERVAL); // 每10分钟执行一次
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LogUtil.LogDebug(null, "File cleaning thread was interrupted.");
                }
            }

            private boolean isFileOld(File file) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                    long fileCreationTime = attrs.creationTime().toMillis();
                    return (System.currentTimeMillis() - fileCreationTime) > EXPIRE_TIME * 60 * 1000;
                } catch (Exception e) {
                    LogUtil.LogError(null, "Error reading file attributes: " + e.getMessage());
                    return false;
                }
            }
        });

        cleanerThread.setDaemon(true); // 设置为守护线程，不会阻止JVM的关闭
        cleanerThread.start();
    }

     // 私有构造函数，防止外部实例化
    private DataBaseUtils() {}

    public static enum ErrorCode {
        SUCCESS,
        IO_FAIL,
        DIRECTORY_NOT_EXIST,
        NOTIFY_ID_NOT_EXIST,
        EMPTY_FILE
    }

    /**
     * @usage 存储响应数据
     * @param notifyId 通知ID
     * @param data 响应数据
     * @return ErrorCode 错误码
     */
    public static ErrorCode AddNotifyData(String notifyId, String data) {
        try {
            String fileName = DATA_DIR + notifyId;
            File file = new File(fileName);
            if (!file.exists()) {
                file.createNewFile();
                LogUtil.LogInfo(null, "create file '" + fileName + "' success");
            }

            FileWriter fw = new FileWriter(file);
            fw.write(data);
            fw.close();

        } catch (IOException e) {
            e.printStackTrace();
            return ErrorCode.IO_FAIL;
        }

        return ErrorCode.SUCCESS;
    }

    /**
     * @usage 读取指定notify_id的数据
     * @param notifyId 通知ID
     * @param dataBuffer 响应数据缓冲区，用于接收数据
     * @return ErrorCode 错误码
     */
    public static ErrorCode GetByNotifyId(String notifyId, StringBuffer dataBuffer) {
        String fileName = DATA_DIR + notifyId;
        File file = new File(fileName);
        if (!file.exists()) {
            LogUtil.LogError(null, "notify_id not exist");
            return ErrorCode.NOTIFY_ID_NOT_EXIST;
        }

        try {
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            String line = br.readLine();
            if (line == null || line.isEmpty()) {
                LogUtil.LogDebug(null, "file: '" + fileName + "' is empty");
                br.close();
                fr.close();
                return ErrorCode.EMPTY_FILE;
            }

            dataBuffer.append(line);

            br.close();
            fr.close();

        } catch (IOException e) {
            e.printStackTrace();
            return ErrorCode.IO_FAIL;
        }

        return ErrorCode.SUCCESS;
    }
}
