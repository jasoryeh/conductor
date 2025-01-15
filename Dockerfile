FROM azul/zulu-openjdk-alpine:8-latest AS builder

RUN apk add git maven

COPY ./ /conductor

RUN cd /conductor && ls -lah && mvn clean install && cp target/**-jar-with-dependencies.jar target/conductor.jar

FROM azul/zulu-openjdk-alpine:8-latest
COPY --from=builder /conductor/target/conductor.jar /conductor.jar
