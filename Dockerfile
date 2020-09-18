FROM openjdk:11-jdk-slim AS build-env

ADD . /code
WORKDIR /code
RUN ./gradlew
RUN ./gradlew shadowJar && mv build/libs/2020-classes-bot-*-all.jar /code/bot.jar

FROM openjdk:11-jdk-slim
COPY --from=build-env /code/bot.jar /bot/bot.jar
ADD ./nate.mp3 /bot/nate.mp3
WORKDIR /bot
ENTRYPOINT ["java","-jar","bot.jar"]