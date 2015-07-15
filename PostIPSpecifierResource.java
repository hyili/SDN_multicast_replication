package net.floodlightcontroller.headerextract;

import java.io.IOException;

import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PostIPSpecifierResource extends ServerResource {
	// Reference /firewall
	// FirewallRulesResource.java
	// http://www.mkyong.com/java/how-to-convert-java-object-to-from-json-jackson/
	@Post
	public String Store(String fmJson) {
		IPpair temp = new IPpair();
		ObjectMapper mapper = new ObjectMapper();
		
		try {
			temp = mapper.readValue(fmJson, IPpair.class);
			System.out.println(HeaderExtract.RHostIP);
	        System.out.println(HeaderExtract.FHostIP);
	        
	        HeaderExtract.RHostIP = temp.RHostIP;
	        HeaderExtract.FHostIP = temp.FHostIP;
	        
	        System.out.println(HeaderExtract.RHostIP);
	        System.out.println(HeaderExtract.FHostIP);
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println(fmJson);
		return fmJson;
	}
}
