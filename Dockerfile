FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests

EXPOSE 8080

CMD ["java", "-Xmx256m", "-Xms128m", "-jar", "target/product-filter-0.0.1-SNAPSHOT.jar"]

