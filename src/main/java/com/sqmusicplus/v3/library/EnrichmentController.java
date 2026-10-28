package com.sqmusicplus.v3.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqmusicplus.v3.config.AjaxResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

/** Authenticated gateway. The worker credential never reaches the browser. */
@RestController
@RequestMapping("/api/enrichment")
public class EnrichmentController {
    private final ObjectMapper json;
    private final String base,key;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public EnrichmentController(ObjectMapper json,@Value("${LYRICFLOW_URL:http://lyricflow:8765}") String base,@Value("${LYRICFLOW_KEY:}") String key){this.json=json;this.base=base;this.key=key;}
    private AjaxResult call(String path,Map<String,Object> data) {
        if(key.isBlank())return AjaxResult.error("歌词整理服务尚未配置");
        try{
            var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(10)).header("X-Worker-Key",key);
            if(data==null)request.GET();else request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data)));
            var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)return AjaxResult.error(response.statusCode()==429?"整理队列已满，请等待现有任务完成":"整理请求未完成，请检查目录与服务状态");
            return AjaxResult.success(json.readValue(response.body(),Map.class));
        }catch(Exception e){return AjaxResult.error("歌词整理服务暂时不可用，请稍后重试");}
    }
    @GetMapping("/jobs") public AjaxResult jobs(){return call("/jobs",null);}
    @PostMapping("/jobs") public AjaxResult submit(@RequestBody Map<String,Object> body){return call("/jobs",body);}
    @PostMapping("/jobs/{id}/retry") public AjaxResult retry(@PathVariable("id") String id){if(!id.matches("[a-f0-9]{32}"))return AjaxResult.error("任务不存在");return call("/jobs/"+id+"/retry",Map.of());}
}
