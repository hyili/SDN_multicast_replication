package net.floodlightcontroller.headerextract;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class MoreBackupServerResource extends ServerResource {
	// Reference /core/web/
	// SwitchStatisticsResource.java
	// CoreWebRoutable.java
	@Get("json")
	public Map<String, String> retrieve() {
		HashMap<String, String> model = new HashMap<String, String>();
		
		model.put("Previous_IP_list", HeaderExtract.AdditionalHostIP.toString());
		System.out.println(HeaderExtract.AdditionalHostIP);
		
		try {
			String AddHostIP = (String) getRequestAttributes().get(HeaderExtract.STR_AddHostIP);
			if (Pattern.matches(HeaderExtract.IP_Regex, AddHostIP))
				if (HeaderExtract.AdditionalHostIP.contains(AddHostIP))
					model.put("IP_Add", "Already existed");
				else if (HeaderExtract.AdditionalHostIP.add(AddHostIP)) {
					HeaderExtract.AdditionalIP++;
					model.put("IP_Add", "OK");
				}
				else
					model.put("IP_Add", "Failed");
			else
				model.put("IP_Add", "Illegal pattern");
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
		
		model.put("After_IP_list", HeaderExtract.AdditionalHostIP.toString());
		System.out.println(HeaderExtract.AdditionalHostIP);
		
		return model;
	}
}
