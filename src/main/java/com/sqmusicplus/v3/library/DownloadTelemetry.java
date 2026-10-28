package com.sqmusicplus.v3.library;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Live byte counters only. Task status and completed history remain in MySQL. */
public final class DownloadTelemetry {
    private record Sample(long bytes,long total,long started,long updated){}
    private static final Map<Integer,Sample> samples=new ConcurrentHashMap<>();
    public static void update(Integer id,long bytes,long total){
        if(id==null)return;long now=System.currentTimeMillis();
        if(samples.size()>512)samples.entrySet().removeIf(e->now-e.getValue().updated()>1800000);
        samples.compute(id,(key,old)->new Sample(bytes,total,old==null||bytes<old.bytes()?now:old.started(),now));
    }
    public static Map<String,Object> snapshot(Integer id,String status){
        Sample s=samples.get(id);boolean done=Set.of("success","supplement_success").contains(status);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("bytes",s==null?0:s.bytes());result.put("total",s==null?0:s.total());
        Integer percent=null;if(done)percent=100;else if(s!=null&&s.total()>0)percent=Math.min(99,Math.round(100f*s.bytes()/s.total()));result.put("percent",percent);
        result.put("bytesPerSecond",s==null||done?0:Math.round(s.bytes()*1000d/Math.max(1000,s.updated()-s.started())));
        result.put("stage",done?"completed":s==null?"preparing":s.total()>0&&s.bytes()>=s.total()?"tagging":"transferring");
        return result;
    }
}
