package io.pivotal.scs.sample;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@ConstructorBinding
@ConfigurationProperties
public class Stuff {

	public List<String> clouds = new ArrayList<>();
	public List<String> languages = new ArrayList<>();
	
}
