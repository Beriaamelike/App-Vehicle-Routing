# Java tabanlı bir image kullan
FROM openjdk:17-jdk-alpine

# Çalışma dizinini belirle
WORKDIR /app

# Uygulamanın jar dosyasını container'a ekle
ADD target/spring-0.0.1-SNAPSHOT.jar app.jar

# Uygulamanın çalıştırılması
ENTRYPOINT ["java", "-jar", "app.jar"]

# Container'in dinleyeceği portu belirt (Eğer Spring Boot uygulamanız 8080 portunu kullanıyorsa)
EXPOSE 8080
