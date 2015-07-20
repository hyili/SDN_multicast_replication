# SDN_multicast_replication
Using Software Defined Network, and then implement multicast module on replication.

floodlight :
-  just run it

mininet :
-  mn --controller=remote,ip=<< floodlight ip >>,port=6653 --topo=tree,depth=2,fanout=3 --switch ovsk,protocols=OpenFlow13
  
module restapi : (2 methods)
-  1.url :
  -  http://<< floodlight ip >>:8080/wm/headerextract/ipspecifier/<< main server ip >>/<< backup server ip >>/json
-  2.curl :
  -  curl -d '{"RHostIP":"<< main server ip >>", "FHostIP":"<< backup server ip >>"}' http://<< floodlight ip >>/wm/headerextract/ipspecifier/json
