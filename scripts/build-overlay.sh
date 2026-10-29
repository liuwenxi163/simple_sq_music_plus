#!/bin/sh
set -eu
cd /source
mkdir -p release/backend/classes release/test
find src/main/java/com/sqmusicplus/v3/library -name '*.java' > release/sources
find src/main/java/com/sqmusicplus/v3/members -name '*.java' >> release/sources
printf '%s\n' src/main/java/com/sqmusicplus/v3/config/AuthUtil.java src/main/java/com/sqmusicplus/v3/config/JwtUtil.java src/main/java/com/sqmusicplus/v3/config/JwtAuthenticationFilter.java src/main/java/com/sqmusicplus/v3/config/SqConfigCache.java src/main/java/com/sqmusicplus/v3/controller/ConfigController.java src/main/java/com/sqmusicplus/v3/controller/DownloadServiceController.java src/main/java/com/sqmusicplus/v3/base/service/impl/DownloadInfoServiceImpl.java src/main/java/com/sqmusicplus/v3/download/DownloadRetryService.java src/main/java/com/sqmusicplus/v3/plug/netease/hander/SQNeteaseCloudMusicInfo.java src/main/java/com/sqmusicplus/v3/plug/netease/hander/NeteaseHander.java >> release/sources
printf '%s\n' src/main/java/com/sqmusicplus/v3/plug/base/hander/SearchHanderAbstract.java src/main/java/com/sqmusicplus/v3/plug/qqvip/QQvipHander.java src/main/java/com/sqmusicplus/v3/controller/MusicController.java src/main/java/com/sqmusicplus/v3/controller/PlugController.java src/main/java/com/sqmusicplus/v3/utils/QqLoginTransport.java src/main/java/com/sqmusicplus/v3/plug/qq/hander/QQHander.java src/main/java/com/sqmusicplus/v3/plug/qq/hander/QQLoginHelp.java >> release/sources
javac -parameters -proc:none --release 21 -cp '/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' -d release/backend/classes @release/sources
sed '/@org.junit.jupiter.api.Test/,+1d' src/test/java/com/sqmusicplus/v3/utils/QqLoginTransportTest.java > release/test/QqLoginTransportTest.java
javac -parameters -proc:none --release 21 -cp 'release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' -d release/test release/test/QqLoginTransportTest.java
java -cp 'release/test:release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' com.sqmusicplus.v3.utils.QqLoginTransportTest
sed '/@org.junit.jupiter.api.Test/,+1d' src/test/java/com/sqmusicplus/v3/controller/QqLoginStatusTest.java > release/test/QqLoginStatusTest.java
javac -parameters -proc:none --release 21 -cp 'release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' -d release/test release/test/QqLoginStatusTest.java
java -cp 'release/test:release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' com.sqmusicplus.v3.controller.QqLoginStatusTest
sed '/@org.junit.jupiter.api.Test/,+1d' src/test/java/com/sqmusicplus/v3/controller/PlaybackQualityTest.java > release/test/PlaybackQualityTest.java
javac -parameters -proc:none --release 21 -cp 'release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' -d release/test release/test/PlaybackQualityTest.java
java -cp 'release/test:release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' com.sqmusicplus.v3.controller.PlaybackQualityTest
javac -parameters -proc:none --release 21 -cp 'release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' -d release/test src/test/java/com/sqmusicplus/v3/library/ListeningFeaturesTest.java
java -cp 'release/test:release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' com.sqmusicplus.v3.library.ListeningFeaturesTest
javac -parameters -proc:none --release 21 -cp 'release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' -d release/test src/test/java/com/sqmusicplus/v3/library/LibraryExperienceTest.java
java -cp 'release/test:release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' com.sqmusicplus.v3.library.LibraryExperienceTest
javac -parameters -proc:none --release 21 -cp 'release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' -d release/test src/test/java/com/sqmusicplus/v3/members/MemberFeaturesTest.java
java -cp 'release/test:release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' com.sqmusicplus.v3.members.MemberFeaturesTest
cp deploy/Backend.Dockerfile release/backend/Dockerfile
tar -czf release/backend.tar.gz -C release/backend .
