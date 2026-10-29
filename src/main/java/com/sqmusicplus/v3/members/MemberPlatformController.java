package com.sqmusicplus.v3.members;

import com.alibaba.fastjson2.*;
import com.sqmusicplus.v3.config.*;
import com.sqmusicplus.v3.base.enums.SetConfigEnum;
import com.sqmusicplus.v3.plug.qq.hander.*;
import com.sqmusicplus.v3.plug.qq.entity.*;
import com.sqmusicplus.v3.plug.qq.enums.*;
import com.sqmusicplus.v3.plug.netease.hander.*;
import okhttp3.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** QR sessions and credentials are owned by the authenticated member, never a body-supplied ID. */
@RestController @RequireLogin @RequestMapping("/api/members/me/platforms")
public class MemberPlatformController {
    private final MemberService members;private final QQHander qq;private final NeteaseHander ne;
    private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).callTimeout(18,TimeUnit.SECONDS).build();
    private static final class Session{String id=UUID.randomUUID().toString(),provider,key,status="等待扫码";QQMusicQr qr;long expires=System.currentTimeMillis()+300000,last;}
    private final Map<Integer,Session> sessions=new ConcurrentHashMap<>();
    public MemberPlatformController(MemberService members,QQHander qq,NeteaseHander ne){this.members=members;this.qq=qq;this.ne=ne;}
    private JSONObject request(String path,JSONObject body)throws Exception{
        SQNeteaseCloudMusicInfo info=ne.neteaseCloudMusicInfo;if(info.getBaseUrl()==null||info.getBaseUrl().isBlank()){info=new SQNeteaseCloudMusicInfo();info.init(SqConfigCache.getSqConfigValue(SetConfigEnum.PLUG_NETEASE_BASEURL).split(";",2)[0]);}
        String base=info.getBaseUrl();if(base==null||base.isBlank())throw new IllegalStateException("请先由管理员配置网易云服务");
        body.put("timestamp",System.currentTimeMillis());Request.Builder req=new Request.Builder().url(base.replaceAll("/+$","")+path).post(okhttp3.RequestBody.create(body.toJSONString(),MediaType.get("application/json")));info.getHeader().forEach(req::header);
        try(Response response=http.newCall(req.build()).execute()){if(!response.isSuccessful()||response.body()==null)throw new IllegalStateException("音乐平台暂时不可用");return JSONObject.parseObject(response.body().string());}
    }
    @GetMapping public AjaxResult bindings(){JSONArray out=new JSONArray();for(String p:List.of("qqvip","netease"))out.add(JSONObject.of("provider",p,"saved",!members.credential(MemberScope.id(),p).isBlank()));return AjaxResult.success(out);}
    @DeleteMapping("/{provider}") public AjaxResult remove(@PathVariable("provider") String p){MemberService.provider(p);members.credential(MemberScope.id(),p,"");sessions.remove(MemberScope.id());return AjaxResult.success();}
    @PutMapping("/{provider}/credential") public AjaxResult credential(@PathVariable("provider") String p,@org.springframework.web.bind.annotation.RequestBody JSONObject body){MemberService.provider(p);String value=body.getString("credential");if(value==null||value.isBlank())throw new IllegalArgumentException("请填写自己的平台登录凭据");if(p.equals("qqvip")){JSONObject c=JSONObject.parseObject(value);if(c.getString("musicid")==null||c.getString("musickey")==null)throw new IllegalArgumentException("QQ 凭据格式不正确");}members.credential(MemberScope.id(),p,value);return AjaxResult.success();}
    @PostMapping("/{provider}/qr") public AjaxResult qr(@PathVariable("provider") String p,@org.springframework.web.bind.annotation.RequestBody(required=false) JSONObject body)throws Exception{
        MemberService.provider(p);sessions.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis());Session s=new Session();s.provider=p;String image;
        if(p.equals("qqvip")){s.qr=body!=null&&"wechat".equals(body.getString("type"))?QQLoginHelp.getWechatLoginQr():QQLoginHelp.getQQLoginQr();if(s.qr==null)throw new IllegalStateException("二维码暂未生成，请稍后重试");image="data:"+s.qr.getMimeType()+";base64,"+Base64.getEncoder().encodeToString(s.qr.getData());}
        else{JSONObject result=request("/login/qr/key",JSONObject.of("cookie",""));if(result.getIntValue("code")!=200||result.getJSONObject("data")==null)throw new IllegalStateException("二维码暂未生成");s.key=result.getJSONObject("data").getString("unikey");JSONObject created=request("/login/qr/create",JSONObject.of("key",s.key,"qrimg",true,"cookie",""));image=created.getJSONObject("data").getString("qrimg");if(image==null||!image.startsWith("data:image/"))throw new IllegalStateException("二维码暂未生成");}
        sessions.put(MemberScope.id(),s);return AjaxResult.success(JSONObject.of("sessionId",s.id,"image",image,"expiresAt",s.expires));
    }
    @GetMapping("/{provider}/qr/{sessionId}") public AjaxResult poll(@PathVariable("provider") String p,@PathVariable("sessionId") String id)throws Exception{
        MemberService.provider(p);Session s=sessions.get(MemberScope.id());if(s==null||!s.provider.equals(p)||!s.id.equals(id)||s.expires<System.currentTimeMillis())return AjaxResult.success(JSONObject.of("status","二维码已过期","done",false,"expired",true));
        synchronized(s){if(System.currentTimeMillis()-s.last<2000)return AjaxResult.success(JSONObject.of("status",s.status,"done",false));s.last=System.currentTimeMillis();boolean done=false,expired=false;
            if(p.equals("qqvip")){
                QQMusicQrEventResult result=s.qr.getQrType()==LoginType.WECHAT?QQLoginHelp.checkWechatQR(s.qr):QQLoginHelp.checkQQQr(s.qr);
                QRCodeLoginEvents event=result==null?QRCodeLoginEvents.OTHER:result.getQrCodeLoginEvents();
                if(event==QRCodeLoginEvents.DONE){QQMusicCookieInfo cookie;if(s.qr.getQrType()==LoginType.WECHAT)cookie=qq.getWechatCookieByCode(result.getCode());else{QQMusicQrEventResult auth=QQLoginHelp.getAuthorizeByQQMusicQrEventResult(result);cookie=auth!=null&&auth.getQrCodeLoginEvents()==QRCodeLoginEvents.CODE_SUCCESS?qq.getCookieByCode(auth.getCode()):null;}if(cookie==null)throw new IllegalStateException("平台登录暂未完成，请重新扫码");done=true;s.status="已登录";}
                else{s.status=event==QRCodeLoginEvents.CONF?"已扫码，请在手机确认":"等待扫码";expired=event==QRCodeLoginEvents.TIMEOUT||event==QRCodeLoginEvents.REFUSE;}
            }else{JSONObject result=request("/login/qr/check",JSONObject.of("key",s.key,"cookie",""));int code=result.getIntValue("code");if(code==803){String cookie=result.getString("cookie");if(cookie==null||cookie.isBlank())throw new IllegalStateException("平台未返回有效登录信息");members.credential(MemberScope.id(),p,cookie);done=true;s.status="已登录";}else{s.status=code==802?"已扫码，请在手机确认":"等待扫码";expired=code==800;}}
            if(done||expired)sessions.remove(MemberScope.id());return AjaxResult.success(JSONObject.of("status",s.status,"done",done,"expired",expired));
        }
    }
}
