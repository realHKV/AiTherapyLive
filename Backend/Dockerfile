FROM --platform=linux/amd64 maven:3.9.14-eclipse-temurin-21@sha256:5c73793a3919815ff0e3921c53f8070d65a35090c0472763fa40ac22c0cfa0f1 AS build
WORKDIR /src
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency

FROM --platform=linux/amd64 eclipse-temurin:21-jre-noble@sha256:da0f0d242bec39e3fe5c1ac4c415840f03be9cef848c243d723166708e02148c
WORKDIR /app
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app
COPY --from=build /src/target/AiTherapy-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --from=build /src/target/AiTherapy-0.0.1-SNAPSHOT-migrator.jar /app/migrator.jar
COPY --from=build /src/target/dependency/ /app/lib/
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod 0555 /app/entrypoint.sh && chmod -R a+rX /app && test -f /app/migrator.jar
USER 10001:10001
EXPOSE 8080
STOPSIGNAL SIGTERM
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["serve"]
