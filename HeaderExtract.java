package net.floodlightcontroller.headerextract;

import java.io.IOException;
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
	protected OFMessageDamper messageDamper;
	protected IRoutingService routingEngine;
	protected IDeviceService deviceService;
	protected IOFSwitchService switchService;
	protected IRestApiService restapi; 
	protected static Logger log = LoggerFactory.getLogger(HeaderExtract.class);
	public static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 0;  //  sec
	public static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0;  //  infinite
	protected static int OFMESSAGE_DAMPER_CAPACITY = 10000; // ms
	protected static int OFMESSAGE_DAMPER_TIMEOUT = 250; // ms
	
	public static final int FORWARDING_APP_ID = 2;
	public static boolean FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG = false;
	protected final short FLOWMOD_DEFAULT_PRIORITY = Short.MAX_VALUE;
	public static boolean FLOWMOD_DEFAULT_MATCH_MAC = true;
	public static boolean FLOWMOD_DEFAULT_MATCH_VLAN = false;
	public static boolean FLOWMOD_DEFAULT_MATCH_IP_ADDR = true;
	public static boolean FLOWMOD_DEFAULT_MATCH_TRANSPORT = true;
	
	
	protected String PORT = "5134";
	public static final String STR_FHostIP = "FHostIP";
	public static final String STR_RHostIP = "RHostIP";
	public static String FHostIP = "10.0.0.2";
	public static String RHostIP = "10.0.0.1";
	protected String FHostMAC = "";
	protected String RHostMAC = "";
	
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return HeaderExtract.class.getSimpleName();
		//return null;
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}
	
	@Get("json")
	public Restlet getRestlet(Context context) {
    	Router router = new Router(context);
        router.attach("/ipspecifier/{" + STR_RHostIP + "}/{" + STR_FHostIP + "}/json", IPSpecifierResource.class);
        router.attach("/ipspecifier/json", PostIPSpecifierResource.class);
        return router;
	}
	
	@Override
	public String basePath() {
		return "/wm/headerextract";
	}
	
	private void pushPacket(IOFSwitch sw, Match match, OFPacketIn pi, OFPort outport) {
		if (pi == null) {
			return;
		}

		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_13) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT));

		// The assumption here is (sw) is the switch that generated the
		// packet-in. If the input port is the same as output port, then
		// the packet-out should be ignored.
		if (inPort.equals(outport)) {
			if (log.isDebugEnabled()) {
				log.debug("Attempting to do packet-out to the same " +
						"interface as packet-in. Dropping packet. " +
						" SrcSwitch={}, match = {}, pi={}",
						new Object[]{sw, match, pi});
				return;
			}
		}

		if (log.isTraceEnabled()) {
			log.trace("PacketOut srcSwitch={} match={} pi={}",
					new Object[] {sw, match, pi});
		}

		OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();

		// set actions
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActions action = sw.getOFFactory().actions();
		OFOxms oxms = sw.getOFFactory().oxms();
		
		OFActionSetField setDlDst = action.buildSetField()
		.setField(
				oxms.buildEthDst()
				.setValue(MacAddress.of(RHostMAC))
				.build()
		)
		.build();
		actions.add(setDlDst);

		OFActionSetField setNwDst = action.buildSetField()
		.setField(
				oxms.buildIpv4Dst()
				.setValue(IPv4Address.of(RHostIP))
				.build()
		)
		.build();
		actions.add(setNwDst);
		
		actions.add(sw.getOFFactory().actions().buildOutput().setPort(outport).setMaxLen(0xffFFffFF).build());
		
		pob.setActions(actions);

		// If the switch doens't support buffering set the buffer id to be none
		// otherwise it'll be the the buffer id of the PacketIn
		if (sw.getBuffers() == 0) {
			// We set the PI buffer id here so we don't have to check again below
			pi = pi.createBuilder().setBufferId(OFBufferId.NO_BUFFER).build();
			pob.setBufferId(OFBufferId.NO_BUFFER);
		} else {
			pob.setBufferId(pi.getBufferId());
		}

		pob.setInPort(inPort);

		// If the buffer id is none or the switch doesn's support buffering
		// we send the data with the packet out
		if (pi.getBufferId() == OFBufferId.NO_BUFFER) {
			byte[] packetData = pi.getData();
			pob.setData(packetData);
		}

		//counterPacketOut.increment();
		sw.write(pob.build());
	}
	
	public boolean pushRoute(Route route_1, Route route_2, Match match, OFPacketIn pi,
			DatapathId pinSwitch, U64 cookie, FloodlightContext cntx,
			boolean reqeustFlowRemovedNotifn, boolean doFlush,
			OFFlowModCommand flowModCommand) {

		boolean srcSwitchIncluded = false;

		List<NodePortTuple> switchPortList_1 = route_1.getPath();
		List<NodePortTuple> switchPortList_2 = route_2.getPath();
		int sP1_size = switchPortList_1.size();
		int sP2_size = switchPortList_2.size();
		int dup_num = -1;
		DatapathId multicast_point = null;
		OFPort multicast_orig_outport = OFPort.of(0);

		DatapathId switchDPID = null;
		DatapathId switchDPID_1 = null;
		DatapathId switchDPID_2 = null;
		System.out.println("Level 4");
		for (int indx_1 = 1, indx_2 = 1; indx_1 <= sP1_size && indx_2 <= sP2_size; indx_1 += 2, indx_2 += 2) {
			System.out.println("Level~~~~");
			switchDPID_1 = switchPortList_1.get(indx_1).getNodeId();
			switchDPID_2 = switchPortList_2.get(indx_2).getNodeId();
			System.out.println(switchDPID_1);
			System.out.println(switchDPID_2);
			if (switchDPID_1.equals(switchDPID_2))
				dup_num = dup_num + 2;
		}
		multicast_point = switchPortList_1.get(dup_num).getNodeId();
		
		System.out.println("Level 4.5");
		for (int indx = switchPortList_1.size() - 1; indx >= 0; indx -= 2) {
			// indx and indx-1 will always have the same switch DPID.
			switchDPID = switchPortList_1.get(indx).getNodeId();
			IOFSwitch sw = switchService.getSwitch(switchDPID);

			if (indx == dup_num) {
				multicast_orig_outport = switchPortList_1.get(indx).getPortId();
				continue;
			}
			
			if (sw == null) {
				if (log.isWarnEnabled()) {
					log.warn("Unable to push route, switch at DPID {} " + "not available", switchDPID);
				}
				return srcSwitchIncluded;
			}
			
			OFFlowMod.Builder fmb;
			switch (flowModCommand) {
			case ADD:
				fmb = sw.getOFFactory().buildFlowAdd();
				break;
			case DELETE:
				fmb = sw.getOFFactory().buildFlowDelete();
				break;
			case DELETE_STRICT:
				fmb = sw.getOFFactory().buildFlowDeleteStrict();
				break;
			case MODIFY:
				fmb = sw.getOFFactory().buildFlowModify();
				break;
			default:
				log.error("Could not decode OFFlowModCommand. Using MODIFY_STRICT. (Should another be used as the default?)");        
			case MODIFY_STRICT:
				fmb = sw.getOFFactory().buildFlowModifyStrict();
				break;			
			}
			
			System.out.println("Level 5");
			OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
			List<OFAction> actions = new ArrayList<OFAction>();
			Match.Builder mb = MatchUtils.createRetentiveBuilder(match);

			System.out.println("Level 6");
			
			/* set input and output ports on the switch */
			OFPort outPort = switchPortList_1.get(indx).getPortId();
			OFPort inPort = switchPortList_1.get(indx - 1).getPortId();
			mb.setExact(MatchField.IN_PORT, inPort);
			mb.setExact(MatchField.IPV4_DST, IPv4Address.of(FHostIP));
			mb.setExact(MatchField.ETH_DST, MacAddress.of(FHostMAC));
			aob.setPort(outPort);
			aob.setMaxLen(Integer.MAX_VALUE);
			actions.add(aob.build());
			
			System.out.println("Level 7");
			if(FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG) {
				Set<OFFlowModFlags> flags = new HashSet<>();
				flags.add(OFFlowModFlags.SEND_FLOW_REM);
				fmb.setFlags(flags);
			}
			
			// compile
			System.out.println("Level 8");
			fmb.setMatch(mb.build()) // was match w/o modifying input port
			.setActions(actions)
			.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
			.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
			.setBufferId(OFBufferId.NO_BUFFER)
			.setCookie(cookie)
			.setOutPort(outPort)
			.setPriority(FLOWMOD_DEFAULT_PRIORITY);

			try {
				if (log.isTraceEnabled()) {
					log.trace("Pushing Route flowmod routeIndx={} " +
							"sw={} inPort={} outPort={}",
							new Object[] {indx,
							sw,
							fmb.getMatch().get(MatchField.IN_PORT),
							outPort });
				}
				System.out.println("Level 9");
				messageDamper.write(sw, fmb.build());
				if (doFlush) {
					sw.flush();
				}

				// Push the packet out the source switch
				System.out.println("Level 10");
				if (sw.getId().equals(pinSwitch)) {
				//if (sw.getId().equals(multicast_point)) {
					// TODO: Instead of doing a packetOut here we could also
					// send a flowMod with bufferId set....
					System.out.println("Level 10.1");
					pushPacket(sw, match, pi, OFPort.NORMAL);
					srcSwitchIncluded = true;
				}
			} catch (IOException e) {
				log.error("Failure writing flow mod", e);
			}
		}
		
		System.out.println(dup_num);
		System.out.println("Level 4.5");
		for (int indx = switchPortList_2.size() - 1; indx >= dup_num; indx -= 2) {
			// indx and indx-1 will always have the same switch DPID.
			switchDPID = switchPortList_2.get(indx).getNodeId();
			IOFSwitch sw = switchService.getSwitch(switchDPID);

			if (sw == null) {
				if (log.isWarnEnabled()) {
					log.warn("Unable to push route, switch at DPID {} " + "not available", switchDPID);
				}
				return srcSwitchIncluded;
			}
			
			// need to build flow mod based on what type it is. Cannot set command later

			OFFlowMod.Builder fmb;
			switch (flowModCommand) {
			case ADD:
				fmb = sw.getOFFactory().buildFlowAdd();
				break;
			case DELETE:
				fmb = sw.getOFFactory().buildFlowDelete();
				break;
			case DELETE_STRICT:
				fmb = sw.getOFFactory().buildFlowDeleteStrict();
				break;
			case MODIFY:
				fmb = sw.getOFFactory().buildFlowModify();
				break;
			default:
				log.error("Could not decode OFFlowModCommand. Using MODIFY_STRICT. (Should another be used as the default?)");        
			case MODIFY_STRICT:
				fmb = sw.getOFFactory().buildFlowModifyStrict();
				break;			
			}
			
			System.out.println("Level 5");
			OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
			List<OFAction> actions = new ArrayList<OFAction>();
			Match.Builder mb = MatchUtils.createRetentiveBuilder(match);

			System.out.println("Level 6");
			if (sw.getId().equals(multicast_point)){
				OFActions action = sw.getOFFactory().actions();
				OFOxms oxms = sw.getOFFactory().oxms();
				
				OFActionOutput output = action.buildOutput()
					    .setMaxLen(0xFFffFFff)
					    .setPort(multicast_orig_outport) // PROBLEM
					    .build();
				actions.add(output);
				
				System.out.println("Level 6.1");
				OFActionSetField setDlDst = action.buildSetField()
						.setField(
								oxms.buildEthDst()
								.setValue(MacAddress.of(RHostMAC))
								.build()
						)
						.build();
				actions.add(setDlDst);
			
				System.out.println("Level 6.2");
				OFActionSetField setNwDst = action.buildSetField()
						.setField(
								oxms.buildIpv4Dst()
								.setValue(IPv4Address.of(RHostIP))
								.build()
						)
						.build();
				actions.add(setNwDst);
			}
			
			/* set input and output ports on the switch */
			OFPort outPort = switchPortList_2.get(indx).getPortId();
			OFPort inPort = switchPortList_2.get(indx - 1).getPortId();
			//mb.setExact(MatchField.IN_PORT, OFPort.ANY);
			mb.setExact(MatchField.IN_PORT, inPort);
			if (!sw.getId().equals(multicast_point)) {
				//mb.setExact(MatchField.IN_PORT, inPort);
				mb.setExact(MatchField.IPV4_DST, IPv4Address.of(RHostIP));
				mb.setExact(MatchField.ETH_DST, MacAddress.of(RHostMAC));
			}
			aob.setPort(outPort);
			aob.setMaxLen(Integer.MAX_VALUE);
			actions.add(aob.build());
			
			System.out.println("Level 7");
			if(FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG) {
				Set<OFFlowModFlags> flags = new HashSet<>();
				flags.add(OFFlowModFlags.SEND_FLOW_REM);
				fmb.setFlags(flags);
			}
			
			// compile
			System.out.println("Level 8");
			fmb.setMatch(mb.build()) // was match w/o modifying input port
			.setActions(actions)
			.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
			.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
			.setBufferId(OFBufferId.NO_BUFFER)
			.setCookie(cookie)
			.setOutPort(outPort)
			.setPriority(FLOWMOD_DEFAULT_PRIORITY);

			try {
				if (log.isTraceEnabled()) {
					log.trace("Pushing Route flowmod routeIndx={} " +
							"sw={} inPort={} outPort={}",
							new Object[] {indx,
							sw,
							fmb.getMatch().get(MatchField.IN_PORT),
							outPort });
				}
				System.out.println("Level 9");
				messageDamper.write(sw, fmb.build());
				if (doFlush) {
					sw.flush();
				}

				// Push the packet out the source switch
				System.out.println("Level 10");
				if (sw.getId().equals(pinSwitch)) {
				//if (sw.getId().equals(multicast_point)) {
					// TODO: Instead of doing a packetOut here we could also
					// send a flowMod with bufferId set....
					System.out.println("Level 10.1");
					pushPacket(sw, match, pi, OFPort.NORMAL);
					srcSwitchIncluded = true;
				}
			} catch (IOException e) {
				log.error("Failure writing flow mod", e);
			}
		}
		return srcSwitchIncluded;
	}
	
	protected Match createMatchFromPacket(IOFSwitch sw, OFPort inPort, FloodlightContext cntx) {
		// The packet in match will only contain the port number.
		// We need to add in specifics for the hosts we're routing between.
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

		// TODO Detect switch type and match to create hardware-implemented flow
		// TODO Allow for IPv6 matches
		if (eth.getEtherType() == EthType.IPv4) { /* shallow check for equality is okay for EthType */
			IPv4 ip = (IPv4) eth.getPayload();
			IPv4Address srcIp = ip.getSourceAddress();
			IPv4Address dstIp = ip.getDestinationAddress();
			
			if (FLOWMOD_DEFAULT_MATCH_IP_ADDR) {
				mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_SRC, srcIp)
				.setExact(MatchField.IPV4_DST, dstIp);
			}

			if (FLOWMOD_DEFAULT_MATCH_TRANSPORT) {
				/*
				 * Take care of the ethertype if not included earlier,
				 * since it's a prerequisite for transport ports.
				 */
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
		} else if (eth.getEtherType() == EthType.ARP) { /* shallow check for equality is okay for EthType */
			mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
		}
		return mb.build();
	}
	
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		
		OFPacketIn pin = (OFPacketIn) msg;
		OFPort inPort = (pin.getVersion().compareTo(OFVersion.OF_13) < 0 ? pin.getInPort() : pin.getMatch().get(MatchField.IN_PORT));
		
		/* Retrieve the deserialized packet in message */
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		
        /* Various getters and setters are exposed in Ethernet */
		MacAddress srcMac = eth.getSourceMACAddress();
		MacAddress dstMac = eth.getDestinationMACAddress();
		VlanVid vlanId = VlanVid.ofVlan(eth.getVlanID());
		
		/*          
		 * Check the ethertype of the Ethernet frame and retrieve the appropriate payload.
		 * Note the shallow equality check. EthType caches and reuses instances for valid types.
		*/
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
				
				/* Your logic here! */
			}
			else if (ipv4.getProtocol().equals(IpProtocol.UDP)) {
				/* We got a UDP packet; get the payload from IPv4 */
				UDP udp = (UDP) ipv4.getPayload();
				
				/* Various getters and setters are exposed in UDP */
				TransportPort srcPort = udp.getSourcePort();
				TransportPort dstPort = udp.getDestinationPort();
				
				if (dstPort.toString().equals(PORT)) {
					System.out.println("Level 2");
					if (dstIp.toString().equals(FHostIP)) {
						IDevice srcDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);
						IDevice dstDevice_1 = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
						FHostMAC = dstDevice_1.getMACAddressString();
						IDevice dstDevice_2 = null;
						Collection<? extends IDevice> allDevice = deviceService.getAllDevices();
						System.out.println("Level 2.3");
						for (IDevice testDevice : allDevice) {
							if (testDevice != null) {
								IPv4Address[] deviceIP = testDevice.getIPv4Addresses();  //  IP can't be fetched correctly
								String deviceMAC = testDevice.getMACAddressString();
								try {
									//System.out.println(deviceMAC);
									System.out.println(deviceIP[0].toString());
									//System.out.println(RHostMAC);
									System.out.println(RHostIP);
									//if (deviceMAC.equals(RHostMAC)) {
									if (deviceIP[0].toString().equals(RHostIP)) {
										System.out.println("Level 2.5");
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
						System.out.println("Level 2.8");
						if ((dstDevice_1 != null) && (dstDevice_2 != null) && (srcDevice != null)) {
							System.out.println("Level 3");
							SwitchPort[] srcSwitchPort = srcDevice.getAttachmentPoints();
							System.out.println("Level 3.4");
							SwitchPort[] dstSwitchPort_1 = dstDevice_1.getAttachmentPoints();
							SwitchPort[] dstSwitchPort_2 = dstDevice_2.getAttachmentPoints();
							System.out.println("Level 3.5");
							Route route_1 = routingEngine.getRoute(srcSwitchPort[0].getSwitchDPID(), srcSwitchPort[0].getPort(), dstSwitchPort_1[0].getSwitchDPID(), dstSwitchPort_1[0].getPort(), U64.of(0));
							Route route_2 = routingEngine.getRoute(srcSwitchPort[0].getSwitchDPID(), srcSwitchPort[0].getPort(), dstSwitchPort_2[0].getSwitchDPID(), dstSwitchPort_2[0].getPort(), U64.of(0));
							System.out.println("Level 3.6");
							
							if ((route_1 != null) && (route_2 != null)) {
								System.out.println("Level 3.7");
								U64 cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

								Match match = createMatchFromPacket(sw, inPort, cntx);
								boolean requestFlowRemovedNotifn = false;
								
								System.out.println("Level 3.8");
								
								if (srcSwitchPort[0].getSwitchDPID() == sw.getId()){
									pushRoute(route_1, route_2, match, pin, sw.getId(), cookie,
										cntx, requestFlowRemovedNotifn, false,
										OFFlowModCommand.ADD);
								}

								System.out.println("Final Level");
							}

						}
					}
				}
				
				/* Your logic here! */
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
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
