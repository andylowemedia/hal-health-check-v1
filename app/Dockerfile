FROM alpine:3.22 AS base
COPY . /app
WORKDIR /app
RUN apk add openjdk21-jdk tzdata && \
    cp /usr/share/zoneinfo/Europe/London /etc/localtime && \
    echo "Europe/London" >  /etc/timezone && \
    apk del tzdata

FROM base AS development
ENTRYPOINT [ "tail", "-f", "/dev/null" ]

FROM base AS production-build
RUN cd /app && \
    ./gradlew build -x test

FROM alpine:3.22 AS production
WORKDIR /app
COPY --from=production-build /app/app/build/distributions/app.zip /
RUN apk add openjdk21-jre tzdata && cp /usr/share/zoneinfo/Europe/London /etc/localtime && \
    echo "Europe/London" >  /etc/timezone && \
    apk del tzdata && \
    unzip /app.zip -d / && rm /app.zip

ENTRYPOINT [ "tail", "-f", "/dev/null" ]
