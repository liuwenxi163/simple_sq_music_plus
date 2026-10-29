package com.sqmusicplus.v3.members;
import java.util.Set;
public final class MemberAccess {
    public static boolean allowed(String method,String path){
        if(path.contains("..")||path.contains("%")||path.contains(";"))return false;
        if(path.equals("/api/config/logout")||path.equals("/api/config/isLogin")||path.equals("/api/config/getOption")||path.equals("/api/config/getPlugBrTypeList"))return true;
        if(path.equals("/api/members/me")||path.startsWith("/api/members/me/"))return true;
        if((path.equals("/api/listening/platforms")||path.startsWith("/api/listening/platforms/")))return true;
        if(path.equals("/api/listening/downloads")&&method.equals("GET"))return true;
        if(path.equals("/api/download/downloadSong")&&method.equals("POST"))return true;
        if(path.startsWith("/api/music/"))return true;
        if(path.equals("/api/library/state"))return Set.of("GET","PUT").contains(method);
        if(path.startsWith("/api/library/")){String part=path.substring(13);return Set.of("GET","POST").contains(method)&&(part.equals("tracks")||part.equals("resolve")||part.equals("scan")&&method.equals("GET")||part.startsWith("lyrics/")||part.startsWith("ticket/")||part.startsWith("stream/")||part.startsWith("cover/"));}
        return false;
    }
}
