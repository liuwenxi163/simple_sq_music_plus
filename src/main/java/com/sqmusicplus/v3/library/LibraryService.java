package com.sqmusicplus.v3.library;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqmusicplus.v3.config.SqConfigCache;
import com.sqmusicplus.v3.base.enums.SetConfigEnum;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** Local library index. One coalesced scan at a time; never rewrites music files. */
@Service
public class LibraryService {
    public record Track(String id,String title,String artist,String album,String path,String format,
                        int duration,long size,long modified,boolean lyrics,boolean cover,boolean playable){}
    private final ObjectMapper json;
    private final Path data;
    private final byte[] key;
    private final AtomicBoolean scanning=new AtomicBoolean();
    private volatile Map<String,Track> tracks=Map.of();
    private volatile Map<String,Object> scanState=Map.of("running",false,"scanned",0,"failed",0);
    private Map<String,Object> preferences;
    private static final Set<String> FORMATS=Set.of("mp3","flac","m4a","mp4","aac","wav","ogg","opus","ape","wma","aiff");
    private static final Set<String> PLAYABLE=Set.of("mp3","flac","m4a","mp4","aac","wav","ogg","opus");

    public LibraryService(ObjectMapper json,@Value("${SQ_DATA_DIR:/data/sqmusic}") String dir,@Value("${jwt.secret}") String secret) throws Exception {
        this.json=json;data=Path.of(dir).toAbsolutePath().normalize();Files.createDirectories(data);
        if(secret.getBytes(StandardCharsets.UTF_8).length<32)throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        key=secret.getBytes(StandardCharsets.UTF_8);
        preferences=loadPreferences();
    }
    Path root() throws Exception {
        String configured=SqConfigCache.getSqConfigValue(SetConfigEnum.SYSTEM_DOWNLOAD_PATH);
        if(configured==null || configured.isBlank())configured=System.getenv().getOrDefault("MUSIC_PATH","/music");
        return Path.of(configured).toRealPath();
    }
    @EventListener(ApplicationReadyEvent.class) public void ready(){scan();}
    @Scheduled(fixedDelayString="${SQ_LIBRARY_SCAN_MS:300000}") public void periodic(){scan();}
    public Map<String,Object> scan(){
        if(scanning.compareAndSet(false,true)){
            scanState=Map.of("running",true,"scanned",0,"failed",0);
            Thread thread=new Thread(this::scanFiles,"sq-library-scan");thread.setDaemon(true);thread.start();
        }
        return status();
    }
    public Map<String,Object> status(){return scanState;}
    private void scanFiles(){
        int count=0,failed=0;boolean truncated=false;
        scanState=Map.of("running",true,"scanned",0,"failed",0);
        try{
            Path root=root();Map<String,Track> next=new LinkedHashMap<>();
            try(Stream<Path> stream=Files.walk(root)){
                Iterator<Path> files=stream.filter(Files::isRegularFile).iterator();
                while(files.hasNext()){
                    Path p=files.next();String ext=extension(p);
                    if(!FORMATS.contains(ext))continue;
                    if(count>=100000){truncated=true;break;}
                    try{
                        Path real=p.toRealPath();if(!real.startsWith(root))continue;
                        String relative=root.relativize(real).toString().replace('\\','/');
                        String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((root+"\n"+relative).getBytes(StandardCharsets.UTF_8)));
                        long size=Files.size(real),modified=Files.getLastModifiedTime(real).toMillis();Track old=tracks.get(id);
                        boolean lyric=Files.isRegularFile(lyricPath(real));boolean cover=coverPath(real)!=null;
                        Track t;
                        if(old!=null && old.size()==size && old.modified()==modified){
                            t=new Track(id,old.title(),old.artist(),old.album(),relative,ext,old.duration(),size,modified,lyric||old.lyrics(),cover||old.cover(),PLAYABLE.contains(ext));
                        } else {
                            String title=stripExtension(real.getFileName().toString()),artist=real.getParent().getFileName().toString(),album="";int duration=0;
                            Path rel=root.relativize(real);
                            if(rel.getNameCount()>2){artist=rel.getName(0).toString();album=rel.getName(rel.getNameCount()-2).toString();}
                            if(rel.getNameCount()==1)artist="未知艺术家";
                            try{
                                var audio=AudioFileIO.read(real.toFile());Tag tag=audio.getTag();
                                duration=audio.getAudioHeader().getTrackLength();
                                if(tag!=null){title=value(tag,FieldKey.TITLE,title);artist=value(tag,FieldKey.ARTIST,artist);album=value(tag,FieldKey.ALBUM,album);
                                    lyric=lyric||!value(tag,FieldKey.LYRICS,"").isBlank();cover=cover||tag.getFirstArtwork()!=null;}
                            }catch(Exception ignored){failed++;}
                            t=new Track(id,title,artist,album,relative,ext,duration,size,modified,lyric,cover,PLAYABLE.contains(ext));
                        }
                        next.put(id,t);count++;
                        if(count%100==0)scanState=Map.of("running",true,"scanned",count,"failed",failed);
                    }catch(Exception ignored){failed++;}
                }
            }
            tracks=Collections.unmodifiableMap(next);
            scanState=Map.of("running",false,"scanned",count,"failed",failed,"truncated",truncated,"finishedAt",Instant.now().toString());
        }catch(Exception error){scanState=Map.of("running",false,"scanned",count,"failed",failed,"error","LIBRARY_UNAVAILABLE");}
        finally{scanning.set(false);}
    }
    private static String value(Tag tag,FieldKey key,String fallback){try{String v=tag.getFirst(key);return v==null||v.isBlank()?fallback:v;}catch(Exception e){return fallback;}}
    private static String extension(Path p){String n=p.getFileName().toString();int i=n.lastIndexOf('.');return i<0?"":n.substring(i+1).toLowerCase(Locale.ROOT);}
    private static String stripExtension(String n){int i=n.lastIndexOf('.');return i<0?n:n.substring(0,i);}
    private static Path lyricPath(Path file){return file.resolveSibling(stripExtension(file.getFileName().toString())+".lrc");}
    private static Path coverPath(Path file){for(String n:List.of("cover.jpg","cover.png","folder.jpg","folder.png")){Path p=file.resolveSibling(n);if(Files.isRegularFile(p))return p;}return null;}
    public List<Track> all(){return tracks.values().stream().sorted(Comparator.comparingLong(Track::modified).reversed()).toList();}
    public Track track(String id){Track t=tracks.get(id);if(t==null)throw new NoSuchElementException("Track not found");return t;}
    public Path file(String id) throws Exception {
        Path root=root(),p=root.resolve(track(id).path()).toRealPath();
        if(!p.startsWith(root)||!Files.isRegularFile(p))throw new NoSuchElementException("Track not found");return p;
    }
    public String lyrics(String id) throws Exception {
        Path file=file(id),lyric=lyricPath(file);
        if(Files.isRegularFile(lyric) && lyric.toRealPath().startsWith(root()) && Files.size(lyric)<2*1024*1024)return Files.readString(lyric);
        try{Tag tag=AudioFileIO.read(file.toFile()).getTag();if(tag!=null)return value(tag,FieldKey.LYRICS,"");}catch(Exception ignored){}
        return "";
    }
    public byte[] cover(String id) throws Exception {
        Path file=file(id),cover=coverPath(file);
        if(cover!=null && cover.toRealPath().startsWith(root()) && Files.size(cover)<16*1024*1024)return Files.readAllBytes(cover);
        try{var art=AudioFileIO.read(file.toFile()).getTag().getFirstArtwork();if(art!=null && art.getBinaryData().length<16*1024*1024)return art.getBinaryData();}catch(Exception ignored){}
        return new byte[0];
    }
    private String sign(String id,long exp) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(("media\n"+id+"\n"+exp).getBytes(StandardCharsets.UTF_8)));
    }
    public String ticket(String id) throws Exception {track(id);long exp=Instant.now().getEpochSecond()+3600;return exp+"."+sign(id,exp);}
    public boolean valid(String id,String ticket){
        try{if(ticket==null||ticket.length()>120||!id.matches("[0-9a-f]{64}"))return false;String[] p=ticket.split("\\.",2);long exp=Long.parseLong(p[0]);long now=Instant.now().getEpochSecond();
            return exp>=now && exp<=now+3605 && MessageDigest.isEqual(sign(id,exp).getBytes(StandardCharsets.US_ASCII),p[1].getBytes(StandardCharsets.US_ASCII));
        }catch(Exception e){return false;}
    }
    public synchronized Map<String,Object> preferences(){Map<String,Object> copy=json.convertValue(preferences,new TypeReference<Map<String,Object>>(){});copy.putIfAbsent("localFirst",true);return copy;}
    private Map<String,Object> loadPreferences(){try{return json.readValue(data.resolve("listening.json").toFile(),new TypeReference<Map<String,Object>>(){});}catch(Exception e){return new LinkedHashMap<>(Map.of("favorites",List.of(),"playlists",List.of(),"recent",List.of()));}}
    public synchronized Map<String,Object> savePreferences(Map<String,Object> input) throws Exception {
        Map<String,Object> next=new LinkedHashMap<>(preferences);
        if(input.containsKey("localFirst")){if(!(input.get("localFirst") instanceof Boolean))throw new IllegalArgumentException("Invalid playback preference");next.put("localFirst",input.get("localFirst"));}
        for(String name:List.of("favorites","playlists","recent"))if(input.containsKey(name)){
            Object value=input.get(name);if(!(value instanceof List<?> list) || list.size()>10000)throw new IllegalArgumentException("Invalid library state");
            next.put(name,value);
        }
        byte[] bytes=json.writeValueAsBytes(next);if(bytes.length>4*1024*1024)throw new IllegalArgumentException("Library state too large");
        Path tmp=data.resolve("listening.json.tmp");Files.write(tmp,bytes);try{Files.move(tmp,data.resolve("listening.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,data.resolve("listening.json"),StandardCopyOption.REPLACE_EXISTING);}
        preferences=next;return preferences();
    }
}
