package com.migration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "spring.jpa.defer-datasource-initialization=true",
    "migration.manatal.token=dummy",
    "migration.zoho.oauth.client-id=dummy",
    "migration.zoho.oauth.client-secret=dummy",
    "migration.zoho.oauth.refresh-token=dummy"
})
@ActiveProfiles("dev")
class ApplicationTests {

    @Test
    void contextLoads() {
    }
}
