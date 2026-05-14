# 1. Estágio de Build
# Usamos o Maven com JDK 21 para compilar o projeto
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copia apenas o pom.xml primeiro para baixar as dependências (otimiza o cache do Docker)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copia o código fonte e gera o jar
COPY src ./src
RUN mvn clean package -DskipTests

# 2. Estágio de Execução
# Usamos uma imagem JRE 21 leve para rodar a aplicação
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copia o JAR (usando curinga para evitar erro de nome de versão)
COPY --from=build /app/target/*.jar app.jar

# Removemos o EXPOSE 8080 pois o Render usa portas dinâmicas via $PORT
ENTRYPOINT ["sh", "-c", "java \
  -Xmx300m -Xss512k \
  -Dserver.port=${PORT:-8080} \
  -Dspring.datasource.url=jdbc:${DATABASE_URL} \
  -Dspring.datasource.username=${DATABASE_USER} \
  -Dspring.datasource.password=${DATABASE_PASSWORD} \
  -Dgroq.api.key=${GROQ_API_KEY} \
  -jar app.jar \
  --spring.profiles.active=prod"]