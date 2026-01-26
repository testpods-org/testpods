package org.testpods.examples.product;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test that requires external PostgreSQL and Kafka.
 * Run via the system-tests module with TestPods providing the dependencies.
 */
@SpringBootTest
@Disabled("Integration test requires external PostgreSQL and Kafka - run via system-tests module")
class ProductServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
