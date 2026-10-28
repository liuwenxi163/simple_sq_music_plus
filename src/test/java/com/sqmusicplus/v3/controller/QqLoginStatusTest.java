package com.sqmusicplus.v3.controller;

import com.sqmusicplus.v3.base.entity.SqConfig;
import com.sqmusicplus.v3.base.enums.SetConfigEnum;
import com.sqmusicplus.v3.config.*;
import com.sqmusicplus.v3.plug.qq.entity.QQMusicCookieInfo;
import com.sqmusicplus.v3.plug.qq.hander.QQHander;
import java.io.IOException;

/** Regression: a valid OAuth session without refresh_key must not be called a failed scan. */
public class QqLoginStatusTest {
    static final String SAVED="{\"musicid\":\"test\",\"musickey\":\"test\",\"refresh_token\":\"test\"}";
    static class Handler extends QQHander {
        boolean valid=true,offline;int checks,refreshes;
        @Override public Boolean checkLoginStatus() throws IOException {checks++;if(offline)throw new IOException("test offline");return valid;}
        @Override public QQMusicCookieInfo refreshToken(){refreshes++;throw new AssertionError("Status must never refresh a session");}
    }
    static void put(SetConfigEnum name,String value){SqConfig config=new SqConfig();config.setConfigKey(name.getKey());config.setConfigValue(value);SqConfigCache.sqConfigMap.put(name.getKey(),config);}
    static void assertCode(AjaxResult result,int expected){if(!Integer.valueOf(expected).equals(result.get("code")))throw new AssertionError("Unexpected status");}
    static PlugController controller(Handler handler) throws Exception {var c=new PlugController();var field=PlugController.class.getDeclaredField("qqHander");field.setAccessible(true);field.set(c,handler);return c;}
    @org.junit.jupiter.api.Test
    public void readOnlyStatus() throws Exception {main(new String[0]);}
    public static void main(String[] args) throws Exception {
        var previous=new java.util.HashMap<>(SqConfigCache.sqConfigMap);
        try {
            put(SetConfigEnum.PLUG_QQVIP_OPEN,"true");put(SetConfigEnum.PLUG_QQVIP_COOKIE,SAVED);
            Handler handler=new Handler();var c=controller(handler);
            assertCode(c.getQQvipcheckQrCodeStatus(),200);
            if(handler.checks!=1 || handler.refreshes!=0)throw new AssertionError("Scan check mutated credentials");
            handler.valid=false;assertCode(c.getQQvipcheckQrCodeStatus(),500);
            handler.offline=true;var unavailable=c.getQQvipcheckQrCodeStatus();assertCode(unavailable,500);
            if(!unavailable.get("msg").toString().contains("保留"))throw new AssertionError("Network error must preserve the session");
            if(!SAVED.equals(SqConfigCache.getSqConfigValue(SetConfigEnum.PLUG_QQVIP_COOKIE)))throw new AssertionError("Session changed");
            handler.offline=false;handler.valid=true;assertCode(c.refreshQQvipCookies(),200);
            if(handler.refreshes!=0)throw new AssertionError("Refresh API needs refresh_key");
            put(SetConfigEnum.PLUG_QQVIP_COOKIE,"");int checks=handler.checks;assertCode(c.getQQvipcheckQrCodeStatus(),500);
            if(checks!=handler.checks)throw new AssertionError("Missing session should not call Tencent");
            put(SetConfigEnum.PLUG_QQVIP_OPEN,"false");assertCode(c.getQQvipcheckQrCodeStatus(),500);
            put(SetConfigEnum.PLUG_QQVIP_COOKIE,SAVED);
            QQMusicCookieInfo existing=new QQHander().refreshToken();
            if(existing==null || !"test".equals(existing.getMusickey()))throw new AssertionError("Startup must preserve an OAuth session without refresh_key");
            System.out.println("QQ_STATUS_REGRESSION_TESTS passed=7");
        } finally {SqConfigCache.sqConfigMap.clear();SqConfigCache.sqConfigMap.putAll(previous);}
    }
}
