# 1. Estágio de Build
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# 2. Estágio de Execução
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Cria usuário não-root por segurança
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring

COPY --from=build /app/target/*.jar app.jar

# Variáveis de ambiente com defaults sensatos
ENV JAVA_OPTS="-Xmx300m -Xss512k -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java \
  ${JAVA_OPTS} \
  -Dserver.port=${PORT:-8080} \
  -Dspring.datasource.url=jdbc:${DATABASE_URL} \
  -Dspring.datasource.username=${DATABASE_USER} \
  -Dspring.datasource.password=${DATABASE_PASSWORD} \
  -Dgroq.api.key=${GROQ_API_KEY} \
  -jar app.jar \
  --spring.profiles.active=prod"]