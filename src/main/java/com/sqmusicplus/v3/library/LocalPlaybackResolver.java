package com.sqmusicplus.v3.library;

import com.sqmusicplus.v3.base.entity.DownloadInfo;
import com.sqmusicplus.v3.base.service.DownloadInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.*;

/** A completed task establishes platform identity. Metadata only locates its file. */
@Service
public class LocalPlaybackResolver {
    private final LibraryService library; private final DownloadInfoService tasks;
    public LocalPlaybackResolver(LibraryService library, DownloadInfoService tasks){this.library=library;this.tasks=tasks;}
    static String text(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);}
    static String artists(String value){return Arrays.stream(text(value).split("\\s*[/;&、]\\s*")).sorted().reduce((a,b)->a+";"+b).orElse("");}
    static String album(String value){return text(value).isBlank()?"单曲":text(value);}
    static List<LibraryService.Track> candidates(List<LibraryService.Track> files,List<DownloadInfo> rows,String format){
        return files.stream().filter(t->t.playable()&&t.size()>0&&(format.equals("auto")||format.equals(t.format())))
            .filter(t->rows.stream().anyMatch(r->Set.of("success","supplement_success").contains(r.getDownloadStatus())
                &&text(t.title()).equals(text(r.getDownloadMusicname()))&&artists(t.artist()).equals(artists(r.getDownloadArtistname()))
                &&album(t.album()).equals(album(r.getDownloadAlbumname()))))
            .filter(t->files.stream().filter(other->other.playable()&&other.format().equals(t.format())&&text(other.title()).equals(text(t.title()))
                &&artists(other.artist()).equals(artists(t.artist()))&&album(other.album()).equals(album(t.album()))).count()==1)
            .sorted(Comparator.comparingInt((LibraryService.Track t)->switch(t.format()){case "flac","wav","aiff"->0;case "m4a","aac"->1;case "mp3"->2;default->3;})).toList();
    }
    public Map<String,Object> resolve(String provider,String id,String format,String quality)throws Exception{
        if(!Set.of("qqvip","qq","netease","kw","kg","mg","tidal","apple","qobuz").contains(provider)||id==null||!id.matches("[A-Za-z0-9,_-]{1,128}"))throw new IllegalArgumentException("Invalid track identity");
        if(!Set.of("auto","flac","mp3","m4a","aac","wav","ogg","opus","aiff").contains(format))throw new IllegalArgumentException("Invalid format");
        var rows=tasks.list(new LambdaQueryWrapper<DownloadInfo>().eq(DownloadInfo::getDownloadPlugName,provider.equals("qq")?"qqvip":provider)
            .eq(DownloadInfo::getDownloadMusicId,id).in(DownloadInfo::getDownloadStatus,List.of("success","supplement_success")));
        com.sqmusicplus.v3.base.enums.PlugBrType requested=null;
        if(!"auto".equals(quality)){requested=com.sqmusicplus.v3.base.enums.PlugBrType.findById(quality);if(requested==null)try{requested=com.sqmusicplus.v3.base.enums.PlugBrType.valueOf(quality);}catch(IllegalArgumentException ignored){}}
        for(var t:candidates(library.all(),rows,format)){
            try{if(java.nio.file.Files.size(library.file(t.id()))==0)continue;
                long bitrate=0;try{bitrate=org.jaudiotagger.audio.AudioFileIO.read(library.file(t.id()).toFile()).getAudioHeader().getBitRateAsNumber();}catch(Exception ignored){}
                if(requested!=null&&(!requested.getType().equals(t.format())||(t.format().equals("mp3")&&bitrate<requested.getBit())))continue;
                String actual=t.format().equals("mp3")&&bitrate>0?"MP3 "+bitrate+" kbps":t.format().toUpperCase(java.util.Locale.ROOT);String ticket=library.ticket(t.id());
                return Map.of("matched",true,"track",t,"quality",actual,"url","/api/library/stream/"+t.id()+"?ticket="+ticket,"coverUrl",t.cover()?"/api/library/cover/"+t.id()+"?ticket="+ticket:"");
            }catch(java.nio.file.NoSuchFileException|NoSuchElementException ignored){}
        }
        return Map.of("matched",false);
    }
}
