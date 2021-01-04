package io.pivotal.scs.sample;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
public class StuffController {

	private Stuff config;
	
	public StuffController(Stuff config) {
		this.config = config;
	}

	@GetMapping("/")
	public Mono<ResponseEntity<Stuff>> getStuff() {
		return Mono.justOrEmpty(config).map(ResponseEntity::ok);
	}
}
