package com.marcos.llmgateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;

@SpringBootTest
class LlmgatewayApplicationTests extends AbstractIntegrationTest {

	@Test
	void verifiesModuleStructure() {
		ApplicationModules.of(LlmgatewayApplication.class).verify();
	}

}
