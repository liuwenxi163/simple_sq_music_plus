package com.sqmusicplus.v3.members;

import com.alibaba.fastjson2.JSONObject;
import com.sqmusicplus.v3.base.entity.DownloadInfo;
import com.sqmusicplus.v3.base.enums.SetConfigEnum;

/** Request/job-local identity. Never changes shared platform configuration. */
public final class MemberScope implements AutoCloseable {
    public static final int ADMIN=9527;
    private static final ThreadLocal<Integer> CURRENT=new ThreadLocal<>();
    private static volatile MemberService service;
    private final Integer previous;
    static void register(MemberService value){service=value;}
    public static int id(){Integer value=CURRENT.get();return value==null?ADMIN:value;}
    public static boolean admin(){return id()==ADMIN;}
    public static MemberScope enter(int id){if(service==null&&id!=ADMIN)throw new IllegalStateException("MEMBER_STORE_UNAVAILABLE");if(service!=null&&!service.active(id))throw new IllegalStateException("MEMBER_DISABLED");return new MemberScope(id);}
    private MemberScope(int id){previous=CURRENT.get();CURRENT.set(id);}
    @Override public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    public static String credential(SetConfigEnum key){if(admin())return null;String provider=provider(key);return provider==null?null:service.credential(id(),provider);}
    public static boolean save(SetConfigEnum key,String value){if(admin())return false;String provider=provider(key);if(provider==null)throw new IllegalStateException("ADMIN_REQUIRED");service.credential(id(),provider,value);return true;}
    private static String provider(SetConfigEnum key){return key==SetConfigEnum.PLUG_QQVIP_COOKIE?"qqvip":key==SetConfigEnum.PLUG_NETEASE_COOKIE?"netease":null;}
    public static void requireProvider(String provider){if(!admin()&&!java.util.Set.of("qq","qqvip","netease").contains(provider))throw new IllegalArgumentException("该成员暂不支持此平台");}
    public static void mark(DownloadInfo task){if(admin())return;JSONObject data=JSONObject.parseObject(task.getDownloadMusicInfo());if(data==null)throw new IllegalArgumentException("Invalid task metadata");data.put("__nookMember",id());task.setDownloadMusicInfo(data.toJSONString());}
    public static int owner(DownloadInfo task){try{JSONObject data=JSONObject.parseObject(task.getDownloadMusicInfo());return data!=null&&data.containsKey("__nookMember")?data.getIntValue("__nookMember"):ADMIN;}catch(Exception e){return ADMIN;}}
    public static MemberScope job(DownloadInfo task){return enter(owner(task));}
}
