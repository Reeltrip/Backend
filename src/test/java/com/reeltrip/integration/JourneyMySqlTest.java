package com.reeltrip.integration;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "app.analysis.enabled=true", "app.maps.persist-enrichment=false",
        "spring.datasource.url=${REELTRIP_JOURNEY_MYSQL_URL}", "spring.datasource.username=root",
        "spring.datasource.password=", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfEnvironmentVariable(named = "REELTRIP_JOURNEY_MYSQL_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/reeltrip_journey.*")
class JourneyMySqlTest extends JourneyHttpScenario { }
