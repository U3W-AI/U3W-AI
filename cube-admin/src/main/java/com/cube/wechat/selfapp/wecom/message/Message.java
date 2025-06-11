/*
 * This file is part of the zyan/wework-msgaudit.
 *
 * (c) 读心印 <aa24615@qq.com>
 *
 * This source file is subject to the MIT license that is bundled
 * with this source code in the file LICENSE.
 */

package com.cube.wechat.selfapp.wecom.message;

import com.cube.wechat.selfapp.wecom.DB;
import com.cube.wechat.selfapp.wecom.util.Audio;
import com.cube.wechat.selfapp.wecom.util.RedisUtil;
import com.cube.wechat.selfapp.wecom.util.WeChatApiUtils;
import com.cube.wechat.selfapp.wecom.util.WebHookTem;
import com.tencent.wework.Finance;
import com.tencent.wework.RSAEncrypt;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class Message {

    public String prikey = null;
    public String corpid = null;
    public long seqs = 0;
    public long sdk;

    public String tableName = null;


    @Autowired
    private WeChatApiUtils weChatApiUtils;


    @Autowired
    private RedisUtil redisUtil;


    public Message(String corpid, String secret, String prikey){

        this.sdk = Finance.NewSdk();

        this.corpid = corpid;
        this.tableName = "message_"+this.corpid;
        int state = Finance.Init(sdk,corpid,secret);
        this.prikey = prikey;
    }


    //解密
    public String decryptData(String encrypt_random_key, String encrypt_msg){

        try {

            String  encrypt_key = RSAEncrypt.decryptRSA(encrypt_random_key,this.prikey);
            long message = Finance.NewSlice();
            int ret = Finance.DecryptData(this.sdk,encrypt_key, encrypt_msg, message);
            if (ret != 0) {
                return "";
            }

            String text =  Finance.GetContentFromSlice(message);
            Finance.FreeSlice(message);

            return text;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public long getSeq(){

        if(this.seqs>0){
            return this.seqs;
        }

        String sql = String.format("SELECT count(*) FROM %s",this.tableName);

        int count = DB.getJdbcTemplate().queryForObject(sql,Integer.class);
        if(count>0){
            sql = String.format("SELECT seq FROM %s order by seq desc LIMIT 1",this.tableName);
            long seq = DB.getJdbcTemplate().queryForObject(sql,Integer.class);
            this.seqs = seq;
            return seq;
        }

        return  0;
    }
    //获取列表
    public void getList() throws Exception{

        long seqs = this.getSeq();
        int limit = 10;
        long slice = Finance.NewSlice();
        int ret = Finance.GetChatData(this.sdk, seqs, limit, "", "", 3, slice);
        if (ret != 0) {
            return;
        }
        String json = Finance.GetContentFromSlice(slice);
        JSONObject jo = new JSONObject(json);

        String errmsg = jo.getString("errmsg");
        int errcode = jo.getInt("errcode");

        if(errcode==0){
            JSONArray chatdata = jo.getJSONArray("chatdata");
            for (int i = 0; i < chatdata.length(); i++) {
                String item = chatdata.get(i).toString();
                JSONObject data = new JSONObject(item);
                String encrypt_random_key = data.getString("encrypt_random_key");
                String encrypt_chat_msg = data.getString("encrypt_chat_msg");
                long publickey_ver = data.getLong("publickey_ver");
                String msgid = data.getString("msgid");
                long seq = data.getLong("seq");
                String message = this.decryptData(encrypt_random_key,encrypt_chat_msg);

                if(this.saveMessage(msgid,seq,publickey_ver,message)){
                    if(this.seqs<seq){
                        this.seqs=seq;
                    }
                }
            }
        }else{
            throw new Exception("获取失败");
        }

        //关闭
        Finance.FreeSlice(slice);

    }


    //保存消息
    public boolean saveMessage(String msgid,long seq,long publickey_ver,String message){


        JSONObject json;

        try {
            json = new JSONObject(message);
        }catch (Exception e){

            String sql = String.format("INSERT INTO %s " +
                    "(msgid,seq,publickey_ver,text) " +
                    "VALUES " +
                    "(?,?,?,'解密失败')",this.tableName);

                int res = DB.getJdbcTemplate().update(sql, msgid, seq, publickey_ver);

            return true;
        }


        String msgfrom = "";
        String roomid = "";
        String msgtype = "";
        String msgdata = "";
        long msgtime = 0;
        String tolist = "";
        String sdkfield = "";
        String text = "";
        String ext = "";

        String action = json.getString("action");

        try {
            msgtime = json.getLong("msgtime");
        }catch (Exception e){
            msgtime = json.getLong("time");
        }
        if(msgtime<2000000000){
            msgtime = msgtime*1000;
        }

        try {
            msgfrom = json.getString("from");
        }catch (Exception e){

        }

        try {
            roomid = json.getString("roomid");
        }catch (Exception e){

        }

        try {
            msgtype = json.getString("msgtype");
        }catch (Exception e){

        }


        if(!msgtype.equals("")) {
            try {
                JSONObject content;
                if(msgtype.equals("docmsg")){
                    content = json.getJSONObject("doc");
                }else if(msgtype.equals("external_redpacket")){
                    content = json.getJSONObject("redpacket");
                }else{
                    content = json.getJSONObject(msgtype);
                }

                if(msgtype.equals("text")) {
                    text = content.getString("content");

                    String sqlTwo = "select bot_key from sys_roombot where room_id =" + "'" + roomid + "'";
                    Map botKey = DB.getJdbcTemplate().queryForMap(sqlTwo);


                    if (text != null && text.matches("\\d{4}")) {
                        String sql = "select id,title,browse_num from wc_research_report where keyword = " + text;
                        Map fileInfo = DB.getJdbcTemplate().queryForMap(sql);

                        if (botKey != null && fileInfo != null) {
                            //利用群机器人推送策元小程序
                             WebHookTem.pushCyPro(fileInfo.get("title")+"",fileInfo.get("browse_num")+"",fileInfo.get("id")+"",botKey.get("bot_key")+"");
                        }
                    } else if (text.contains("东来文库")) {

                        if (botKey != null) {
                            //利用群机器人推送许都之行小程序
                            WebHookTem.pushXdzxPro(botKey.get("bot_key") + "");
                        }
                    }else if (text.contains("胖东来") && roomid.equals("wrypNhRQAAgM_LxM1ObMMG9qSpEIKRkA")) {
                        //利用群机器人推送许都之行小程序
                        WebHookTem.pushAIAnal(text,botKey.get("bot_key") + "");
                    }
                }
                try {
                    sdkfield = content.getString("sdkfileid");
                }catch (Exception e){
                }

                try {
                    msgdata = content.toString();
                }catch (Exception e){
                }
                //图片
                if(msgtype.equals("image")){
                    ext = "png";
                }
                //视频
                if(msgtype.equals("video")){
                    ext = "mp4";
                }
                //语音
                if(msgtype.equals("voice")){
                    ext = "amr";
                }
                //语音通话
                if(msgtype.equals("meeting_voice_call")){
                    ext = "mp4";
                }
                //表情
                if(msgtype.equals("emotion")){
                    int type = content.getInt("type");
                    if(type==1){ //动态表情
                        ext = "gif";
                    }
                    if(type==2){ //静态表情
                        ext = "png";
                    }
                }
                //文件
                if(msgtype.equals("file")){
                    String fileext = content.getString("fileext");
                    ext = fileext;
                }
            }catch (Exception e){
            }

        }


        //接收人 [user1,user2...]
        try {
            JSONArray tolistList = json.getJSONArray("tolist");
            int len = tolistList.length();
            String[] tolistArray = new String[len];
            for (int i = 0; i < len; i++) {
                tolistArray[i] = tolistList.get(i).toString();
            }
            tolist = StringUtils.arrayToCommaDelimitedString(tolistArray);
        }catch (Exception e){
        }




        String media_path = "";

        if(!sdkfield.equals("")){
            media_path =  "/msgfile/"+this.corpid+"/"+seq+"."+ext;
        }

        Date Now = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String created = ft.format(Now);

        //入库啦
        String sql;
        sql = String.format("SELECT count(*) FROM %s WHERE msgid='%s'",this.tableName,msgid);

        if(DB.getJdbcTemplate().queryForObject(sql,Integer.class)==0){
            sql = String.format("INSERT INTO %s " +
                    "(msgid,seq,`action`,msgfrom,tolist,roomid,msgtime,msgtype,text,sdkfield,msgdata,created,media_path,publickey_ver) " +
                    "VALUES " +
                    "(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",this.tableName);
            try {
                int res = DB.getJdbcTemplate().update(sql,msgid,seq,action,msgfrom,tolist,roomid,msgtime,msgtype,text,sdkfield,msgdata,created,media_path,publickey_ver);
                if(res>=1){

                    if(!sdkfield.equals("")){
                        this.downMedia(sdkfield,media_path, ext);
                    }
                    return true;
                }
                return false;
            }catch (Exception e){
                return false;
            }

        }else{
            return true;
        }

    }


    public void downMedia(String sdkField,String media_path, String ext){
        String indexbuf = "";
        while(true) {
            long media_data = Finance.NewMediaData();
            int ret = Finance.GetMediaData(this.sdk, indexbuf, sdkField, "", "", 60, media_data);
            if (ret != 0) {
                return;
            }
            try {
                FileOutputStream outputStream = new FileOutputStream(new File("."+media_path), true);
                outputStream.write(Finance.GetData(media_data));
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (Finance.IsMediaDataFinish(media_data) == 1) {
                Finance.FreeMediaData(media_data);
                String sql = String.format("UPDATE %s SET media_code=1 WHERE sdkfield=?",this.tableName);
                DB.getJdbcTemplate().update(sql,sdkField);

                if(ext.equals("amr")){
                    try {
                        Audio.toMp3("."+media_path,"."+media_path+".mp3");
                    }catch (Exception e){

                    }
                }

                break;
            } else {
                indexbuf = Finance.GetOutIndexBuf(media_data);
                Finance.FreeMediaData(media_data);
            }
        }
    }

}
