package com.a205.mafya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class MafyaApplication {

	public static void main(String[] args) {
		SpringApplication.run(MafyaApplication.class, args);
	}

}
