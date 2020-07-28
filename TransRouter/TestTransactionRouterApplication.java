package com.efx.tps;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.servlet.ServletContext;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import com.efx.tps.TransactionRouterApplication;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionRouter")
public class TestTransactionRouterApplication
{
	TransactionRouterApplication cut = new TransactionRouterApplication();	// cut = class under test
	
	@Mock
	ServletContext servletContext;
	
	@Mock
	Environment env;

	
	@Test
	@DisplayName("api method")
	void testApi() {
		assertNotNull(cut
				.api(servletContext, env), () -> "The call to api did not return the expected results");
	}

	// Constructor
	@Test
	@DisplayName("constructor")
	void testDispatchServiceApplication() {
		assertNotNull(new TransactionRouterApplication(), () -> "The call to the constructor did not return the expected results");
	}

	@Test
	@Disabled
	@Tag("main")
	@DisplayName("static main method")
	void testMain() {
		// this is for testing the static method: main
		TransactionRouterApplication.main(new String[0]);
		boolean worked = TransactionRouterApplication.context.isRunning();
		assertTrue(worked, () -> "The call to main did not provide a successful startup");
	}	
	
}
