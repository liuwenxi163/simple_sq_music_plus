package com.sqmusicplus.v3.controller;

import com.alibaba.fastjson2.JSONObject;
import com.sqmusicplus.v3.base.entity.*;
import com.sqmusicplus.v3.base.enums.SetConfigEnum;
import com.sqmusicplus.v3.base.enums.PlugBrType;
import com.sqmusicplus.v3.config.SqConfigCache;
import com.sqmusicplus.v3.download.vo.DownloadUrlResult;
import com.sqmusicplus.v3.plug.entity.*;
import com.sqmusicplus.v3.plug.qqvip.QQvipHander;
import java.util.List;

/** Calls the actual playback endpoint and real quality conversion, without external music requests. */
public class PlaybackQualityTest {
    static class Source extends QQvipHander {
        DownloadInfo received;int metadataCalls;
        @Override public Music querySongById(String id) {
            metadataCalls++;
            return new Music().setId(id).setMusicName("Test").setMusicArtists(List.of("Artist"))
                .setMusicAlbum("Album").setDataInfo(new JSONObject())
                .setBits(List.of(PlugBrType.QQVIP_Flac_2000,PlugBrType.QQVIP_MP3_320,PlugBrType.QQVIP_MP3_128));
        }
        @Override public DownloadUrlResult getDownloadUrl(DownloadInfo info) {
            received=info;DownloadUrlResult result=new DownloadUrlResult();result.setPlugBrTypeId(info.getDownloadBrType());return result;
        }
    }
    @org.junit.jupiter.api.Test
    public void independentPlaybackQuality() throws Exception {main(new String[0]);}
    public static void main(String[] args) throws Exception {
        var previous=new java.util.HashMap<>(SqConfigCache.sqConfigMap);
        try {
            Source source=new Source();MusicController controller=new MusicController();controller.searchHanderAbstractList=List.of(source);
            for(String preference:List.of("auto","flac","mp3","")) {
                SqConfig config=new SqConfig();config.setConfigKey(SetConfigEnum.SYSTEM_DOWNLOAD_FILE_AUDIO_FORMAT.getKey());config.setConfigValue(preference);SqConfigCache.sqConfigMap.put(config.getConfigKey(),config);
                for(PlugBrType quality:List.of(PlugBrType.QQVIP_MP3_320,PlugBrType.QQVIP_MP3_128,PlugBrType.QQVIP_Flac_2000)) {
                    PlugDownloadSongParam request=new PlugDownloadSongParam();request.setId("public-test-track");request.setPlugName("qqvip");request.setBrType(quality);
                    controller.getDownloadUrl(request);
                    if(!quality.getId().equals(source.received.getDownloadBrType()))throw new AssertionError("Download preference changed playback quality");
                    if(!preference.equals(SqConfigCache.getSqConfigValue(SetConfigEnum.SYSTEM_DOWNLOAD_FILE_AUDIO_FORMAT)))throw new AssertionError("Playback changed download settings");
                }
            }
            if(source.metadataCalls!=0)throw new AssertionError("QQ playback waited for unrelated metadata/lyrics");
            System.out.println("PLAYBACK_QUALITY_TESTS passed=13");
        }finally{SqConfigCache.sqConfigMap.clear();SqConfigCache.sqConfigMap.putAll(previous);}
    }
}
