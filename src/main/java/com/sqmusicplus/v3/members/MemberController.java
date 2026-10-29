package com.sqmusicplus.v3.members;
import com.alibaba.fastjson2.JSONObject;
import com.sqmusicplus.v3.config.*;
import com.sqmusicplus.v3.base.entity.DownloadInfo;
import com.sqmusicplus.v3.base.service.DownloadInfoService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController @RequireLogin @RequestMapping("/api/members")
public class MemberController {
    private final MemberService members;private final DownloadInfoService tasks;
    public MemberController(MemberService members,DownloadInfoService tasks){this.members=members;this.tasks=tasks;}
    private void admin(){if(!MemberScope.admin())throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
    @GetMapping("/me") public AjaxResult me(){return AjaxResult.success(members.profile(MemberScope.id()));}
    @GetMapping public AjaxResult list(){admin();return AjaxResult.success(members.list());}
    @PostMapping public AjaxResult create(@RequestBody JSONObject body){admin();return AjaxResult.success(members.create(body.getString("username"),body.getString("displayName"),body.getString("password")));}
    @PatchMapping("/{id}") public AjaxResult update(@PathVariable("id") int id,@RequestBody JSONObject body){admin();return AjaxResult.success(members.update(id,body.getString("displayName"),body.containsKey("disabled")?body.getBoolean("disabled"):null));}
    @PutMapping("/{id}/password") public AjaxResult reset(@PathVariable("id") int id,@RequestBody JSONObject body){admin();members.resetPassword(id,body.getString("password"));return AjaxResult.success();}
    @PatchMapping("/me") public AjaxResult profile(@RequestBody JSONObject body){return AjaxResult.success(members.update(MemberScope.id(),body.getString("displayName"),null));}
    @PutMapping("/me/password") public AjaxResult password(@RequestBody JSONObject body){JSONObject p=members.profile(MemberScope.id());Integer id=members.authenticate(p.getString("username"),body.getString("currentPassword"));if(id==null||id!=MemberScope.id())throw new IllegalArgumentException("当前密码不正确");members.resetPassword(id,body.getString("password"));return AjaxResult.success();}
    @PostMapping("/me/downloads/{id}/retry") public AjaxResult retry(@PathVariable("id") Integer id){DownloadInfo task=tasks.getById(id);if(task==null||!MemberScope.admin()&&MemberScope.owner(task)!=MemberScope.id())throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(!"error".equals(task.getDownloadStatus()))throw new IllegalArgumentException("只能重试失败的任务");tasks.updateById(new DownloadInfo().setId(id).setDownloadStatus("waiting"));return AjaxResult.success();}
}
