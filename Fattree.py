#!/usr/bin/python

from mininet.topo import Topo
from mininet.net import Mininet
from mininet.link import TCLink
from mininet.util import dumpNodeConnections
from mininet.log import setLogLevel
from mininet.node import RemoteController
from mininet.cli import CLI

#class MyTopo( Topo ):
#	"Simple topology example."
#
#	def __init__( net ):
#		"Create custom topo."
#	
#		# Initialize topology
#		Topo.__init__( net )

def perfTest():
	"Create network and run simple performance test"
	#topo = MyTopo()
	net = Mininet( topo=None, link=TCLink, controller=RemoteController )
	net.addController('c', controller=RemoteController, ip='127.0.0.1', port=6633)

	# Add hosts and switches
	Pod_host = []
	Pod_switch = []
	Aggr_switch = []
	Core_switch = []
	for i in range(1001, 1005, 1):
		t = net.addSwitch( str(i) )
		Core_switch.append( t )
	for i in range(2001, 2009, 1):
		t = net.addSwitch( str(i) )
		Aggr_switch.append( t )
	for i in range(3001, 3009, 1):
		t = net.addSwitch( str(i) )
		Pod_switch.append( t )
	for i in range(0, 16, 1):
		Pod_host.append( net.addHost( str(i) ) )

	net.addLink(Pod_host[0], Pod_switch[0], bw=100)
	net.addLink(Pod_host[1], Pod_switch[0], bw=100)
	net.addLink(Pod_host[2], Pod_switch[1], bw=100)
	net.addLink(Pod_host[3], Pod_switch[1], bw=100)
	net.addLink(Pod_host[4], Pod_switch[2], bw=100)
	net.addLink(Pod_host[5], Pod_switch[2], bw=100)
	net.addLink(Pod_host[6], Pod_switch[3], bw=100)
	net.addLink(Pod_host[7], Pod_switch[3], bw=100)
	net.addLink(Pod_host[8], Pod_switch[4], bw=100)
	net.addLink(Pod_host[9], Pod_switch[4], bw=100)
	net.addLink(Pod_host[10], Pod_switch[5], bw=100)
	net.addLink(Pod_host[11], Pod_switch[5], bw=100)
	net.addLink(Pod_host[12], Pod_switch[6], bw=100)
	net.addLink(Pod_host[13], Pod_switch[6], bw=100)
	net.addLink(Pod_host[14], Pod_switch[7], bw=100)
	net.addLink(Pod_host[15], Pod_switch[7], bw=100)

	net.addLink(Pod_switch[0], Aggr_switch[0], bw=100)
	net.addLink(Pod_switch[0], Aggr_switch[1], bw=100)
	net.addLink(Pod_switch[1], Aggr_switch[0], bw=100)
	net.addLink(Pod_switch[1], Aggr_switch[1], bw=100)
	net.addLink(Pod_switch[2], Aggr_switch[2], bw=100)
	net.addLink(Pod_switch[2], Aggr_switch[3], bw=100)
	net.addLink(Pod_switch[3], Aggr_switch[2], bw=100)
	net.addLink(Pod_switch[3], Aggr_switch[3], bw=100)
	net.addLink(Pod_switch[4], Aggr_switch[4], bw=100)
	net.addLink(Pod_switch[4], Aggr_switch[5], bw=100)
	net.addLink(Pod_switch[5], Aggr_switch[4], bw=100)
	net.addLink(Pod_switch[5], Aggr_switch[5], bw=100)
	net.addLink(Pod_switch[6], Aggr_switch[6], bw=100)
	net.addLink(Pod_switch[6], Aggr_switch[7], bw=100)
	net.addLink(Pod_switch[7], Aggr_switch[6], bw=100)
	net.addLink(Pod_switch[7], Aggr_switch[7], bw=100)

	net.addLink(Core_switch[0], Aggr_switch[0], bw=1000)
	net.addLink(Core_switch[0], Aggr_switch[2], bw=1000)
	net.addLink(Core_switch[0], Aggr_switch[4], bw=1000)
	net.addLink(Core_switch[0], Aggr_switch[6], bw=1000)
	net.addLink(Core_switch[1], Aggr_switch[1], bw=1000)
	net.addLink(Core_switch[1], Aggr_switch[3], bw=1000)
	net.addLink(Core_switch[1], Aggr_switch[5], bw=1000)
	net.addLink(Core_switch[1], Aggr_switch[7], bw=1000)
	net.addLink(Core_switch[2], Aggr_switch[0], bw=1000)
	net.addLink(Core_switch[2], Aggr_switch[2], bw=1000)
	net.addLink(Core_switch[2], Aggr_switch[4], bw=1000)
	net.addLink(Core_switch[2], Aggr_switch[6], bw=1000)
	net.addLink(Core_switch[3], Aggr_switch[1], bw=1000)
	net.addLink(Core_switch[3], Aggr_switch[3], bw=1000)
	net.addLink(Core_switch[3], Aggr_switch[5], bw=1000)
	net.addLink(Core_switch[3], Aggr_switch[7], bw=1000)

	net.start()
	for i in range(0, 4, 1):
		t = Core_switch[i]
		t.cmd('ovs-vsctl set bridge '+str(i)+' stp-enable=true')
	for i in range(0, 8, 1):
		t = Aggr_switch[i]
		t.cmd('ovs-vsctl set bridge '+str(i)+' stp-enable=true')
	for i in range(0, 8, 1):
		t = Pod_switch[i]
		t.cmd('ovs-vsctl set bridge '+str(i)+' stp-enable=true')

#	print "*** Dumping host connections ***"
#	dumpNodeConnections( net.hosts )
#	print "*** Testing network connectivity ***"
#	net.pingAll()
#	print "*** Testing bandwidth between h1 and h2 ***"
#	h0, h1, h2 = net.get( '0', '1', '15' )
#	h1.popen('iperf -s -u -i 1 > SamePod', shell=True)
#	h2.popen('iperf -s -u -i 1 > DiffPod', shell=True)
#	h0.cmdPrint('iperf -c '+h1.IP()+' -u -t 10 -i 1 -b 100m')
#	h0.cmdPrint('iperf -c '+h2.IP()+' -u -t 10 -i 1 -b 100m')
	print "*** Starting CLI ***"
	CLI(net)
	net.stop()

if __name__ == '__main__':
	setLogLevel( 'info' )
	perfTest()
