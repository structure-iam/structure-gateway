FROM eclipse-temurin:8-jdk-alpine

RUN apk update && apk add --no-cache curl tzdata && rm -rf /var/cache/apk/*

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""
ENV PARAMS=""
ENV APP_PATH=/app/boot/app.jar

RUN mkdir -p /app/logs /app/boot

ADD ./target/gateway.jar /app/boot/app.jar
ADD ./liveness.sh /app/liveness.sh

RUN chmod +x /app/liveness.sh

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD /app/liveness.sh || exit 1

EXPOSE 8080

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar $APP_PATH $PARAMS"]
