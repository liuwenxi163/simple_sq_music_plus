package com.sqmusicplus.v3.members;

import com.alibaba.fastjson2.*;
import com.sqmusicplus.v3.base.enums.SetConfigEnum;
import com.sqmusicplus.v3.config.SqConfigCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class MemberService {
    private final Path root,file;private final byte[] key;private final JSONObject state;private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();private final SecureRandom random=new SecureRandom();
    public MemberService(@Value("${SQ_DATA_DIR:/data/sqmusic}") String dir)throws Exception{
        root=Path.of(dir).toAbsolutePath().normalize();Files.createDirectories(root);file=root.resolve("members.json");Path keyFile=root.resolve("members.key");
        if(!Files.exists(keyFile)){if(Files.exists(file))throw new IllegalStateException("Missing member encryption key");byte[] fresh=new byte[32];random.nextBytes(fresh);Files.write(keyFile,fresh,StandardOpenOption.CREATE_NEW);privateFile(keyFile);}
        key=Files.readAllBytes(keyFile);if(key.length!=32)throw new IllegalStateException("Invalid member encryption key");
        state=Files.exists(file)?JSONObject.parseObject(Files.readString(file)):JSONObject.of("nextId",10000,"members",new JSONArray());if(state==null||state.getJSONArray("members")==null)throw new IllegalStateException("Invalid member data");MemberScope.register(this);
    }
    private static void privateFile(Path p)throws Exception{try{Files.setPosixFilePermissions(p,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}}
    private synchronized void persist(){try{Path tmp=file.resolveSibling("members.json.tmp");Files.writeString(tmp,state.toJSONString());privateFile(tmp);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}catch(Exception e){throw new IllegalStateException("无法保存成员数据",e);}}
    public String adminName(){String value=SqConfigCache.getSqConfigValue(SetConfigEnum.SYSTEM_LOGIN_ACCOUNT);return value==null||value.isBlank()?"admin":value;}
    private JSONObject row(int id){for(Object o:state.getJSONArray("members")){JSONObject r=(JSONObject)o;if(r.getIntValue("id")==id)return r;}throw new IllegalArgumentException("成员不存在");}
    public synchronized boolean active(int id){if(id==MemberScope.ADMIN)return true;try{return !row(id).getBooleanValue("disabled")&&!row(id).getBooleanValue("deleted");}catch(Exception e){return false;}}
    public synchronized int version(int id){return id==MemberScope.ADMIN?0:row(id).getIntValue("version");}
    public synchronized boolean session(int id,String name,Integer version){return active(id)&&(id==MemberScope.ADMIN?adminName().equals(name):row(id).getString("username").equals(name)&&version!=null&&version==version(id));}
    public synchronized JSONObject profile(int id){if(id==MemberScope.ADMIN)return JSONObject.of("id",id,"username",adminName(),"displayName",state.getOrDefault("adminDisplayName",adminName()),"role","ADMIN","disabled",false);JSONObject r=row(id);return JSONObject.of("id",id,"username",r.getString("username"),"displayName",r.getString("displayName"),"role","MEMBER","disabled",r.getBooleanValue("disabled"),"createdAt",r.getString("createdAt"));}
    public synchronized JSONArray list(){JSONArray out=JSONArray.of(profile(MemberScope.ADMIN));for(Object o:state.getJSONArray("members")){JSONObject r=(JSONObject)o;if(!r.getBooleanValue("deleted"))out.add(profile(r.getIntValue("id")));}return out;}
    private static String name(String value){if(value==null||!value.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{2,47}"))throw new IllegalArgumentException("账号需为 3—48 位字母、数字或 ._- ");return value.toLowerCase(Locale.ROOT);}
    private static void password(String value){if(value==null||value.length()<8||value.getBytes(StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("密码至少 8 位，UTF-8 编码不超过 72 字节");}
    private static String display(String value,String fallback){String v=value==null?fallback:value.trim();if(v.isEmpty()||v.length()>40)throw new IllegalArgumentException("昵称需为 1—40 字");return v;}
    public synchronized JSONObject create(String username,String displayName,String password){String n=name(username);password(password);if(n.equalsIgnoreCase(adminName()))throw new IllegalArgumentException("账号已存在");for(Object o:state.getJSONArray("members"))if(n.equalsIgnoreCase(((JSONObject)o).getString("username")))throw new IllegalArgumentException("账号已存在");int id=state.getIntValue("nextId");JSONObject r=JSONObject.of("id",id,"username",n,"displayName",display(displayName,n),"passwordHash",passwords.encode(password),"version",0,"disabled",false,"createdAt",Instant.now().toString(),"credentials",new JSONObject());state.getJSONArray("members").add(r);state.put("nextId",id+1);persist();return profile(id);}
    public synchronized Integer authenticate(String username,String password){if(username==null||password==null)return null;if(adminName().equals(username)){String saved=SqConfigCache.getSqConfigValue(SetConfigEnum.SYSTEM_LOGIN_PASSWORD);if(saved!=null&&MessageDigest.isEqual(saved.getBytes(StandardCharsets.UTF_8),password.getBytes(StandardCharsets.UTF_8)))return MemberScope.ADMIN;return null;}for(Object o:state.getJSONArray("members")){JSONObject r=(JSONObject)o;if(r.getString("username").equalsIgnoreCase(username)&&active(r.getIntValue("id"))&&passwords.matches(password,r.getString("passwordHash")))return r.getIntValue("id");}return null;}
    public synchronized JSONObject update(int id,String displayName,Boolean disabled){if(id==MemberScope.ADMIN){if(Boolean.TRUE.equals(disabled))throw new IllegalArgumentException("不能停用默认管理员");if(displayName!=null)state.put("adminDisplayName",display(displayName,adminName()));}else{JSONObject r=row(id);if(displayName!=null)r.put("displayName",display(displayName,r.getString("username")));if(disabled!=null){r.put("disabled",disabled);r.put("version",r.getIntValue("version")+1);}}persist();return profile(id);}
    public synchronized void resetPassword(int id,String value){if(id==MemberScope.ADMIN)throw new IllegalArgumentException("请在系统设置修改管理员密码");password(value);JSONObject r=row(id);r.put("passwordHash",passwords.encode(value));r.put("version",r.getIntValue("version")+1);persist();}
    private String encrypt(String plain)throws Exception{byte[] iv=new byte[12];random.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));byte[] body=c.doFinal(plain.getBytes(StandardCharsets.UTF_8)),all=new byte[iv.length+body.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(body,0,all,iv.length,body.length);return Base64.getEncoder().encodeToString(all);}
    private String decrypt(String text)throws Exception{byte[] all=Base64.getDecoder().decode(text);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,all,0,12));return new String(c.doFinal(all,12,all.length-12),StandardCharsets.UTF_8);}
    static void provider(String p){if(!Set.of("qqvip","netease").contains(p))throw new IllegalArgumentException("平台不支持");}
    public synchronized String credential(int id,String p){provider(p);if(id==MemberScope.ADMIN)return SqConfigCache.getSqConfigValue(p.equals("qqvip")?SetConfigEnum.PLUG_QQVIP_COOKIE:SetConfigEnum.PLUG_NETEASE_COOKIE);if(!active(id))throw new IllegalStateException("MEMBER_DISABLED");String value=row(id).getJSONObject("credentials").getString(p);try{return value==null?"":decrypt(value);}catch(Exception e){throw new IllegalStateException("PLATFORM_CREDENTIAL_UNAVAILABLE");}}
    public synchronized void credential(int id,String p,String value){provider(p);if(value==null||value.length()>65536)throw new IllegalArgumentException("凭据过长");if(id==MemberScope.ADMIN){SqConfigCache.updateConfigToDb(p.equals("qqvip")?SetConfigEnum.PLUG_QQVIP_COOKIE:SetConfigEnum.PLUG_NETEASE_COOKIE,value);return;}if(!active(id))throw new IllegalStateException("MEMBER_DISABLED");try{row(id).getJSONObject("credentials").put(p,encrypt(value));persist();}catch(Exception e){throw new IllegalStateException("无法保存平台登录");}}
    public Path directory(int id){if(id==MemberScope.ADMIN)return root;return root.resolve("members").resolve(Integer.toString(id));}
}
