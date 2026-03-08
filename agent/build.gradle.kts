plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.h2database:h2")
    implementation("dev.langchain4j:langchain4j-spring-boot-starter:0.35.0")
    implementation("dev.langchain4j:langchain4j-ollama-spring-boot-starter:0.35.0")
    implementation("dev.langchain4j:langchain4j-google-ai-gemini-spring-boot-starter:0.35.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
