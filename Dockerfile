# 서버에서 쓰던 이미지 정의와 같다. 먼저 ./gradlew build 로 jar 를 만든 뒤 빌드한다.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/hongmap-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
