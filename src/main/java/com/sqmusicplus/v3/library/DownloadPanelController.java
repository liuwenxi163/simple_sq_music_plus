package com.sqmusicplus.v3.library;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqmusicplus.v3.base.entity.DownloadInfo;
import com.sqmusicplus.v3.base.service.DownloadInfoService;
import com.sqmusicplus.v3.config.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequireLogin @RequestMapping("/api/listening/downloads")
public class DownloadPanelController {
    private final DownloadInfoService tasks;private final ObjectMapper json;
    public DownloadPanelController(DownloadInfoService tasks,ObjectMapper json){this.tasks=tasks;this.json=json;}
    @GetMapping public AjaxResult list(@RequestParam(name="tab",defaultValue="active") String tab,@RequestParam(name="page",defaultValue="1") int page){
        var query=new LambdaQueryWrapper<DownloadInfo>().isNotNull(DownloadInfo::getDownloadPlugName).ne(DownloadInfo::getDownloadMusicId,"0");
        if(!com.sqmusicplus.v3.members.MemberScope.admin())query.apply("JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(download_music_info),download_music_info,'{}'), '$.__nookMember')) = {0}",com.sqmusicplus.v3.members.MemberScope.id());
        switch(tab){
            case "active" -> query.in(DownloadInfo::getDownloadStatus,List.of("waiting","loading","retry","supplement"));
            case "completed" -> query.in(DownloadInfo::getDownloadStatus,List.of("success","supplement_success"));
            case "failed" -> query.eq(DownloadInfo::getDownloadStatus,"error");
            case "all" -> {} default -> throw new IllegalArgumentException("Invalid tab");
        }
        // Keep the transfers visible even when a large sync just appended waiting rows.
        if("active".equals(tab))query.last("ORDER BY CASE WHEN download_status IN ('loading','supplement') THEN 0 WHEN download_status='retry' THEN 1 ELSE 2 END, download_update_time ASC");
        else query.orderByDesc(DownloadInfo::getDownloadUpdateTime);
        var result=tasks.page(new Page<>(Math.max(1,page),50),query);var items=new ArrayList<Map<String,Object>>();
        for(var task:result.getRecords()){
            Map<String,Object> item=json.convertValue(task,new TypeReference<Map<String,Object>>(){});
            item.remove("downloadMusicInfo");item.put("progress",DownloadTelemetry.snapshot(task.getId(),task.getDownloadStatus()));items.add(item);
        }
        return AjaxResult.success(Map.of("items",items,"total",result.getTotal(),"page",Math.max(1,page),"pageSize",50));
    }
}
