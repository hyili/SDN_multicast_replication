package net.floodlightcontroller.headerextract;

import java.util.HashMap;
import java.util.Map;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class ShowSettingResource extends ServerResource {
	// Reference /core/web/
	// SwitchStatisticsResource.java
	// CoreWebRoutable.java
	@Get("json")
	public Map<String, Object> retrieve() {
		HashMap<String, Object> model = new HashMap<String, Object>();
		
		model.put("RHostIP", HeaderExtract.RHostIP);
		model.put("FHostIP", HeaderExtract.FHostIP);
		model.put("AdditionalHostIP", HeaderExtract.AdditionalHostIP.toString());
		model.put("AdditionalIPNum", HeaderExtract.AdditionalIP);
		
		return model;
	}
}
