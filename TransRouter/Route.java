package com.efx.tps;

import lombok.Builder;
import lombok.EqualsAndHashCode;

@Builder
@EqualsAndHashCode
public class Route {

	public String terminalID;
	public String target;
	
}
