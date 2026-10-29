"""Small source overlay, compiled and fault-tested offline before any NAS service change."""
from pathlib import Path
import shutil,tarfile
root=Path(__file__).resolve().parents[1];out=root/'release/backend-source';out.mkdir(parents=True,exist_ok=True)
sources=list((root/'src/main/java/com/sqmusicplus/v3/library').glob('*.java'))
sources += list((root/'src/main/java/com/sqmusicplus/v3/members').glob('*.java'))
sources += [root/'src/test/java/com/sqmusicplus/v3/members/MemberFeaturesTest.java']
sources += [root/'src/main/java/com/sqmusicplus/v3'/p for p in ['config/AuthUtil.java', 'config/JwtUtil.java', 'config/JwtAuthenticationFilter.java', 'config/SqConfigCache.java', 'controller/ConfigController.java', 'controller/DownloadServiceController.java', 'base/service/impl/DownloadInfoServiceImpl.java', 'download/DownloadRetryService.java', 'plug/netease/hander/SQNeteaseCloudMusicInfo.java','plug/netease/hander/NeteaseHander.java']]
sources += [root/p for p in ['src/main/java/com/sqmusicplus/v3/plug/qqvip/QQvipHander.java','src/main/java/com/sqmusicplus/v3/controller/MusicController.java','src/main/java/com/sqmusicplus/v3/controller/PlugController.java','src/main/java/com/sqmusicplus/v3/utils/QqLoginTransport.java','src/main/java/com/sqmusicplus/v3/plug/qq/hander/QQHander.java','src/main/java/com/sqmusicplus/v3/plug/qq/hander/QQLoginHelp.java','src/test/java/com/sqmusicplus/v3/utils/QqLoginTransportTest.java','src/test/java/com/sqmusicplus/v3/controller/QqLoginStatusTest.java','src/test/java/com/sqmusicplus/v3/controller/PlaybackQualityTest.java','src/main/java/com/sqmusicplus/v3/plug/base/hander/SearchHanderAbstract.java','src/test/java/com/sqmusicplus/v3/library/ListeningFeaturesTest.java','scripts/build-overlay.sh','deploy/Backend.Dockerfile','src/test/java/com/sqmusicplus/v3/library/LibraryExperienceTest.java']]
for path in sources:
    target=out/path.relative_to(root);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy(path,target)
    if path.suffix=='.sh':target.write_bytes(path.read_bytes().replace(b'\r\n',b'\n'))
shutil.copytree(root/'licenses',out/'licenses',dirs_exist_ok=True)
shutil.copy(root/'deploy/BackendSource.Dockerfile',out/'Dockerfile')
with tarfile.open(root/'release/backend.tar.gz','w:gz') as tar:
    for path in out.iterdir():tar.add(path,arcname=path.name)
