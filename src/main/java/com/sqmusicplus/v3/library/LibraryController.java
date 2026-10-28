package com.sqmusicplus.v3.library;

import com.sqmusicplus.v3.config.AjaxResult;
import com.sqmusicplus.v3.config.RequireLogin;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/library")
@RequireLogin
public class LibraryController {
    private final LibraryService library;
    public LibraryController(LibraryService library){this.library=library;}
    @GetMapping("/tracks") public AjaxResult tracks(@RequestParam(name="q",defaultValue="") String q,@RequestParam(name="offset",defaultValue="0") int offset,@RequestParam(name="limit",defaultValue="100") int limit) throws Exception {
        String query=q.toLowerCase(Locale.ROOT);var all=library.all().stream().filter(t->(t.title()+" "+t.artist()+" "+t.album()).toLowerCase(Locale.ROOT).contains(query)).toList();
        var result=new ArrayList<Map<String,Object>>();
        for(var t:all.stream().skip(Math.max(0,offset)).limit(Math.max(1,Math.min(500,limit))).toList()){
            Map<String,Object> row=new LinkedHashMap<>();row.put("track",t);
            row.put("coverUrl",t.cover()?"/api/library/cover/"+t.id()+"?ticket="+library.ticket(t.id()):"");result.add(row);
        }
        return AjaxResult.success(Map.of("items",result,"total",all.size(),"scan",library.status()));
    }
    @PostMapping("/scan") public AjaxResult scan(){return AjaxResult.success(library.scan());}
    @GetMapping("/scan") public AjaxResult scanStatus(){return AjaxResult.success(library.status());}
    @GetMapping("/state") public AjaxResult state(){return AjaxResult.success(library.preferences());}
    @PutMapping("/state") public AjaxResult state(@RequestBody Map<String,Object> body) throws Exception{return AjaxResult.success(library.savePreferences(body));}
    @GetMapping("/lyrics/{id}") public AjaxResult lyrics(@PathVariable("id") String id) throws Exception{return AjaxResult.success("success",library.lyrics(id));}
    @PostMapping("/ticket/{id}") public AjaxResult ticket(@PathVariable("id") String id) throws Exception {
        String ticket=library.ticket(id);return AjaxResult.success(Map.of("url","/api/library/stream/"+id+"?ticket="+ticket,"expiresIn",3600));
    }
    private void authorize(String id,String ticket){if(!library.valid(id,ticket))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invalid media ticket");}
    @RequireLogin(false)
    @GetMapping("/stream/{id}") public ResponseEntity<Resource> stream(@PathVariable("id") String id,@RequestParam(name="ticket",required=false) String ticket) throws Exception {
        authorize(id,ticket);Path path=library.file(id);String type=Files.probeContentType(path);if(type==null)type="application/octet-stream";
        return ResponseEntity.ok().header(HttpHeaders.ACCEPT_RANGES,"bytes").header(HttpHeaders.CACHE_CONTROL,"private, max-age=0")
            .contentType(MediaType.parseMediaType(type)).contentLength(Files.size(path)).body(new FileSystemResource(path));
    }
    @RequireLogin(false)
    @GetMapping("/cover/{id}") public ResponseEntity<byte[]> cover(@PathVariable("id") String id,@RequestParam(name="ticket",required=false) String ticket) throws Exception {
        authorize(id,ticket);byte[] bytes=library.cover(id);if(bytes.length==0)return ResponseEntity.notFound().build();
        String type=bytes.length>8 && bytes[0]==(byte)0x89?"image/png":bytes.length>12 && bytes[0]=='R'?"image/webp":"image/jpeg";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header(HttpHeaders.CACHE_CONTROL,"private, max-age=300").body(bytes);
    }
}
