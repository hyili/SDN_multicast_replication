package net.floodlightcontroller.headerextract;

import java.io.IOException;

import java.util.regex.Pattern;

import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

public class PostIPSpecifierResource extends ServerResource {
	// Reference /firewall
	// FirewallRulesResource.java
	// http://www.mkyong.com/java/how-to-convert-java-object-to-from-json-jackson/
	@Post
	public String Store(String fmJson) {
		IPpair temp = new IPpair();
		ObjectMapper mapper = new ObjectMapper();
		boolean post_check = true;
		boolean syntax_check = true;
		String result = "{";
		
		try {
			temp = mapper.readValue(fmJson, IPpair.class);
			System.out.println(HeaderExtract.RHostIP);
			System.out.println(HeaderExtract.FHostIP);
			System.out.println(HeaderExtract.AdditionalHostIP);
			
			if (temp.RHostIP != null && Pattern.matches(HeaderExtract.IP_Regex, temp.RHostIP)) {
				HeaderExtract.RHostIP = temp.RHostIP;
				result = result + "\"RHostIP\":\"OK\", ";
			}
			else {
				result = result + "\"RHostIP\":\"Failed\", ";
				post_check = false;
			}
			
			if (temp.FHostIP != null && Pattern.matches(HeaderExtract.IP_Regex, temp.FHostIP)) {
				HeaderExtract.FHostIP = temp.FHostIP;
				result = result + "\"FHostIP\":\"OK\", ";
			}
			else {
				result = result + "\"FHostIP\":\"Failed\", ";
				post_check = false;
			}
			
			if (temp.AddHostIP != null && Pattern.matches(HeaderExtract.IP_Regex, temp.AddHostIP))
				try {
					if (HeaderExtract.AdditionalHostIP.contains(temp.AddHostIP)) {
						result = result + "\"AddHostIP\":\"Illegal pattern\", ";
						post_check = false;
					}
					else if (HeaderExtract.AdditionalHostIP.add(temp.AddHostIP)) {
						HeaderExtract.AddtionalIP++;
						result = result + "\"AddHostIP\":\"OK\", ";
					}
					else {
						result = result + "\"AddHostIP\":\"Failed\", ";
						post_check = false;
					}
				} catch (ResourceException e) {
					e.printStackTrace();
					result = result + "\"AddHostIP\":\"Failed\", ";
					post_check = false;
				}
			System.out.println("Here?");
			if (temp.DelHostIP != null && Pattern.matches(HeaderExtract.IP_Regex, temp.DelHostIP))
				try {
					if (!HeaderExtract.AdditionalHostIP.contains(temp.DelHostIP)) {
						result = result + "\"DelHostIP\":\"Illegal pattern\", ";
						post_check = false;
					}
					else if (HeaderExtract.AdditionalHostIP.remove(temp.DelHostIP) && HeaderExtract.AddtionalIP > 0) {
						HeaderExtract.AddtionalIP--;
						result = result + "\"DelHostIP\":\"OK\", ";
					}
					else {
						result = result + "\"DelHostIP\":\"Failed\", ";
						post_check = false;
					}
				} catch (ResourceException e) {
					e.printStackTrace();
					result = result + "\"DelHostIP\":\"Failed\", ";
					post_check = false;
				}
			
			System.out.println(HeaderExtract.RHostIP);
			System.out.println(HeaderExtract.FHostIP);
			System.out.println(HeaderExtract.AdditionalHostIP);
		} catch (NullPointerException e) {
			post_check = false;
			e.printStackTrace();
		} catch (UnrecognizedPropertyException e) {
			post_check = false;
			syntax_check = false;
			e.printStackTrace();
		} catch (JsonGenerationException e) {
			post_check = false;
			e.printStackTrace();
		} catch (JsonMappingException e) {
			post_check = false;
			e.printStackTrace();
		} catch (IOException e) {
			post_check = false;
			e.printStackTrace();
		}
		
		if (post_check && syntax_check)
			result = result + "\"Post\":\"OK\"}";
		else
			if (syntax_check)
				result = result + "\"Post\":\"Failed\"}";
			else
				result = result + "\"Post\":\"Unrecognized property\"}";
		
		System.out.println(fmJson);
		return result;
	}
}
