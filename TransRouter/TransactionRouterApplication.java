package com.efx.tps;

import com.efx.common.base.KubernetesApplication;

import javax.servlet.ServletContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

import com.efx.common.utils.SwaggerUtils;

import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@EnableSwagger2
@ComponentScan(basePackages = "com.efx")
public class TransactionRouterApplication {

	// this variable is just used for unit testing
	static ConfigurableApplicationContext context = null;

	public static void main(String[] args) {
		context = KubernetesApplication.run(TransactionRouterApplication.class, args);
	}
	
	@Bean
	@Autowired
	public Docket api(ServletContext servletContext, Environment env) {
		return SwaggerUtils.getInstance().getSwaggerDocket("Transaction Router",
				"This application serves as a front-end router for TPS",env, servletContext);
	}
	
}
