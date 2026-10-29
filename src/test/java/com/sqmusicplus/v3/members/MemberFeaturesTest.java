package com.sqmusicplus.v3.members;
import com.alibaba.fastjson2.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqmusicplus.v3.base.entity.*;
import com.sqmusicplus.v3.base.enums.SetConfigEnum;
import com.sqmusicplus.v3.config.*;
import com.sqmusicplus.v3.library.LibraryService;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
public class MemberFeaturesTest {
 static int checks;static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 interface Attempt{void run()throws Exception;}
 static void reject(Attempt a,String m)throws Exception{boolean failed=false;try{a.run();}catch(Exception e){failed=true;}check(failed,m);}
 static void field(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 static int request(JwtAuthenticationFilter f,String token,String path,int expectedMember)throws Exception{
  SecurityContextHolder.clearContext();int[] status={200};Map<String,Object> attrs=new HashMap<>();
  HttpServletRequest req=(HttpServletRequest)Proxy.newProxyInstance(MemberFeaturesTest.class.getClassLoader(),new Class[]{HttpServletRequest.class},(o,m,a)->switch(m.getName()){case "getRequestURI","getServletPath"->path;case "getMethod"->"GET";case "getHeader"->"sqmusic".equals(a[0])?token:null;case "getAttribute"->attrs.get(a[0]);case "setAttribute"->{attrs.put((String)a[0],a[1]);yield null;}case "removeAttribute"->{attrs.remove(a[0]);yield null;}default->zero(m.getReturnType());});
  HttpServletResponse res=(HttpServletResponse)Proxy.newProxyInstance(MemberFeaturesTest.class.getClassLoader(),new Class[]{HttpServletResponse.class},(o,m,a)->{if(m.getName().equals("setStatus"))status[0]=(int)a[0];if(m.getName().equals("getWriter"))return new java.io.PrintWriter(new java.io.StringWriter());return zero(m.getReturnType());});
  Method apply=JwtAuthenticationFilter.class.getDeclaredMethod("doFilterInternal",HttpServletRequest.class,HttpServletResponse.class,FilterChain.class);apply.setAccessible(true);apply.invoke(f,req,res,(FilterChain)(q,s)->check(MemberScope.id()==expectedMember,"Filter enters authenticated identity"));
  check(MemberScope.id()==9527,"Request identity always released");SecurityContextHolder.clearContext();return status[0];
 }
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("nook-members-test");var original=new HashMap<>(SqConfigCache.sqConfigMap);
  try{
   for(var pair:List.of(new String[]{"system.login.account","admin"},new String[]{"system.login.password","admin-fixture-secret"},new String[]{"plug.qqvip.cookie","admin-cookie"})){SqConfig c=new SqConfig();c.setConfigKey(pair[0]);c.setConfigValue(pair[1]);SqConfigCache.sqConfigMap.put(pair[0],c);}
   MemberService m=new MemberService(dir.toString());check(m.profile(9527).getString("role").equals("ADMIN"),"Default admin highest role");check(m.authenticate("admin","admin-fixture-secret")==9527,"Legacy admin works");reject(()->m.update(9527,null,true),"Admin cannot be disabled");
   int a=m.create("alice","Alice","alice-fixture-secret").getIntValue("id"),b=m.create("bob","Bob","bob-fixture-secret").getIntValue("id");check(a!=b&&a>=10000,"Stable unique IDs");check(m.authenticate("ALICE","alice-fixture-secret")==a,"Member BCrypt login");check(m.authenticate("alice","wrong")==null,"Bad password fails");reject(()->m.create("Alice",null,"duplicate-secret"),"Case-insensitive unique names");reject(()->m.create("../x",null,"bad-name-secret"),"No path username");
   String c1="{\"musicid\":\"11\",\"musickey\":\"alice-secret\"}",c2="bob-platform-secret";m.credential(a,"qqvip",c1);m.credential(b,"netease",c2);
   String stored=Files.readString(dir.resolve("members.json"));check(!stored.contains(c1)&&!stored.contains("alice-secret")&&!stored.contains(c2)&&!stored.contains("alice-fixture-secret"),"Passwords hashed, platform credentials encrypted");check(!m.list().toJSONString().contains("passwordHash")&&!m.list().toJSONString().contains("credentials"),"Profiles expose no secrets");
   try(var scope=MemberScope.enter(a)){check(SqConfigCache.getSqConfigValue(SetConfigEnum.PLUG_QQVIP_COOKIE).equals(c1),"Member cookie scoped");check(SqConfigCache.getSqConfigValue(SetConfigEnum.PLUG_NETEASE_COOKIE).isEmpty(),"Unbound account never borrows admin");try(var nested=MemberScope.enter(b)){check(SqConfigCache.getSqConfigValue(SetConfigEnum.PLUG_NETEASE_COOKIE).equals(c2),"Nested identity own credentials");}check(MemberScope.id()==a,"Nested identity restored");reject(()->SqConfigCache.updateConfigToDb(SetConfigEnum.SYSTEM_LOGIN_ACCOUNT,"changed"),"Member cannot change system settings");}
   check(SqConfigCache.getSqConfigValue(SetConfigEnum.PLUG_QQVIP_COOKIE).equals("admin-cookie"),"Global admin config preserved");
   LibraryService library=new LibraryService(new ObjectMapper(),dir.toString(),"test-fixture-key-at-least-32-characters");library.savePreferences(Map.of("favorites",List.of("admin-song")));String legacy=Files.readString(dir.resolve("listening.json"));
   try(var scope=MemberScope.enter(a)){library.savePreferences(Map.of("favorites",List.of("alice-song"),"localFirst",false));}
   try(var scope=MemberScope.enter(b)){check(((List<?>)library.preferences().get("favorites")).isEmpty(),"New member has no other favorites");library.savePreferences(Map.of("favorites",List.of("bob-song")));}
   check(Files.readString(dir.resolve("listening.json")).equals(legacy),"Admin listening.json retained byte for byte");
   MemberService loadedMembers=new MemberService(dir.toString());check(loadedMembers.credential(a,"qqvip").equals(c1),"Encrypted credentials survive reload");MemberService members=m;MemberScope.register(m);
   LibraryService reloaded=new LibraryService(new ObjectMapper(),dir.toString(),"test-fixture-key-at-least-32-characters");try(var scope=MemberScope.enter(a)){check(reloaded.preferences().get("favorites").equals(List.of("alice-song")),"Private preferences survive restart");check(reloaded.preferences().get("localFirst").equals(false),"Private playback policy persists");}
   DownloadInfo task=new DownloadInfo().setDownloadMusicInfo("{}");try(var scope=MemberScope.enter(a)){MemberScope.mark(task);}check(MemberScope.owner(task)==a,"Persisted job ownership");try(var scope=MemberScope.job(task)){check(SqConfigCache.getSqConfigValue(SetConfigEnum.PLUG_QQVIP_COOKIE).equals(c1),"Background job resumes submitting credentials");}check(MemberScope.owner(new DownloadInfo().setDownloadMusicInfo("{}"))==9527,"Legacy tasks stay admin");
   var pool=java.util.concurrent.Executors.newFixedThreadPool(2);try{var f1=pool.submit(()->{try(var scope=MemberScope.enter(a)){return members.credential(MemberScope.id(),"qqvip");}});var f2=pool.submit(()->{try(var scope=MemberScope.enter(b)){return members.credential(MemberScope.id(),"netease");}});check(f1.get().equals(c1)&&f2.get().equals(c2),"Parallel request identities isolated");}finally{pool.shutdownNow();}
   for(String path:List.of("/api/members","/api/members/9527/password","/api/config/getConfigList","/api/plug/QQLoginQr","/api/task/refreshTask","/mcp","/api/members/me/../9527","/api/members/me%2f.."))check(!MemberAccess.allowed("GET",path),"Admin route denied "+path);
   check(MemberAccess.allowed("GET","/api/members/me/platforms")&&MemberAccess.allowed("PUT","/api/library/state"),"Private routes allowed");check(!MemberAccess.allowed("POST","/api/library/scan"),"Shared scanner admin-only");
   JwtUtil jwt=new JwtUtil();field(jwt,"members",m);field(jwt,"secret","test-fixture-key-at-least-32-characters");field(jwt,"expiration",3600000L);String token=jwt.generateToken(a,"alice");JwtAuthenticationFilter filter=new JwtAuthenticationFilter();field(filter,"members",m);field(filter,"jwtUtil",jwt);field(filter,"loginCheckInterceptor",new LoginCheckInterceptor(){public boolean checkLogin(HttpServletRequest r){return true;}});
   check(request(filter,token,"/api/members/me",a)==200,"Real JWT private request");check(request(filter,token,"/api/config/getConfigList",a)==403,"Real JWT rejects global configuration");check(request(filter,token,"/mcp",a)==403,"Member cannot invoke MCP admin tools");check(request(filter,null,"/api/library/state",9527)==401,"Anonymous private request denied");
   int old=m.version(a);m.resetPassword(a,"replacement-fixture-secret");check(!m.session(a,"alice",old)&&m.authenticate("alice","replacement-fixture-secret")==a,"Password change revokes sessions");check(request(filter,token,"/api/members/me",a)==401,"Old JWT rejected after reset");m.update(a,null,true);check(!m.active(a)&&m.authenticate("alice","replacement-fixture-secret")==null,"Disabled member cannot log in");reject(()->MemberScope.job(task),"Disabled member task cannot borrow another identity");
   System.out.println("MEMBER_FEATURES_TESTS_PASSED="+checks);
  }finally{SqConfigCache.sqConfigMap.clear();SqConfigCache.sqConfigMap.putAll(original);try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
 }
}
