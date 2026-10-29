FROM registry.cn-hangzhou.aliyuncs.com/sqdockler/simple_sq_music_plus@sha256:4f3e499c07da526bea5648fc4e1b9c449e363356c5e8c1ef4d96c2d67b1e20f2
COPY . /source/
RUN cd /source && JAVA_TOOL_OPTIONS='-Xms16m -Xmx256m' sh scripts/build-overlay.sh && cp -r release/backend/classes/. /app/BOOT-INF/classes/ && mkdir -p /app/licenses && cp licenses/* /app/licenses/ && rm -rf /source
WORKDIR /app
