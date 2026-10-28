package com.sqmusicplus.v3.library;
import com.sqmusicplus.v3.config.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequireLogin @RequestMapping("/api/listening/collection")
public class AccountCollectionController {
    private final AccountCollectionService collection;
    public AccountCollectionController(AccountCollectionService collection){this.collection=collection;}
    @GetMapping public AjaxResult status(){return AjaxResult.success(collection.status());}
    @GetMapping("/status") public AjaxResult summary(){return AjaxResult.success(collection.summary());}
    @PutMapping public AjaxResult settings(@RequestBody Map<String,Boolean> body)throws Exception{return AjaxResult.success(collection.configure(Boolean.TRUE.equals(body.get("enabled")),Boolean.TRUE.equals(body.get("autoDownload"))));}
    @PostMapping("/sync") public AjaxResult sync(){return AjaxResult.success(collection.start());}
}
