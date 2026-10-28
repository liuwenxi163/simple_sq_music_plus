package com.sqmusicplus.v3.library;
import com.alibaba.fastjson2.*;
import java.util.*;
public class ListeningFeaturesTest {
    public static void main(String[] args){
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
        System.out.println("LISTENING_FEATURES_TESTS_PASSED=10");
    }
    static void check(boolean value,String description){if(!value)throw new AssertionError(description);}
}
