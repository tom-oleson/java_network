package com.efx.tps;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Builder;

@Builder
public class RouteConfiguration {

	static Logger log = LoggerFactory.getLogger(RouteConfiguration.class);	
	
	public String path;
	public Map<String,Target> namedTargets;
	public List<Route> namedRoutes;
	public List<Route> starRoutes;
	public List<Route> allRoutes;
	public List<Target> allTargets;
	

	public List<String> readFile() throws Exception {

		log.info(String.format("processing %s", path));

		BufferedReader reader = null;
		
		File external = new File(path);
		if(external.exists()) {
			// load from external file
			FileInputStream fis = new FileInputStream(external);
			reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
		}
		else {
			// load from jar resources
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			InputStream is = loader.getResourceAsStream(path);
			reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		}
		
		
	    List<String> results = new ArrayList<String>();
	    String line = reader.readLine();
	    while (line != null) {
	        results.add(line);
	        line = reader.readLine();
	    }
	    reader.close();
	      
	    log.info(String.format("read %d lines from %s",  results.size(), path));
	     
	    return results;
	 }
	
	public List<Target> getRouteTargets(String terminalID) {
	
		Set<Target> matchingTargets = new HashSet<Target>();
		List<Route> matchingRoutes = new ArrayList<Route>();
		
		// find all matching routes for terminal ID
		for(Route route: namedRoutes) {
			if(terminalID.contains(route.terminalID)) {
				matchingRoutes.add(route);
			}
		}
		
		// if no specific terminal ID route(s) found, consider star (*) routes
		if(matchingRoutes.size() == 0) {
			for(Route route: starRoutes) {
				if(route.terminalID.equals("*")) {
					matchingRoutes.add(route);
				}
			}
		}
		
		// resolve the matching routes to targets
		for(Route route: matchingRoutes) {
			if(route.target.equals("*")) {
				// add all targets
				for(Target target: allTargets) {
					matchingTargets.add(target);
				}
			}
			else {
				// find target by name
				Target found = namedTargets.get(route.target);
				if(found != null) {
					matchingTargets.add(found);
				}
			}
		}
		
		ArrayList<Target> results = new ArrayList<Target>();
		results.addAll(matchingTargets);
		return results;
	}
	
	public void addRoute(Route route) {
		if(route.terminalID.equals("*")) {
			starRoutes.add(route);
			allRoutes.add(route);
		}
		else if(!route.terminalID.isEmpty()) {
			namedRoutes.add(route);
			allRoutes.add(route);
		}
	}
	
	public void addTarget(Target target) {
		namedTargets.put(target.name,  target);
		allTargets.add(target);
	}
	
	public void load() {
		
			try {
			
			int lineNum = 0;
			
			namedTargets = new HashMap<String, Target>();
			namedRoutes = new ArrayList<Route>();
			starRoutes = new ArrayList<Route>();
			allRoutes = new ArrayList<Route>();
			allTargets = new ArrayList<Target>();
			
			List<String> lines = readFile();
			if(lines.size() > 0) {
				for(String s: lines) {
					lineNum++;
					// if not empty line and not a comment line
					if(s.length() > 0 && s.charAt(0) != '#') {
						String[] parts = s.split("\\|");
						if(parts.length < 3) {
							log.error(String.format("Short entry at line %d: %s", lineNum, s));
							continue;
						}
						String ruleType = parts[0];
						if(parts.length > 3 && ruleType.equalsIgnoreCase("TARGET")) {
								
								String mode = parts[1];
								String targetName = parts[2];
								String hostAndPort = parts[3];
								
								if(targetName.isEmpty() || hostAndPort.isEmpty()) {
									log.error(String.format("Missing target or host:port at line %d: %s", lineNum, s));
									continue;
								}
								
								String[] host_parts = hostAndPort.split(":");
								if(host_parts.length < 2) {
									log.error(String.format("Missing host or port at line %d: %s", lineNum, s));
									continue;
								}
								Target target = Target.builder()
										.mode(mode).name(targetName).host(host_parts[0]).port(Integer.parseInt(host_parts[1]))
										.build();
								
								addTarget(target);
							}
						else if(parts.length > 2 && ruleType.equalsIgnoreCase("ROUTE")) {
							String terminalID = parts[1];
							String target = parts[2];
							
							if(terminalID.isEmpty() || target.isEmpty()) {
								log.error(String.format("Missing terminalID or target at line %d: %s", lineNum, s));
								continue;
							}
							
							Route route = Route.builder().terminalID(terminalID).target(target).build();
							addRoute(route);
						}
						else {
							log.error(String.format("Invalid rule at line %d: %s", lineNum, s));
						}
					}
				}
			}
		
			log.info(String.format("loaded %d target rules, %d route rules from %s",  allTargets.size(), allRoutes.size(), path ));
			
		} catch(Exception ex) { log.error("load failed: "+ex.getMessage()); }
	}
	
}
