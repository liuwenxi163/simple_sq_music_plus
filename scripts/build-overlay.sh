#!/bin/sh
set -eu
cd /source
mkdir -p release/backend/classes release/test
find src/main/java/com/sqmusicplus/v3/library -name '*.java' > release/sources
printf '%s\n' src/main/java/com/sqmusicplus/v3/controller/PlugController.java src/main/java/com/sqmusicplus/v3/utils/QqLoginTransport.java src/main/java/com/sqmusicplus/v3/plug/qq/hander/QQHander.java src/main/java/com/sqmusicplus/v3/plug/qq/hander/QQLoginHelp.java >> release/sources
javac -parameters -proc:none --release 21 -cp '/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' -d release/backend/classes @release/sources
sed '/@org.junit.jupiter.api.Test/,+1d' src/test/java/com/sqmusicplus/v3/utils/QqLoginTransportTest.java > release/test/QqLoginTransportTest.java
javac -parameters -proc:none --release 21 -cp 'release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' -d release/test release/test/QqLoginTransportTest.java
java -cp 'release/test:release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' com.sqmusicplus.v3.utils.QqLoginTransportTest
sed '/@org.junit.jupiter.api.Test/,+1d' src/test/java/com/sqmusicplus/v3/controller/QqLoginStatusTest.java > release/test/QqLoginStatusTest.java
javac -parameters -proc:none --release 21 -cp 'release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' -d release/test release/test/QqLoginStatusTest.java
java -cp 'release/test:release/backend/classes:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*' com.sqmusicplus.v3.controller.QqLoginStatusTest
cp deploy/Backend.Dockerfile release/backend/Dockerfile
tar -czf release/backend.tar.gz -C release/backend .
