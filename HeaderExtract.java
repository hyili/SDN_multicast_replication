package net.floodlightcontroller.headerextract;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.VlanVid;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.resource.Get;
import org.restlet.routing.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.module.ModuleLoaderResource;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestletRoutable;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.MatchUtils;
import net.floodlightcontroller.util.OFMessageDamper;



public class HeaderExtract implements IFloodlightModule, IOFMessageListener, RestletRoutable {

	protected IFloodlightProviderService floodlightProvider;
	protected Set<Long> macAddresses;
	protected static Logger logger;
	protected static OFMessageDamper messageDamper;
	protected static IRoutingService routingEngine;
	protected static IDeviceService deviceService;
	protected static IOFSwitchService switchService;
	protected static IRestApiService restapi; 
	protected static Logger log = LoggerFactory.getLogger(HeaderExtract.class);
	public static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 0;  //  sec
	public static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0;  //  infinite
	protected static int OFMESSAGE_DAMPER_CAPACITY = 10000; // ms
	protected static int OFMESSAGE_DAMPER_TIMEOUT = 250; // ms
	
	public static final int FORWARDING_APP_ID = 2;
	public static boolean FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG = false;
	protected static final short FLOWMOD_DEFAULT_PRIORITY = Short.MAX_VALUE;
	public static boolean FLOWMOD_DEFAULT_MATCH_MAC = true;
	public static boolean FLOWMOD_DEFAULT_MATCH_VLAN = false;
	public static boolean FLOWMOD_DEFAULT_MATCH_IP_ADDR = true;
	public static boolean FLOWMOD_DEFAULT_MATCH_TRANSPORT = true;
	
	public static final String STR_FHostIP = "FHostIP";
	public static final String STR_RHostIP = "RHostIP";
	public static final String STR_AddHostIP = "AddHostIP";
	public static final String STR_DelHostIP = "DelHostIP";
	public static final String IP_Regex = "^([1-9]?\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\.([1-9]?\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\.([1-9]?\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\.([1-9]?\\d|1\\d\\d|2[0-4]\\d|25[0-5])$";
	public static String FHostIP = "10.0.0.2";
	public static String RHostIP = "10.0.0.1";
	public static ArrayList<String> AdditionalHostIP = new ArrayList<String>();
	public static int AddtionalIP = 0;
	
	protected String PORT = "5134";
	protected static String FHostMAC = "";
	protected static String RHostMAC = "";
	
	@Override
	public String getName() {
		return HeaderExtract.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}
	
	@Get("json")
	public Restlet getRestlet(Context context) {
		Router router = new Router(context);
		router.attach("/ipspecifier/add/{" + STR_AddHostIP + "}/json", MoreBackupServerResource.class);
		router.attach("/ipspecifier/del/{" + STR_DelHostIP + "}/json", DeleteBackupServerResource.class);
		router.attach("/ipspecifier/{" + STR_RHostIP + "}/{" + STR_FHostIP + "}/json", IPSpecifierResource.class);
		router.attach("/ipspecifier/json", PostIPSpecifierResource.class);
		return router;
	}
	
	@Override
	public String basePath() {
		return "/wm/headerextract";
	}
	
	protected Match createMatchFromPacket(IOFSwitch sw, OFPort inPort, FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		VlanVid vlan = VlanVid.ofVlan(eth.getVlanID());
		MacAddress srcMac = eth.getSourceMACAddress();
		MacAddress dstMac = eth.getDestinationMACAddress();
		
		Match.Builder mb = sw.getOFFactory().buildMatch();
		mb.setExact(MatchField.IN_PORT, inPort);
		
		if (FLOWMOD_DEFAULT_MATCH_MAC) {
			mb.setExact(MatchField.ETH_SRC, srcMac)
			.setExact(MatchField.ETH_DST, dstMac);
		}
		
		if (FLOWMOD_DEFAULT_MATCH_VLAN) {
			if (!vlan.equals(VlanVid.ZERO)) {
				mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(vlan));
			}
		}
		
		
		if (eth.getEtherType() == EthType.IPv4) {
			IPv4 ip = (IPv4) eth.getPayload();
			IPv4Address srcIp = ip.getSourceAddress();
			IPv4Address dstIp = ip.getDestinationAddress();
			
			if (FLOWMOD_DEFAULT_MATCH_IP_ADDR) {
				mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_SRC, srcIp)
				.setExact(MatchField.IPV4_DST, dstIp);
			}
			
			if (FLOWMOD_DEFAULT_MATCH_TRANSPORT) {
				if (!FLOWMOD_DEFAULT_MATCH_IP_ADDR) {
					mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
				}
				
				if (ip.getProtocol().equals(IpProtocol.TCP)) {
					TCP tcp = (TCP) ip.getPayload();
					mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
					.setExact(MatchField.TCP_SRC, tcp.getSourcePort())
					.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
				} else if (ip.getProtocol().equals(IpProtocol.UDP)) {
					UDP udp = (UDP) ip.getPayload();
					mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
					//.setExact(MatchField.UDP_SRC, udp.getSourcePort())
					.setExact(MatchField.UDP_DST, udp.getDestinationPort());
				}
			}
		} else if (eth.getEtherType() == EthType.ARP) {
			mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
		}
		return mb.build();
	}
	
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		
		OFPacketIn pin = (OFPacketIn) msg;
		OFPort inPort = (pin.getVersion().compareTo(OFVersion.OF_13) < 0 ? pin.getInPort() : pin.getMatch().get(MatchField.IN_PORT));
		
		/* Retrieve the deserialized packet in message */
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		
		/* Various getters and setters are exposed in Ethernet */
		MacAddress srcMac = eth.getSourceMACAddress();
		MacAddress dstMac = eth.getDestinationMACAddress();
		VlanVid vlanId = VlanVid.ofVlan(eth.getVlanID());
		
		if (eth.getEtherType() == EthType.IPv4) {
			/* We got an IPv4 packet; get the payload from Ethernet */
			IPv4 ipv4 = (IPv4) eth.getPayload();
			
			/* Various getters and setters are exposed in IPv4 */
			byte[] ipOptions = ipv4.getOptions();
			IPv4Address srcIp = ipv4.getSourceAddress();
			IPv4Address dstIp = ipv4.getDestinationAddress();
			
			if (ipv4.getProtocol().equals(IpProtocol.TCP)) {
				/* We got a TCP packet; get the payload from IPv4 */
				TCP tcp = (TCP) ipv4.getPayload();
				
				/* Various getters and setters are exposed in TCP */
				TransportPort srcPort = tcp.getSourcePort();
				TransportPort dstPort = tcp.getDestinationPort();
				short flags = tcp.getFlags();
				
			}
			else if (ipv4.getProtocol().equals(IpProtocol.UDP)) {
				/* We got a UDP packet; get the payload from IPv4 */
				UDP udp = (UDP) ipv4.getPayload();
				
				/* Various getters and setters are exposed in UDP */
				TransportPort srcPort = udp.getSourcePort();
				TransportPort dstPort = udp.getDestinationPort();
				
				if (dstPort.toString().equals(PORT)) {
					System.out.println("Packet dst port is "+PORT);
					if (dstIp.toString().equals(FHostIP)) {
						IDevice srcDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);
						IDevice dstDevice_1 = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
						FHostMAC = dstDevice_1.getMACAddressString();
						IDevice dstDevice_2 = null;
						Collection<? extends IDevice> allDevice = deviceService.getAllDevices();
						System.out.println("** Start to find the dst device **");
						for (IDevice testDevice : allDevice) {
							if (testDevice != null) {
								IPv4Address[] deviceIP = testDevice.getIPv4Addresses();  //  IP can't be fetched correctly
								String deviceMAC = testDevice.getMACAddressString();
								try {
									System.out.println(deviceIP[0].toString());
									System.out.println(RHostIP);
									if (deviceIP[0].toString().equals(RHostIP)) {
										System.out.println("Find it");
										dstDevice_2 = testDevice;
										RHostMAC = deviceMAC;
										System.out.println(RHostMAC);
										break;
									}
								}
								catch (ArrayIndexOutOfBoundsException e){
									System.out.println(testDevice.getMACAddressString());
								}
							}
							else
								continue;
						}
						System.out.println("** Start to find the shortest route **");
						if ((dstDevice_1 != null) && (dstDevice_2 != null) && (srcDevice != null)) {
							SwitchPort[] srcSwitchPort = srcDevice.getAttachmentPoints();
							SwitchPort[] dstSwitchPort_1 = dstDevice_1.getAttachmentPoints();
							SwitchPort[] dstSwitchPort_2 = dstDevice_2.getAttachmentPoints();
							Route route_1 = routingEngine.getRoute(srcSwitchPort[0].getSwitchDPID(), srcSwitchPort[0].getPort(), dstSwitchPort_1[0].getSwitchDPID(), dstSwitchPort_1[0].getPort(), U64.of(0));
							Route route_2 = routingEngine.getRoute(srcSwitchPort[0].getSwitchDPID(), srcSwitchPort[0].getPort(), dstSwitchPort_2[0].getSwitchDPID(), dstSwitchPort_2[0].getPort(), U64.of(0));
							
							if ((route_1 != null) && (route_2 != null)) {
								System.out.println("Find it");
								U64 cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

								Match match = createMatchFromPacket(sw, inPort, cntx);
								boolean requestFlowRemovedNotifn = false;
								
								System.out.println("** Start to push the rule **");
								
								if (srcSwitchPort[0].getSwitchDPID() == sw.getId()){
									MulticastRoutingPolicy.pushRoute(route_1, route_2, match, pin, sw.getId(), cookie,
										cntx, requestFlowRemovedNotifn, false,
										OFFlowModCommand.ADD);
								}

								System.out.println("** Finish **");
							}

						}
					}
				}
			}
			else {
				/* More to come here */
			}
		}
		else if (eth.getEtherType() == EthType.ARP) {
			/* We got an ARP packet; get the payload from Ethernet */
			ARP arp = (ARP) eth.getPayload();
			
			/* More to come here */
		}
		else {
			/* Unhandled ethertype */
		}
		
		return Command.CONTINUE;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IRoutingService.class);
		l.add(IDeviceService.class);
		l.add(IOFSwitchService.class);
		l.add(IRestApiService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		macAddresses = new ConcurrentSkipListSet<Long>();
		logger = LoggerFactory.getLogger(HeaderExtract.class);
		routingEngine = context.getServiceImpl(IRoutingService.class);
		deviceService = context.getServiceImpl(IDeviceService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		restapi = context.getServiceImpl(IRestApiService.class);
		messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY, EnumSet.of(OFType.FLOW_MOD), OFMESSAGE_DAMPER_TIMEOUT);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		restapi.addRestletRoutable(this);
	}
}
