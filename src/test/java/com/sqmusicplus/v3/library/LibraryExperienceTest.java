package com.sqmusicplus.v3.library;
import com.alibaba.fastjson2.*;
import com.sqmusicplus.v3.base.entity.*;
import com.sqmusicplus.v3.config.SqConfigCache;
import com.sqmusicplus.v3.plug.qq.hander.QQHander;
import java.util.*;
public class LibraryExperienceTest {
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    static LibraryService.Track file(String id,String title,String format){return new LibraryService.Track(id,title,"Artist","Album",id+"."+format,format,100,1000,0,false,false,true);}
    public static void main(String[] args)throws Exception{
        DownloadInfo row=new DownloadInfo().setDownloadMusicname("Song").setDownloadArtistname("Artist").setDownloadAlbumname("Album").setDownloadStatus("success");
        var mp3=file("mp3","Song","mp3");var flac=file("flac","Song","flac");
        check(LocalPlaybackResolver.candidates(List.of(mp3,flac),List.of(row),"auto").get(0).format().equals("flac"),"Highest local version");
        check(LocalPlaybackResolver.candidates(List.of(mp3,flac),List.of(row),"mp3").equals(List.of(mp3)),"Explicit format retained");
        check(LocalPlaybackResolver.candidates(List.of(file("live","Song (Live)","flac")),List.of(row),"auto").isEmpty(),"Never match another version by loose title");
        check(LocalPlaybackResolver.candidates(List.of(flac,file("duplicate","Song","flac")),List.of(row),"auto").isEmpty(),"Ambiguous files must not be guessed");
        row.setDownloadStatus("loading");check(LocalPlaybackResolver.candidates(List.of(mp3),List.of(row),"auto").isEmpty(),"Incomplete download is never played");
        check(PlatformMusicService.dailyId(JSONObject.parseObject("{\"v_shelf\":[{\"id\":\"wrong\",\"subtype\":1},{\"subtype\":510,\"id\":\"1234\"}]}")).equals("1234"),"Only actual daily feed selected");
        check(PlatformMusicService.dailyId(JSONObject.of("id","bad","subtype",510))==null,"Validate recommendation identity");
        check(QqAccountTransport.sign("123").equals("zzcec1b555gzqzg7laztguyjl2bu20r6x1w50c55f60"),"Published zzc test vector");
        check(!QqAccountTransport.CLIENT.retryOnConnectionFailure(),"No automatic write replay");
        check(PlatformMusicService.neSong(JSONObject.of("id",1,"name","Song")).getJSONArray("brTypes").isEmpty(),"Never invent source qualities");
        check(PlatformMusicService.neSong(JSONObject.of("id",1,"h",JSONObject.of("size",100),"sq",JSONObject.of("size",0))).getJSONArray("brTypes").equals(JSONArray.of("NETEASE_MP3_320")),"Only advertised versions exposed");
        var before=new HashMap<>(SqConfigCache.sqConfigMap);java.nio.file.Path dir=java.nio.file.Files.createTempDirectory("sq-experience-test");
        try{
            SqConfig setting=new SqConfig();setting.setConfigKey("plug.qqvip.cookie");setting.setConfigValue("{\"musicid\":\"1\",\"musickey\":\"fixture\",\"loginType\":2}");SqConfigCache.sqConfigMap.put(setting.getConfigKey(),setting);
            QQHander qq=new QQHander(){@Override public Boolean checkLoginStatus(){return true;}};var field=QQHander.class.getDeclaredField("qqSearchEntity");field.setAccessible(true);field.set(qq,new com.sqmusicplus.v3.plug.qq.entity.QQSearchEntity());
            AccountCollectionService snapshots=new AccountCollectionService(null,null,null,null,dir.toString());
            class Fake extends PlatformMusicService {
                Set<String> liked=new HashSet<>(Set.of("old"));int writes;boolean accept=true;
                Fake(){super(qq,null,snapshots);}
                @Override JSONObject q(JSONObject c,String module,String method,JSONObject param){
                    if(method.equals("GetPlaylistByUin"))return JSONObject.of("total",1,"v_playlist",JSONArray.of(JSONObject.of("tid","42","dirId","201","dirName","Likes")));
                    if(method.equals("CgiGetDiss")){JSONArray songs=new JSONArray();for(String id:liked)songs.add(JSONObject.of("mid",id,"name","Song","id",55,"album",JSONObject.of("mid","album","name","Album")));return JSONObject.of("total_song_num",songs.size(),"songlist",songs);}
                    return JSONObject.of("track_info",JSONObject.of("mid",param.getString("song_mid"),"id",55,"name","Song","album",JSONObject.of("mid","album","name","Album")));
                }
                @Override JSONObject writeQq(JSONObject c,String module,String method,JSONObject param){writes++;if(accept)liked.add("fresh");return JSONObject.of("retCode",0);}
            }
            Fake fake=new Fake();check(fake.change("qqvip","old",null,true).getBooleanValue("alreadyPresent")&&fake.writes==0,"Already liked is idempotent");
            boolean forbidden=false;try{fake.change("qqvip","fresh","999",null);}catch(IllegalArgumentException expected){forbidden=true;}check(forbidden&&fake.writes==0,"Foreign playlist rejected before write");
            fake.accept=false;boolean unconfirmed=false;try{fake.change("qqvip","fresh",null,true);}catch(IllegalStateException expected){unconfirmed=expected.getMessage().equals("PLATFORM_WRITE_UNCONFIRMED");}check(unconfirmed,"Success code without changed membership is not success");
            fake.accept=true;check(fake.change("qqvip","fresh",null,true).getBooleanValue("confirmed"),"Write confirmed by subsequent read");
        }finally{SqConfigCache.sqConfigMap.clear();SqConfigCache.sqConfigMap.putAll(before);java.nio.file.Files.deleteIfExists(dir.resolve("qq-collection.json"));java.nio.file.Files.delete(dir);}
        System.out.println("LIBRARY_EXPERIENCE_TESTS_PASSED=15");
    }
}
