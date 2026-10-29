package com.sqmusicplus.v3.library;

import com.alibaba.fastjson2.*;
import com.sqmusicplus.v3.base.enums.SetConfigEnum;
import com.sqmusicplus.v3.config.SqConfigCache;
import com.sqmusicplus.v3.plug.qq.hander.QQHander;
import com.sqmusicplus.v3.plug.netease.hander.NeteaseHander;
import org.springframework.stereotype.Service;
import okhttp3.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Fixed platform adapters. Credentials and owner identifiers never leave this service. */
@Service
public class PlatformMusicService {
    private final QQHander qq;private final NeteaseHander netease;private final AccountCollectionService collections;
    private record Cached(String account,long expires,JSONObject value){}
    private final Map<String,Cached> cache=new ConcurrentHashMap<>();
    public PlatformMusicService(QQHander qq,NeteaseHander netease,AccountCollectionService collections){this.qq=qq;this.netease=netease;this.collections=collections;}
    static String digest(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
    String secret(String provider){String value=SqConfigCache.getSqConfigValue(provider.equals("qqvip")?SetConfigEnum.PLUG_QQVIP_COOKIE:SetConfigEnum.PLUG_NETEASE_COOKIE);return value==null?"":value;}
    JSONObject qqCookie(){String value=secret("qqvip");if(value.isBlank())throw new IllegalStateException("PLATFORM_LOGIN_REQUIRED");JSONObject c=JSONObject.parseObject(value);QqAccountTransport.cookie(c);return c;}
    static void provider(String value){if(!Set.of("qqvip","netease").contains(value))throw new IllegalArgumentException("PLATFORM_NOT_SUPPORTED");}
    JSONObject q(JSONObject c,String module,String method,JSONObject param)throws Exception{return QqAccountTransport.call(c,module,method,param,false);}
    JSONObject writeQq(JSONObject c,String module,String method,JSONObject param)throws Exception{return QqAccountTransport.call(c,module,method,param,true);}
    private JSONObject own(JSONObject c)throws Exception{JSONObject p=JSONObject.parseObject(qq.getqqSearchEntity().userSelfSongListParam(c.getString("musicid"),c.getString("musickey"),c.getString("loginType"))).getJSONObject("req");return q(c,p.getString("module"),p.getString("method"),p.getJSONObject("param"));}
    static JSONArray array(JSONObject o,String name){JSONArray a=o.getJSONArray(name);return a==null?new JSONArray():a;}
    private JSONObject ne(String path,JSONObject params)throws Exception{
        String cookie=secret("netease");if(cookie.isBlank())throw new IllegalStateException("PLATFORM_LOGIN_REQUIRED");
        String base=netease.neteaseCloudMusicInfo.getBaseUrl();if(base==null||base.isBlank())throw new IllegalStateException("PLATFORM_UNAVAILABLE");
        JSONObject body=JSONObject.parseObject(params.toJSONString());body.put("cookie",cookie);body.put("timestamp",System.currentTimeMillis());
        Request.Builder builder=new Request.Builder().url(base.replaceAll("/+$","")+path).post(RequestBody.create(body.toJSONString(),MediaType.get("application/json")));
        netease.neteaseCloudMusicInfo.getHeader().forEach(builder::header);
        try(Response r=QqAccountTransport.CLIENT.newCall(builder.build()).execute()){if(!r.isSuccessful()||r.body()==null)throw new IllegalStateException("PLATFORM_UNAVAILABLE");JSONObject result=JSONObject.parseObject(r.body().string());if(result.getIntValue("code")!=200)throw new IllegalStateException(result.getIntValue("code")==301?"PLATFORM_LOGIN_REQUIRED":"PLATFORM_REQUEST_REJECTED");return result;}
    }
    public JSONObject accounts(){JSONArray accounts=new JSONArray(),errors=new JSONArray();for(String p:List.of("qqvip","netease")){
        if(secret(p).isBlank())continue;
        try{context(p);accounts.add(JSONObject.of("id",p,"name",p.equals("qqvip")?"QQ 音乐":"网易云音乐","daily",true,"writePlaylists",true));}
        catch(Exception e){errors.add(JSONObject.of("id",p,"message",safeError(e)));}
    }return JSONObject.of("accounts",accounts,"errors",errors);}
    private JSONObject context(String p)throws Exception{
        provider(p);String identity=digest(secret(p));Cached saved=cache.get(p+":context");if(saved!=null&&saved.account.equals(identity)&&saved.expires>System.currentTimeMillis())return JSONObject.parseObject(saved.value.toJSONString());
        JSONObject out=new JSONObject();JSONArray lists=new JSONArray(),liked=new JSONArray();
        if(p.equals("qqvip")){
            if(!Boolean.TRUE.equals(qq.checkLoginStatus()))throw new IllegalStateException("PLATFORM_LOGIN_REQUIRED");JSONObject c=qqCookie(),catalog=own(c);if(!catalog.containsKey("v_playlist"))throw new IllegalStateException("PLATFORM_LOGIN_REQUIRED");
            if(catalog.getIntValue("total")>array(catalog,"v_playlist").size())throw new IllegalStateException("PLATFORM_LIBRARY_INCOMPLETE");
            for(Object item:array(catalog,"v_playlist")){JSONObject row=(JSONObject)item;String tid=row.getString("tid"),dir=row.getString("dirId");if(tid==null||!tid.matches("[0-9]+")||dir==null||!dir.matches("[0-9]+"))continue;
                if(!"201".equals(dir)&&Integer.parseInt(dir)>=201&&Integer.parseInt(dir)<=206)continue;
                JSONObject list=JSONObject.of("id",tid,"dirId",dir,"name","201".equals(dir)?"我喜欢":row.getString("dirName"),"kind","201".equals(dir)?"likes":"owned");lists.add(list);
                if("201".equals(dir)){out.put("likesId",tid);for(Object t:qqTracks(c,list))liked.add(((JSONObject)t).getString("id"));}
            }
        }else{
            JSONObject status=ne("/login/status",new JSONObject());JSONObject data=status.getJSONObject("data"),profile=data==null?null:data.getJSONObject("profile");if(profile==null||profile.getLongValue("userId")==0)throw new IllegalStateException("PLATFORM_LOGIN_REQUIRED");long uid=profile.getLongValue("userId");
            JSONArray all=array(ne("/user/playlist",JSONObject.of("uid",uid,"limit",1000)),"playlist");
            for(Object entry:all){JSONObject row=(JSONObject)entry,creator=row.getJSONObject("creator");if(creator!=null&&creator.getLongValue("userId")==uid&&!row.getBooleanValue("subscribed"))lists.add(JSONObject.of("id",row.getString("id"),"name",row.getString("name"),"kind",row.getIntValue("specialType")==5?"likes":"owned"));}
            liked=array(ne("/likelist",JSONObject.of("uid",uid)),"ids");out.put("uid",uid);
        }
        out.put("_account",identity);out.put("playlists",lists);out.put("likedIds",liked);cache.put(p+":context",new Cached(identity,System.currentTimeMillis()+60000,out));return JSONObject.parseObject(out.toJSONString());
    }
    public JSONObject library(String p)throws Exception{JSONObject value=context(p);value.remove("uid");value.remove("_account");return value;}
    private JSONArray qqTracks(JSONObject c,JSONObject list)throws Exception{
        JSONArray tracks=new JSONArray();long until=System.currentTimeMillis()+18000;long total=Long.MAX_VALUE;
        for(int page=0;tracks.size()<total;page++){
            if(page>=50||System.currentTimeMillis()>until)throw new IllegalStateException("PLATFORM_LIBRARY_INCOMPLETE");
            JSONObject data=q(c,"music.srfDissInfo.DissInfo","CgiGetDiss",JSONObject.of("disstid",Long.parseLong(list.getString("id")),"dirid",Integer.parseInt(list.getString("dirId")),"song_begin",page*100,"song_num",100,"onlysonglist",1,"tag",1,"userinfo",1,"orderlist",1));
            if(!data.containsKey("total_song_num"))throw new IllegalStateException("PLATFORM_LIBRARY_INCOMPLETE");total=data.getLongValue("total_song_num");JSONArray batch=array(data,"songlist");if(batch.isEmpty()&&tracks.size()<total)throw new IllegalStateException("PLATFORM_LIBRARY_INCOMPLETE");
            for(Object entry:batch){JSONObject song=AccountCollectionService.song((JSONObject)entry);if(song!=null)tracks.add(song);}
        }return tracks;
    }
    static String dailyId(Object object){
        if(object instanceof JSONObject o){if(o.getIntValue("subtype")==510){String id=o.getString("id");if(id!=null&&id.matches("[0-9]+"))return id;}for(Object value:o.values()){String id=dailyId(value);if(id!=null)return id;}}
        else if(object instanceof JSONArray a)for(Object value:a){String id=dailyId(value);if(id!=null)return id;}return null;
    }
    static JSONObject neSong(JSONObject raw){JSONObject album=raw.getJSONObject("al");if(album==null)album=raw.getJSONObject("album");JSONArray artists=raw.getJSONArray("ar");if(artists==null)artists=raw.getJSONArray("artists");JSONArray names=new JSONArray();if(artists!=null)for(Object item:artists)names.add(((JSONObject)item).getString("name"));return JSONObject.of("id",raw.getString("id"),"plugName","netease","name",raw.getString("name"),"artistName",names,"albumName",album==null?"":album.getString("name"),"pic",album==null?"":album.getString("picUrl"),"duration",raw.getLongValue("dt"),"brTypes",JSONArray.of("NETEASE_FLAC_2000","NETEASE_MP3_320","NETEASE_MP3_128"));}
    public JSONObject daily(String p)throws Exception{
        provider(p);JSONObject ctx=context(p);String key=digest(secret(p))+LocalDate.now(ZoneId.of("Asia/Shanghai"));Cached saved=cache.get(p+":daily");if(saved!=null&&saved.account.equals(key)&&saved.expires>System.currentTimeMillis())return JSONObject.parseObject(saved.value.toJSONString());JSONArray songs;
        if(p.equals("qqvip")){JSONObject c=qqCookie();JSONObject feed=q(c,"music.recommend.RecommendFeed","get_recommend_feed",JSONObject.of("userid",c.getString("musicid")));String id=dailyId(feed);if(id==null)throw new IllegalStateException("DAILY_RECOMMENDATION_UNAVAILABLE");songs=qqTracks(c,JSONObject.of("id",id,"dirId","0"));}
        else{JSONObject data=ne("/recommend/songs",new JSONObject()).getJSONObject("data");songs=new JSONArray();for(Object raw:array(data,"dailySongs"))songs.add(neSong((JSONObject)raw));}
        if(songs.isEmpty())throw new IllegalStateException("DAILY_RECOMMENDATION_UNAVAILABLE");JSONObject out=JSONObject.of("provider",p,"date",LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),"tracks",songs,"title",p.equals("qqvip")?"每日30首":"每日推荐","playlists",ctx.getJSONArray("playlists"),"likedIds",ctx.getJSONArray("likedIds"));cache.put(p+":daily",new Cached(key,System.currentTimeMillis()+1800000,out));return JSONObject.parseObject(out.toJSONString());
    }
    static JSONObject owned(JSONObject context,String id){for(Object item:array(context,"playlists")){JSONObject list=(JSONObject)item;if(id.equals(list.getString("id")))return list;}throw new IllegalArgumentException("PLAYLIST_NOT_OWNED");}
    static boolean contains(JSONArray tracks,String id){return tracks.stream().anyMatch(t->id.equals(((JSONObject)t).getString("id")));}
    public JSONObject change(String p,String mid,String target,Boolean like)throws Exception{
        provider(p);if(mid==null||!mid.matches("[A-Za-z0-9]{1,64}"))throw new IllegalArgumentException("Invalid song identity");
        cache.remove(p+":context");JSONObject ctx=context(p),list;String credential=secret(p);if(!digest(credential).equals(ctx.getString("_account")))throw new IllegalStateException("PLATFORM_ACCOUNT_CHANGED");
        if(like!=null){list=array(ctx,"playlists").stream().map(JSONObject.class::cast).filter(x->"likes".equals(x.getString("kind"))).findFirst().orElseThrow(()->new IllegalStateException("PLATFORM_LIBRARY_INCOMPLETE"));}
        else list=owned(ctx,target);
        boolean want=like==null||like;JSONObject normalized=null;
        if(p.equals("qqvip")){
            JSONObject c=JSONObject.parseObject(credential);JSONArray before=qqTracks(c,list);if(contains(before,mid)==want)return JSONObject.of("confirmed",true,"liked",want,"playlistId",list.getString("id"),"alreadyPresent",true);
            JSONObject payload=JSONObject.parseObject(qq.getqqSearchEntity().musicInfoRequestParam(mid)).getJSONObject("songinfo"),data=q(c,payload.getString("module"),payload.getString("method"),payload.getJSONObject("param")),track=data.getJSONObject("track_info");
            if(track==null||!mid.equals(track.getString("mid"))||track.getLongValue("id")==0)throw new IllegalStateException("PLATFORM_SONG_UNAVAILABLE");normalized=AccountCollectionService.song(track);
            if(!digest(secret(p)).equals(ctx.getString("_account")))throw new IllegalStateException("PLATFORM_ACCOUNT_CHANGED");
            writeQq(c,"music.musicasset.PlaylistDetailWrite",want?"AddSonglist":"DelSonglist",JSONObject.of("dirId",Integer.parseInt(list.getString("dirId")),"v_songInfo",JSONArray.of(JSONObject.of("songId",track.getLongValue("id"),"songType",track.getIntValue("type")))));
            if(contains(qqTracks(c,list),mid)!=want)throw new IllegalStateException("PLATFORM_WRITE_UNCONFIRMED");
            collections.confirmedChange(digest(c.getString("musicid")),list.getString("id"),normalized,want);
        }else{
            if(!mid.matches("[0-9]+"))throw new IllegalArgumentException("Invalid song identity");
            if(!digest(secret(p)).equals(ctx.getString("_account")))throw new IllegalStateException("PLATFORM_ACCOUNT_CHANGED");
            if(like!=null){ne("/like",JSONObject.of("id",mid,"like",like));boolean actual=array(ne("/likelist",JSONObject.of("uid",ctx.getLongValue("uid"))),"ids").stream().anyMatch(x->mid.equals(x.toString()));if(actual!=want)throw new IllegalStateException("PLATFORM_WRITE_UNCONFIRMED");}
            else{ne("/playlist/tracks",JSONObject.of("op","add","pid",list.getString("id"),"tracks",mid));JSONObject detail=ne("/playlist/detail",JSONObject.of("id",list.getString("id"))).getJSONObject("playlist");if(detail==null||!array(detail,"trackIds").stream().anyMatch(x->mid.equals(((JSONObject)x).getString("id"))))throw new IllegalStateException("PLATFORM_WRITE_UNCONFIRMED");}
        }
        cache.remove(p+":context");cache.remove(p+":daily");return JSONObject.of("confirmed",true,"liked",want,"playlistId",list.getString("id"));
    }
    static String safeError(Exception e){String m=e.getMessage();return m!=null&&m.matches("[A-Z_]+")?m:"PLATFORM_UNAVAILABLE";}
}
