package net.floodlightcontroller.headerextract;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.MatchUtils;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

public class MulticastRoutingPolicy {
	
	private static void pushPacket(IOFSwitch sw, Match match, OFPacketIn pi, OFPort outport) {
		if (pi == null) {
			return;
		}

		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_13) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT));

		if (inPort.equals(outport)) {
			if (HeaderExtract.log.isDebugEnabled()) {
				HeaderExtract.log.debug("Attempting to do packet-out to the same " +
						"interface as packet-in. Dropping packet. " +
						" SrcSwitch={}, match = {}, pi={}",
						new Object[]{sw, match, pi});
				return;
			}
		}

		if (HeaderExtract.log.isTraceEnabled()) {
			HeaderExtract.log.trace("PacketOut srcSwitch={} match={} pi={}",
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
				.setValue(MacAddress.of(HeaderExtract.RHostMAC))
				.build()
		)
		.build();
		actions.add(setDlDst);

		OFActionSetField setNwDst = action.buildSetField()
		.setField(
				oxms.buildIpv4Dst()
				.setValue(IPv4Address.of(HeaderExtract.RHostIP))
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
	
	public static boolean pushRoute(Route route_1, Route route_2, Match match, OFPacketIn pi,
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
		System.out.println("Comparing...");
		for (int indx_1 = 1, indx_2 = 1; indx_1 <= sP1_size && indx_2 <= sP2_size; indx_1 += 2, indx_2 += 2) {
			switchDPID_1 = switchPortList_1.get(indx_1).getNodeId();
			switchDPID_2 = switchPortList_2.get(indx_2).getNodeId();
			System.out.println(switchDPID_1);
			System.out.println(switchDPID_2);
			if (switchDPID_1.equals(switchDPID_2))
				dup_num = dup_num + 2;
		}
		multicast_point = switchPortList_1.get(dup_num).getNodeId();
		
		for (int indx = switchPortList_1.size() - 1; indx >= 0; indx -= 2) {
			// indx and indx-1 will always have the same switch DPID.
			switchDPID = switchPortList_1.get(indx).getNodeId();
			IOFSwitch sw = HeaderExtract.switchService.getSwitch(switchDPID);

			if (indx == dup_num) {
				multicast_orig_outport = switchPortList_1.get(indx).getPortId();
				continue;
			}
			
			if (sw == null) {
				if (HeaderExtract.log.isWarnEnabled()) {
					HeaderExtract.log.warn("Unable to push route, switch at DPID {} " + "not available", switchDPID);
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
				HeaderExtract.log.error("Could not decode OFFlowModCommand. Using MODIFY_STRICT. (Should another be used as the default?)");        
			case MODIFY_STRICT:
				fmb = sw.getOFFactory().buildFlowModifyStrict();
				break;			
			}
			
			OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
			List<OFAction> actions = new ArrayList<OFAction>();
			Match.Builder mb = MatchUtils.createRetentiveBuilder(match);

			/* set input and output ports on the switch */
			OFPort outPort = switchPortList_1.get(indx).getPortId();
			OFPort inPort = switchPortList_1.get(indx - 1).getPortId();
			mb.setExact(MatchField.IN_PORT, inPort);
			mb.setExact(MatchField.IPV4_DST, IPv4Address.of(HeaderExtract.FHostIP));
			mb.setExact(MatchField.ETH_DST, MacAddress.of(HeaderExtract.FHostMAC));
			aob.setPort(outPort);
			aob.setMaxLen(Integer.MAX_VALUE);
			actions.add(aob.build());
			
			if(HeaderExtract.FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG) {
				Set<OFFlowModFlags> flags = new HashSet<>();
				flags.add(OFFlowModFlags.SEND_FLOW_REM);
				fmb.setFlags(flags);
			}
			
			// compile
			fmb.setMatch(mb.build()) // was match w/o modifying input port
			.setActions(actions)
			.setIdleTimeout(HeaderExtract.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
			.setHardTimeout(HeaderExtract.FLOWMOD_DEFAULT_HARD_TIMEOUT)
			.setBufferId(OFBufferId.NO_BUFFER)
			.setCookie(cookie)
			.setOutPort(outPort)
			.setPriority(HeaderExtract.FLOWMOD_DEFAULT_PRIORITY);

			try {
				if (HeaderExtract.log.isTraceEnabled()) {
					HeaderExtract.log.trace("Pushing Route flowmod routeIndx={} " +
							"sw={} inPort={} outPort={}",
							new Object[] {indx,
							sw,
							fmb.getMatch().get(MatchField.IN_PORT),
							outPort });
				}
				HeaderExtract.messageDamper.write(sw, fmb.build());
				if (doFlush) {
					sw.flush();
				}

				// Push the packet out the source switch
				if (sw.getId().equals(pinSwitch)) {
				//if (sw.getId().equals(multicast_point)) {
					// TODO: Instead of doing a packetOut here we could also
					// send a flowMod with bufferId set....
					System.out.println("Packet-Out");
					pushPacket(sw, match, pi, OFPort.NORMAL);
					srcSwitchIncluded = true;
				}
			} catch (IOException e) {
				HeaderExtract.log.error("Failure writing flow mod", e);
			}
		}
		
		for (int indx = switchPortList_2.size() - 1; indx >= dup_num; indx -= 2) {
			// indx and indx-1 will always have the same switch DPID.
			switchDPID = switchPortList_2.get(indx).getNodeId();
			IOFSwitch sw = HeaderExtract.switchService.getSwitch(switchDPID);

			if (sw == null) {
				if (HeaderExtract.log.isWarnEnabled()) {
					HeaderExtract.log.warn("Unable to push route, switch at DPID {} " + "not available", switchDPID);
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
				HeaderExtract.log.error("Could not decode OFFlowModCommand. Using MODIFY_STRICT. (Should another be used as the default?)");        
			case MODIFY_STRICT:
				fmb = sw.getOFFactory().buildFlowModifyStrict();
				break;			
			}
			
			OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
			List<OFAction> actions = new ArrayList<OFAction>();
			Match.Builder mb = MatchUtils.createRetentiveBuilder(match);

			if (sw.getId().equals(multicast_point)){
				OFActions action = sw.getOFFactory().actions();
				OFOxms oxms = sw.getOFFactory().oxms();
				
				OFActionOutput output = action.buildOutput()
					    .setMaxLen(0xFFffFFff)
					    .setPort(multicast_orig_outport) // PROBLEM
					    .build();
				actions.add(output);
				
				OFActionSetField setDlDst = action.buildSetField()
						.setField(
								oxms.buildEthDst()
								.setValue(MacAddress.of(HeaderExtract.RHostMAC))
								.build()
						)
						.build();
				actions.add(setDlDst);
			
				OFActionSetField setNwDst = action.buildSetField()
						.setField(
								oxms.buildIpv4Dst()
								.setValue(IPv4Address.of(HeaderExtract.RHostIP))
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
				mb.setExact(MatchField.IPV4_DST, IPv4Address.of(HeaderExtract.RHostIP));
				mb.setExact(MatchField.ETH_DST, MacAddress.of(HeaderExtract.RHostMAC));
			}
			aob.setPort(outPort);
			aob.setMaxLen(Integer.MAX_VALUE);
			actions.add(aob.build());
			
			if(HeaderExtract.FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG) {
				Set<OFFlowModFlags> flags = new HashSet<>();
				flags.add(OFFlowModFlags.SEND_FLOW_REM);
				fmb.setFlags(flags);
			}
			
			// compile
			fmb.setMatch(mb.build()) // was match w/o modifying input port
			.setActions(actions)
			.setIdleTimeout(HeaderExtract.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
			.setHardTimeout(HeaderExtract.FLOWMOD_DEFAULT_HARD_TIMEOUT)
			.setBufferId(OFBufferId.NO_BUFFER)
			.setCookie(cookie)
			.setOutPort(outPort)
			.setPriority(HeaderExtract.FLOWMOD_DEFAULT_PRIORITY);

			try {
				if (HeaderExtract.log.isTraceEnabled()) {
					HeaderExtract.log.trace("Pushing Route flowmod routeIndx={} " +
							"sw={} inPort={} outPort={}",
							new Object[] {indx,
							sw,
							fmb.getMatch().get(MatchField.IN_PORT),
							outPort });
				}
				HeaderExtract.messageDamper.write(sw, fmb.build());
				if (doFlush) {
					sw.flush();
				}

				// Push the packet out the source switch
				if (sw.getId().equals(pinSwitch)) {
				//if (sw.getId().equals(multicast_point)) {
					// TODO: Instead of doing a packetOut here we could also
					// send a flowMod with bufferId set....
					System.out.println("Packet-Out");
					pushPacket(sw, match, pi, OFPort.NORMAL);
					srcSwitchIncluded = true;
				}
			} catch (IOException e) {
				HeaderExtract.log.error("Failure writing flow mod", e);
			}
		}
		return srcSwitchIncluded;
	}
}
