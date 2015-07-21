# SDN_multicast_replication
## Using Software Defined Network, and then implement multicast module on replication.

### Environment :
*  Ubuntu 15.04
*  Floodlight 1.1
*  OpenFlow13
*  Mininet
*  OpenVSwitch 2.3.1
*  Eclipse 3.8.1
*  Openjdk-7

### Function :
*  Main server and backup server can be any host and any ip in any topologies
*  Client can also be each of them
*  Analyze the routes to both main server and backup server, then find the multicast point to insert the multicast rule
*  Restapi can edit the main server's and backup server's ip addresses, and can show the current settings
*  More backup servers -- hard working
*  QoS of routing policy -- hard working
*  Reliable udp on multicsat -- hard working
*  Byte level file replication OR block level replication -- hard working

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
