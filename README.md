# SDN_multicast_replication
Using Software Defined Network, and then implement multicast module on replication.

### Environment :
*  Ubuntu 15.04
*  Floodlight 1.1
*  OpenFlow13
*  Mininet
*  OpenVSwitch 2.3.1
*  Eclipse 3.8.1
*  Openjdk-7

### Function :
*  Main server and backup server can be any host and any ip in any given topologies.
*  Client can also be each of host in topology.
*  Reduce the bandwidth waste by multicast.
*  Restapi can edit the main server's and backup server's ip addresses, and can show the current settings.
*  More backup servers -- hard working
  *  First, build a multicast tree with self-defined structure to improve the efficient of inserting the rules.
  *  Then, record the branch point which is the place where multicast rule insert into and assign each server a vlan-id.
  *  Finally, we can then insert the rule one by one from parent to leaves.
*  QoS of routing policy -- hard working  
  *  Detect bottleneck bandwidth of the route and choose the maximum one.
  *  Search for suitable floodlight function.
*  Reliable udp on multicsat -- hard working
  *  First, set a reliable udp main server, a reliable udp backup server and a reliable udp client.
  *  Second, modify module to implement packet buffering and receive the ack from server.
  *  Then, client only needs to contact with main server without handling backup server request. Let controller take care of it with packet-in message and vlan-id.
*  Byte level file replication OR block level replication -- not yet

### Floodlight :
*  Just run it
*  Default main server ip : 10.0.0.1
*  Default backup server ip : 10.0.0.2

### Mininet :
```shell
mn --controller=remote,ip=(floodlight ip),port=6653 --topo=tree,depth=2,fanout=3 --switch ovsk,protocols=OpenFlow13
```

### Restapi : (2 methods)
*  url :
```
http://(floodlight ip):8080/wm/headerextract/ipspecifier/(main server ip)/(backup server ip)/json
http://(floodlight ip):8080/wm/headerextract/ipspecifier/show/json
```

*  curl :
```shell
curl -d '{"RHostIP":"(main server ip)", "FHostIP":"(backup server ip)"}' http://(floodlight ip)/wm/headerextract/ipspecifier/json
```

### Advanced Restapi : (function not finished)
*  url :
```
http://(floodlight ip):8080/wm/headerextract/ipspecifier/add/(other new backup server ip)/json  
http://(floodlight ip):8080/wm/headerextract/ipspecifier/del/(old backup server ip)/json
```

*  curl :
```shell
curl -d '{"AddHostIP":"(other new backup server ip)", "DelHostIP":"old backup server ip"}' http://(floodlight ip)/wm/headerextract/ipspecifier/json
```
