package com.lastmilebanking.backend.integration;

import com.lastmilebanking.backend.config.DevTestUserSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
public class ProdProfileSeederExclusionTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testSeederDoesNotExecuteUnderProdProfile() {
        // Since test hasn't supplied LASTMILE_TEST_PASSWORD and it doesn't fail, 
        // the seeder is clearly not running.
        // Verify the seeder bean is entirely excluded from the production context
        assertThat(applicationContext.containsBean("devTestUserSeeder")).isFalse();
    }
}
