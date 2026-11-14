package com.reeltrip.integration;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "app.analysis.enabled=true", "app.maps.persist-enrichment=false",
        "spring.datasource.url=jdbc:h2:mem:journey;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER,VALUE;DB_CLOSE_DELAY=-1"
})
class JourneyHttpTest extends JourneyHttpScenario { }
