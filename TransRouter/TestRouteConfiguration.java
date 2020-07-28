package com.efx.tps;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;


public class TestRouteConfiguration {

	static Logger log = LoggerFactory.getLogger(TestRouteConfiguration.class);
	
	@Test
	public void readConfiguration() throws Exception {

		List<Target> targets;
		
		String path = "terminals-test.conf";
		RouteConfiguration config = RouteConfiguration.builder().path(path).build();
		config.load();

		// match to specific rule
		targets = config.getRouteTargets("TES00002      ");
		assertTrue(targets.size() == 1, () -> "Failed to return expected number of targets");


		// match to specific rule
		targets = config.getRouteTargets("TES00003       ");
		assertTrue(targets.size() == 1, () -> "Failed to return expected number of targets");


		// match to star (*) rule to go to ALL (2) configured targets
		targets = config.getRouteTargets("RANDO       ");
		assertTrue(targets.size() == 2, () -> "Failed to return expected number of targets");
		
		
		//log.info(String.format("matched to %d targets", targets.size()));
		
	}

}
