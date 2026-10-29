package com.sqmusicplus.v3.library;
import com.sqmusicplus.v3.config.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequireLogin @RequestMapping("/api/listening/platforms")
public class PlatformMusicController {
    private final PlatformMusicService service;
    public PlatformMusicController(PlatformMusicService service){this.service=service;}
    @GetMapping public AjaxResult accounts(){return AjaxResult.success(service.accounts());}
    @GetMapping("/{provider}/daily") public AjaxResult daily(@PathVariable("provider") String provider){try{return AjaxResult.success(service.daily(provider));}catch(Exception e){return AjaxResult.error(PlatformMusicService.safeError(e));}}
    @GetMapping("/{provider}/library") public AjaxResult library(@PathVariable("provider") String provider){try{return AjaxResult.success(service.library(provider));}catch(Exception e){return AjaxResult.error(PlatformMusicService.safeError(e));}}
    @PostMapping("/{provider}/like") public AjaxResult like(@PathVariable("provider") String provider,@RequestBody Map<String,Object> body){try{if(!(body.get("like") instanceof Boolean))throw new IllegalArgumentException("Invalid like");return AjaxResult.success(service.change(provider,Objects.toString(body.get("id"),""),null,(Boolean)body.get("like")));}catch(Exception e){return AjaxResult.error(PlatformMusicService.safeError(e));}}
    @PostMapping("/{provider}/playlists/{id}/tracks") public AjaxResult add(@PathVariable("provider") String provider,@PathVariable("id") String id,@RequestBody Map<String,String> body){try{return AjaxResult.success(service.change(provider,body.get("id"),id,null));}catch(Exception e){return AjaxResult.error(PlatformMusicService.safeError(e));}}
}
