package com.sqmusicplus.v3.library;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sqmusicplus.v3.base.entity.DownloadInfo;
import com.sqmusicplus.v3.base.service.DownloadInfoService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import java.util.Date;

/** One backend owns this download database. Run before scheduled workers start. */
@Component
public class DownloadRecovery {
    private final DownloadInfoService downloads;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(DownloadRecovery.class);
    public DownloadRecovery(DownloadInfoService downloads){this.downloads=downloads;}
    @PostConstruct public void recover(){
        try{
            downloads.update(new LambdaUpdateWrapper<DownloadInfo>().eq(DownloadInfo::getDownloadStatus,"loading")
                .set(DownloadInfo::getDownloadStatus,"waiting").set(DownloadInfo::getDownloadUpdateTime,new Date()));
        }catch(org.springframework.dao.DataAccessException error){
            // A first installation can create its tables later during initialization.
            LOG.warn("DOWNLOAD_RECOVERY_DEFERRED error_type={}",error.getClass().getSimpleName());
        }
    }
}
