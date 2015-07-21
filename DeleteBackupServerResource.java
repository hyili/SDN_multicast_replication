package net.floodlightcontroller.headerextract;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class DeleteBackupServerResource extends ServerResource {
	// Reference /core/web/
	// SwitchStatisticsResource.java
	// CoreWebRoutable.java
	@Get("json")
	public Map<String, String> retrieve() {
		HashMap<String, String> model = new HashMap<String, String>();
		
		model.put("Previous_IP_list", HeaderExtract.AdditionalHostIP.toString());
		System.out.println(HeaderExtract.AdditionalHostIP);
		
		try {
			String DelHostIP = (String) getRequestAttributes().get(HeaderExtract.STR_DelHostIP);
			if (Pattern.matches(HeaderExtract.IP_Regex, DelHostIP))
				if (!HeaderExtract.AdditionalHostIP.contains(DelHostIP))
					model.put("IP_Del", "Nothing to delete");
				else if (HeaderExtract.AdditionalHostIP.remove(DelHostIP) && HeaderExtract.AddtionalIP > 0) {
					HeaderExtract.AddtionalIP--;
					model.put("IP_Del", "OK");
				}
				else
					model.put("IP_Del", "Failed");
			else
				model.put("IP_Del", "Illegal pattern");
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
		
		model.put("After_IP_list", HeaderExtract.AdditionalHostIP.toString());
		System.out.println(HeaderExtract.AdditionalHostIP);
		
		return model;
	}
}
