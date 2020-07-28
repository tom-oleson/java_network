package com.efx.tps;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Builder
@EqualsAndHashCode
@ToString
public class Target {

	public String mode;		// S=server, L=listener
	public String name;
	public String host;
	public int port;
	
}
