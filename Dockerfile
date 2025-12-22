FROM eclipse-temurin:11-jdk-alpine AS builder

WORKDIR /app
COPY . .

# Install latest clojure tools.
RUN apk add --no-cache curl bash && \
    curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh && \
    chmod +x linux-install.sh && \
    ./linux-install.sh

# Build the uberjar.
RUN clojure -T:build uber

FROM eclipse-temurin:11-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/app.jar /app/app.jar

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
