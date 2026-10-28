package com.sqmusicplus.v3.library;
import com.alibaba.fastjson2.*;
import java.util.*;
public class ListeningFeaturesTest {
    public static void main(String[] args)throws Exception{
        check(DownloadTelemetry.snapshot(999,"loading").get("percent")==null,"Unknown size must be indeterminate");
        DownloadTelemetry.update(900,25,100);check(DownloadTelemetry.snapshot(900,"loading").get("percent").equals(25),"Real bytes");
        DownloadTelemetry.update(900,100,100);check(DownloadTelemetry.snapshot(900,"loading").get("percent").equals(99),"Tagging is not complete");
        check(DownloadTelemetry.snapshot(900,"success").get("percent").equals(100),"DB confirms complete");
        DownloadTelemetry.update(901,10,-1);check(DownloadTelemetry.snapshot(901,"loading").get("percent")==null,"Unknown length");
        check(DownloadTelemetry.snapshot(900,"loading").get("bytes").equals(100L),"Task counters isolated");
        JSONObject raw=JSONObject.parseObject("{\"mid\":\"test\",\"name\":\"Track\",\"interval\":123,\"singer\":[{\"name\":\"Artist\"}],\"album\":{\"mid\":\"abc\",\"name\":\"Album\"},\"file\":{\"size_flac\":10,\"size_320mp3\":5},\"private_field\":\"never expose\"}");
        JSONObject song=AccountCollectionService.song(raw);
        check(song.getJSONArray("brTypes").equals(JSONArray.of("QQVIP_Flac_2000","QQVIP_MP3_320")),"Only offered qualities");
        check(!song.containsKey("private_field"),"Allowlisted metadata only");
        check(song.getString("duration").equals("123000"),"Duration milliseconds");
        check(AccountCollectionService.safeCover("javascript:alert(1)").isEmpty(),"Unsafe cover rejected");
        var prior=new HashMap<>(com.sqmusicplus.v3.config.SqConfigCache.sqConfigMap);
        var setting=new com.sqmusicplus.v3.base.entity.SqConfig();setting.setConfigKey("system.download.file.audio.format");setting.setConfigValue("auto");com.sqmusicplus.v3.config.SqConfigCache.sqConfigMap.put(setting.getConfigKey(),setting);
        java.nio.file.Path dir=java.nio.file.Files.createTempDirectory("sq-collection-test");int[] queued={0};
        try{
            var tasks=(com.sqmusicplus.v3.base.service.DownloadInfoService)java.lang.reflect.Proxy.newProxyInstance(ListeningFeaturesTest.class.getClassLoader(),new Class[]{com.sqmusicplus.v3.base.service.DownloadInfoService.class},(proxy,method,input)->{
                if(method.getName().equals("list"))return List.of();
                if(method.getName().equals("add")){var task=(com.sqmusicplus.v3.base.entity.DownloadInfo)input[0];if(task.getDownloadMusicId().equals("bad"))throw new IllegalStateException("fixture failure");queued[0]++;return true;}
                throw new AssertionError(method.getName());
            });
            var service=new AccountCollectionService(null,new com.sqmusicplus.v3.plug.qqvip.QQvipHander(),tasks,null,dir.toString());
            JSONObject bad=JSONObject.parseObject(song.toJSONString());bad.put("id","bad");JSONObject good=JSONObject.parseObject(song.toJSONString());good.put("id","good");
            JSONObject list=new JSONObject();list.put("tracks",JSONArray.of(bad,good,bad,good));
            check(service.enqueue(JSONArray.of(list))==1&&queued[0]==1,"One invalid track cannot abort other playlists or duplicate downloads");
            check(service.summary().getIntValue("queueFailures")==1,"Report failed insert count");
        }finally{com.sqmusicplus.v3.config.SqConfigCache.sqConfigMap.clear();com.sqmusicplus.v3.config.SqConfigCache.sqConfigMap.putAll(prior);java.nio.file.Files.deleteIfExists(dir.resolve("qq-collection.json"));java.nio.file.Files.delete(dir);}
        System.out.println("LISTENING_FEATURES_TESTS_PASSED=12");
    }
    static void check(boolean value,String description){if(!value)throw new AssertionError(description);}
}
