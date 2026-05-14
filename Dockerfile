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

# Copia o JAR gerado no estágio anterior
# O nome do JAR segue o padrão: artifactId-version.jar do seu pom.xml
COPY --from=build /app/target/context-api-1.0.0.jar app.jar

# Expõe a porta padrão do Spring Boot
EXPOSE 8080

# Comando para iniciar a aplicação com limites de memória para o plano free do Render
ENTRYPOINT ["java", \
    "-Xmx300m", \
    "-Xss512k", \
    "-Dspring.datasource.url=jdbc:${DATABASE_URL}", \
    "-Dspring.datasource.username=${DATABASE_USER}", \
    "-Dspring.datasource.password=${DATABASE_PASSWORD}", \
    "-jar", "app.jar", \
    "--spring.profiles.active=prod"]