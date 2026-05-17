package org.testpods.examples.order;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testpods.junit.TestPod;
import org.testpods.junit.TestPods;

/**
 * Integration test that requires external PostgreSQL and Kafka.
 * Run via the system-tests module with TestPods providing the dependencies.
 */
@SpringBootTest
@Disabled("Integration test requires external PostgreSQL and Kafka - run via system-tests module")
@TestPods
class OrderServiceApplicationTests {


	@TestPod


	@Test
	void contextLoads() {
	}

}
