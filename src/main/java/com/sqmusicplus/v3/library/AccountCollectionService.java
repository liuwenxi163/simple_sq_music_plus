package com.sqmusicplus.v3.library;

import com.alibaba.fastjson2.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sqmusicplus.v3.base.entity.DownloadInfo;
import com.sqmusicplus.v3.base.enums.*;
import com.sqmusicplus.v3.base.service.DownloadInfoService;
import com.sqmusicplus.v3.config.SqConfigCache;
import com.sqmusicplus.v3.plug.entity.PlugSearchMusicResult;
import com.sqmusicplus.v3.plug.qq.hander.QQHander;
import com.sqmusicplus.v3.plug.qqvip.QQvipHander;
import com.sqmusicplus.v3.utils.QqLoginTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Account collection snapshots survive restarts. Never writes to the QQ account. */
@Service
public class AccountCollectionService {
    private final QQHander qq;private final QQvipHander vip;private final DownloadInfoService downloads;
    private final LibraryService library;private final Path file;private JSONObject state;
    private final AtomicBoolean running=new AtomicBoolean();
    public AccountCollectionService(QQHander qq,QQvipHander vip,DownloadInfoService downloads,LibraryService library,@Value("${SQ_DATA_DIR:/data/sqmusic}") String dir)throws Exception{
        this.qq=qq;this.vip=vip;this.downloads=downloads;this.library=library;file=Path.of(dir).resolve("qq-collection.json");
        state=Files.isRegularFile(file)?JSONObject.parseObject(Files.readString(file)):new JSONObject();
        state.putIfAbsent("enabled",false);state.putIfAbsent("autoDownload",true);state.putIfAbsent("playlists",new JSONArray());
    }
    public synchronized JSONObject status(){JSONObject copy=JSONObject.parseObject(state.toJSONString());copy.put("running",running.get());copy.put("intervalMinutes",10);copy.remove("accountKey");return copy;}
    public synchronized JSONObject summary(){JSONObject copy=new JSONObject();for(String key:List.of("enabled","autoDownload","lastAttempt","lastSuccess","error","trackCount","queuedLastRun"))if(state.containsKey(key))copy.put(key,state.get(key));copy.put("running",running.get());copy.put("intervalMinutes",10);return copy;}
    public synchronized JSONObject configure(boolean enabled,boolean autoDownload)throws Exception{state.put("enabled",enabled);state.put("autoDownload",autoDownload);persist();return status();}
    private synchronized void persist()throws Exception{
        Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temp,state.toJSONString());
        try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
    @Scheduled(fixedDelay=600000,initialDelay=30000) public void periodic(){if(status().getBooleanValue("enabled"))start();}
    public JSONObject start(){
        if(running.compareAndSet(false,true)){Thread worker=new Thread(this::sync,"sq-qq-collection");worker.setDaemon(true);worker.start();}
        return status();
    }
    private JSONObject request(String payload,JSONObject cookie)throws Exception{
        JSONObject body=JSONObject.parseObject(payload),comm=body.getJSONObject("comm");
        comm.put("qq",cookie.getString("musicid"));comm.put("authst",cookie.getString("musickey"));comm.put("tmeLoginType",cookie.getString("loginType"));
        JSONObject root=JSONObject.parseObject(QqLoginTransport.readOnlyApi(qq.getConfig().getSearchUrl(),body.toJSONString()));
        JSONObject req=root.getJSONObject("req");
        if(root.getIntValue("code")!=0||req==null||req.getIntValue("code")!=0||req.getJSONObject("data")==null)throw new IllegalStateException("QQ_COLLECTION_UNAVAILABLE");
        JSONObject data=req.getJSONObject("data");if(data.containsKey("code")&&data.getIntValue("code")!=0)throw new IllegalStateException("QQ_COLLECTION_UNAVAILABLE");return data;
    }
    private static JSONArray rows(JSONObject object,String key){JSONArray result=object.getJSONArray(key);return result==null?new JSONArray():result;}
    private JSONObject playlist(JSONObject raw,boolean owned){
        String tid=raw.getString("tid"),dir=raw.getString("dirId");if(tid==null||!tid.matches("[0-9]+")||"0".equals(tid))return null;
        JSONObject result=new JSONObject();result.put("id","qqvip:"+tid);result.put("source","qqvip");result.put("remoteId",tid);result.put("dirId",dir==null?"0":dir);
        result.put("name","201".equals(dir)?"我喜欢":raw.getString(owned?"dirName":"name"));result.put("kind","201".equals(dir)?"likes":owned?"owned":"collected");
        String cover=raw.getString(owned?"picUrl":"logo");result.put("coverUrl",safeCover(cover));return result;
    }
    static String safeCover(String url){return url!=null&&url.startsWith("https://")?url:"";}
    static JSONObject song(JSONObject raw){
        String mid=raw.getString("mid");if(mid==null||mid.isBlank())return null;
        JSONObject result=new JSONObject();result.put("id",mid);result.put("plugName","qqvip");result.put("name",raw.getString("name"));
        JSONArray artists=new JSONArray();for(Object entry:rows(raw,"singer"))artists.add(((JSONObject)entry).getString("name"));result.put("artistName",artists);
        JSONObject album=raw.getJSONObject("album");result.put("albumName",album==null?"":album.getString("name"));result.put("albumid",album==null?"":album.getString("mid"));
        String albumId=album==null?null:album.getString("mid");result.put("pic",albumId!=null&&albumId.matches("[A-Za-z0-9]+")?"https://y.gtimg.cn/music/photo_new/T002R300x300M000"+albumId+".jpg":"");
        result.put("duration",String.valueOf(raw.getLongValue("interval")*1000));JSONArray qualities=new JSONArray();JSONObject media=raw.getJSONObject("file");
        if(media!=null){if(media.getLongValue("size_flac")>0)qualities.add("QQVIP_Flac_2000");if(media.getLongValue("size_320mp3")>0)qualities.add("QQVIP_MP3_320");if(media.getLongValue("size_128mp3")>0)qualities.add("QQVIP_MP3_128");}
        result.put("brTypes",qualities);return result;
    }
    private JSONArray fetchSongs(JSONObject list,JSONObject cookie,long deadline)throws Exception{
        JSONArray songs=new JSONArray();Set<String> seen=new HashSet<>();long total=Long.MAX_VALUE;
        for(long page=1;songs.size()<total;page++){
            if(page>100||System.currentTimeMillis()>deadline)throw new IllegalStateException("COLLECTION_LIMIT_REACHED");
            JSONObject data=request(qq.getqqSearchEntity().songListInfoRequestParam(list.getString("remoteId"),list.getString("dirId"),page,50L),cookie);
            JSONArray batch=rows(data,"songlist");if(!data.containsKey("total_song_num"))throw new IllegalStateException("QQ_COLLECTION_UNAVAILABLE");total=data.getLongValue("total_song_num");
            int added=0;for(Object entry:batch){JSONObject track=song((JSONObject)entry);if(track!=null&&seen.add(track.getString("id"))){songs.add(track);added++;}}
            if(batch.isEmpty()||added==0){if(songs.size()<total)throw new IllegalStateException("QQ_COLLECTION_INCOMPLETE");break;}
        }
        return songs;
    }
    private int enqueue(JSONArray lists)throws Exception{
        // Include pending and failed rows in the identity set; failed tasks are
        // retried explicitly, never reinserted on every synchronization tick.
        Map<String,Integer> known=new HashMap<>();
        for(DownloadInfo row:downloads.list(new LambdaQueryWrapper<DownloadInfo>().eq(DownloadInfo::getDownloadPlugName,"qqvip"))){PlugBrType type=PlugBrType.findById(row.getDownloadBrType());if(type!=null)known.merge(row.getDownloadMusicId(),type.getBit(),Math::max);}
        int added=0;String format=SqConfigCache.getSqConfigValue(SetConfigEnum.SYSTEM_DOWNLOAD_FILE_AUDIO_FORMAT);if(format==null||format.isBlank())format="auto";
        for(Object item:lists)for(Object entry:rows((JSONObject)item,"tracks")){
            JSONObject track=(JSONObject)entry;String id=track.getString("id");
            List<PlugBrType> types=rows(track,"brTypes").stream().map(t->PlugBrType.valueOf(t.toString())).toList();if(types.isEmpty())continue;
            PlugBrType quality=PlugBrType.findMaxByTypeAndPlugNameAndBits(format,"qqvip",types);if(quality==null)continue;
            if(known.getOrDefault(id,0)>=quality.getBit())continue;
            PlugSearchMusicResult metadata=new PlugSearchMusicResult().setId(id).setName(track.getString("name")).setArtistName(track.getList("artistName",String.class))
                .setAlbumName(track.getString("albumName")).setPlugName("qqvip").setBrTypes(types).setDataInfo(new JSONObject());
            if(downloads.add(vip.musicToDownloadInfo(metadata,quality,false))){known.put(id,quality.getBit());added++;}
        }
        return added;
    }
    private void sync(){
        long deadline=System.currentTimeMillis()+300000;
        try{
            synchronized(this){state.put("lastAttempt",Instant.now().toString());state.put("error","");persist();}
            String raw=SqConfigCache.getSqConfigValue(SetConfigEnum.PLUG_QQVIP_COOKIE);if(raw==null||raw.isBlank())throw new IllegalStateException("QQ_LOGIN_REQUIRED");
            JSONObject cookie=JSONObject.parseObject(raw);String account=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cookie.getString("musicid").getBytes(StandardCharsets.UTF_8)));
            String key=cookie.getString("musickey"),uin=cookie.getString("musicid"),login=cookie.getString("loginType");var helper=qq.getqqSearchEntity();
            LinkedHashMap<String,JSONObject> catalog=new LinkedHashMap<>();
            JSONObject own=request(helper.userSelfSongListParam(uin,key,login),cookie);
            if(!own.containsKey("v_playlist"))throw new IllegalStateException("QQ_COLLECTION_UNAVAILABLE");
            if(own.containsKey("bFinish")&&!own.getBooleanValue("bFinish"))throw new IllegalStateException("QQ_COLLECTION_INCOMPLETE");
            for(Object item:rows(own,"v_playlist")){JSONObject list=playlist((JSONObject)item,true);if(list!=null)catalog.put(list.getString("id"),list);}
            for(int page=1;page<=100;page++){
                JSONObject payload=JSONObject.parseObject(helper.followSongListParam(uin,key,login,cookie.getString("encryptUin"),50,page));
                payload.getJSONObject("req").getJSONObject("param").put("offset",(page-1)*50);
                JSONObject data=request(payload.toJSONString(),cookie);JSONArray batch=rows(data,"v_list");
                for(Object item:batch){JSONObject list=playlist((JSONObject)item,false);if(list!=null)catalog.putIfAbsent(list.getString("id"),list);}
                if((long)page*50>=data.getLongValue("total"))break;if(batch.isEmpty()||page==100)throw new IllegalStateException("QQ_COLLECTION_INCOMPLETE");
            }
            if(catalog.size()>200)throw new IllegalStateException("COLLECTION_LIMIT_REACHED");
            JSONArray lists=new JSONArray();int references=0;
            for(JSONObject list:catalog.values()){
                JSONArray tracks=fetchSongs(list,cookie,deadline);references+=tracks.size();if(references>50000)throw new IllegalStateException("COLLECTION_LIMIT_REACHED");
                list.put("tracks",tracks);list.put("count",tracks.size());lists.add(list);
            }
            synchronized(this){state.put("accountKey",account);state.put("playlists",lists);state.put("lastSuccess",Instant.now().toString());state.put("trackCount",references);persist();}
            int queued=status().getBooleanValue("autoDownload")?enqueue(lists):0;
            synchronized(this){state.put("queuedLastRun",queued);persist();}
            library.scan();
        }catch(Exception error){
            synchronized(this){state.put("error",error instanceof java.io.IOException?"QQ_NETWORK_UNAVAILABLE":error.getMessage()!=null&&error.getMessage().matches("[A-Z_]+")?error.getMessage():"QQ_COLLECTION_UNAVAILABLE");try{persist();}catch(Exception ignored){}}
        }finally{running.set(false);}
    }
}
