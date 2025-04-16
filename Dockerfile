FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests

EXPOSE 8081

CMD ["java", "-jar", "target/product-filter-0.0.1-SNAPSHOT.jar"]
