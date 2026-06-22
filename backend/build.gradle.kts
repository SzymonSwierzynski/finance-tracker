plugins {
    java
    id("org.springframework.boot") version "3.5.15"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
    id("com.diffplug.spotless") version "7.0.2"
}

group = "com.financetracker"
version = "0.1.0"
description = "Personal Finance Tracker backend"

java {
    toolchain {
        // Contract pins Java 21 (LTS) even though a newer JDK may be installed.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val springdocVersion = "2.8.9"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// Coverage is meaningful only on logic; exclude boilerplate (entry point,
// config, DTO records, JPA entities) so the gate reflects services + utils.
val coverageExclusions =
    listOf(
        "com/financetracker/Application.class",
        "com/financetracker/**/config/**",
        "com/financetracker/config/**",
        "com/financetracker/**/dto/**",
        "com/financetracker/**/*Request.class",
        "com/financetracker/**/*Response.class",
        "com/financetracker/**/*Dto.class",
        "com/financetracker/**/*Entity.class",
        "com/financetracker/auth/User.class",
        "com/financetracker/auth/RefreshToken.class",
        "com/financetracker/settings/Settings.class",
        "com/financetracker/account/Account.class",
        "com/financetracker/transaction/Transaction.class",
        "com/financetracker/common/UserOwnedEntity.class",
        "com/financetracker/common/BaseEntity.class",
    )

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) { exclude(coverageExclusions) }
            },
        ),
    )
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) { exclude(coverageExclusions) }
            },
        ),
    )
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                // Pragmatic floor for Phase 1 (money/auth/settings well covered);
                // ramp toward the contract's ~80% in the hardening phase.
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.24.0")
        importOrder()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
