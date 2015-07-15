package net.floodlightcontroller.headerextract;

import java.util.HashMap;
import java.util.Map;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class IPSpecifierResource extends ServerResource{
	// Reference /core/web/
	// SwitchStatisticsResource.java
	// CoreWebRoutable.java
	@Get("json")
    public Map<String, Object> retrieve() {
        HashMap<String, Object> model = new HashMap<String, Object>();
        
        System.out.println(HeaderExtract.RHostIP);
        System.out.println(HeaderExtract.FHostIP);
        
        String RHostIP = (String) getRequestAttributes().get(HeaderExtract.STR_RHostIP);
        String FHostIP = (String) getRequestAttributes().get(HeaderExtract.STR_FHostIP);
        HeaderExtract.RHostIP = RHostIP;
        HeaderExtract.FHostIP = FHostIP;
        
        System.out.println(HeaderExtract.RHostIP);
        System.out.println(HeaderExtract.FHostIP);
        model.put("OK", new Long(2));
        return model;
    }
}
