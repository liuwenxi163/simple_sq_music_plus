package com.sqmusicplus.v3.library;

import com.alibaba.fastjson2.*;
import okhttp3.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** ag-1 / zzc protocol adapted from Jixun Wu's MIT-licensed qmweb-sign.
 * Full attribution and permission: licenses/qmweb-sign-MIT.txt. No write retries. */
final class QqAccountTransport {
    static final OkHttpClient CLIENT=new OkHttpClient.Builder().connectTimeout(6,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS)
        .callTimeout(18,TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).build();
    private static final int[] SCRAMBLE={89,39,179,150,218,82,58,252,177,52,186,123,120,64,242,133,143,161,121,179};
    private static final int[] RESPONSE={122,63,140,29,94,155,47,10,108,77,126,139,31,58,92,157,14,43,111,74,129};
    private static final byte[] KEY={(byte)189,48,95,16,(byte)208,(byte)255,116,(byte)182,(byte)239,84,(byte)218,(byte)184,53,(byte)181,(byte)225,(byte)207};
    static String sign(String body)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-1").digest(body.getBytes(StandardCharsets.UTF_8));String hash=HexFormat.of().withUpperCase().formatHex(digest);byte[] mid=new byte[20];for(int i=0;i<20;i++)mid[i]=(byte)(SCRAMBLE[i]^digest[i]);
        StringBuilder a=new StringBuilder(),b=new StringBuilder();for(int i:new int[]{23,14,6,36,16,7,19})a.append(hash.charAt(i));for(int i:new int[]{16,1,32,12,19,27,8,5})b.append(hash.charAt(i));
        return ("zzc"+a+Base64.getEncoder().encodeToString(mid).replaceAll("[\\\\/+=]","")+b).toLowerCase(Locale.ROOT);
    }
    static JSONObject comm(JSONObject cookie){return JSONObject.of("ct",24,"cv",4747474,"uin",cookie.getString("musicid"),"qq",cookie.getString("musicid"),"authst",cookie.getString("musickey"),"tmeLoginType",cookie.getString("loginType"),"format","json");}
    static String cookie(JSONObject c){String id=c.getString("musicid"),key=c.getString("musickey");if(id==null||!id.matches("[0-9]+")||key==null||key.matches(".*[\\r\\n;].*"))throw new IllegalStateException("PLATFORM_LOGIN_REQUIRED");return "uin="+id+"; qqmusic_uin="+id+"; qqmusic_key="+key+"; qm_keyst="+key+"; wxuin="+id+"; wxmusickey="+key+"; login_type="+c.getString("loginType");}
    static JSONObject call(JSONObject c,String module,String method,JSONObject param,boolean write)throws Exception{
        JSONObject common=comm(c);if(write){int gtk=5381;for(char ch:c.getString("musickey").toCharArray())gtk=gtk*33+ch;gtk&=0x7fffffff;common.put("g_tk",gtk);common.put("g_tk_new_20200303",gtk);common.put("platform","yqq.json");common.put("needNewCode",1);}
        String body=JSONObject.of("comm",common,"req",JSONObject.of("module",module,"method",method,"param",param)).toJSONString();String url="https://u6.y.qq.com/cgi-bin/musicu.fcg";String payload=body;
        if(write){byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(KEY,"AES"),new GCMParameterSpec(128,iv));byte[] enc=cipher.doFinal(body.getBytes(StandardCharsets.UTF_8)),joined=Arrays.copyOf(iv,iv.length+enc.length);System.arraycopy(enc,0,joined,iv.length,enc.length);payload=Base64.getEncoder().encodeToString(joined);url="https://u6.y.qq.com/cgi-bin/musics.fcg?encoding=ag-1&sign="+sign(body);}
        Request request=new Request.Builder().url(url).header("Referer","https://y.qq.com/").header("User-Agent","Mozilla/5.0 QQMusic/73222").header("Cookie",cookie(c)).post(RequestBody.create(payload,MediaType.get(write?"text/plain":"application/json"))).build();
        try(Response response=CLIENT.newCall(request).execute()){
            if(!response.isSuccessful()||response.body()==null)throw new IllegalStateException("PLATFORM_UNAVAILABLE");byte[] raw=response.body().bytes();if(raw.length>8*1024*1024)throw new IllegalStateException("PLATFORM_RESPONSE_TOO_LARGE");
            if(write)for(int i=0;i<raw.length;i++)raw[i]^=(byte)RESPONSE[i%RESPONSE.length];JSONObject result=JSONObject.parseObject(new String(raw,StandardCharsets.UTF_8)),block=result.getJSONObject("req");
            if(result.getIntValue("code")!=0||block==null||!block.containsKey("code")||block.getIntValue("code")!=0||block.getJSONObject("data")==null)throw new IllegalStateException(write?"PLATFORM_WRITE_REJECTED":"PLATFORM_UNAVAILABLE");
            JSONObject data=block.getJSONObject("data");for(String name:List.of("code","subcode","retcode","retCode"))if(data.containsKey(name)&&data.getIntValue(name)!=0)throw new IllegalStateException(write?"PLATFORM_WRITE_REJECTED":"PLATFORM_UNAVAILABLE");
            if(write&&!data.containsKey("retCode"))throw new IllegalStateException("PLATFORM_WRITE_UNCONFIRMED");return data;
        }
    }
}
