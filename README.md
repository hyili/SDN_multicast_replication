# SDN_multicast_replication
Using Software Defined Network, and then implement multicast module on replication.

-
environment :
-  Ubuntu 15.04
-  Floodlight 1.1
-  OpenFlow13
-  Mininet
-  OpenVSwitch 2.3.1
-  Eclipse 3.8

-
floodlight :
-  just run it
-  default main server ip : 10.0.0.1
-  default backup server ip : 10.0.0.2

-
mininet :
>  mn --controller=remote,ip=(floodlight ip),port=6653 --topo=tree,depth=2,fanout=3 --switch ovsk,protocols=OpenFlow13

-
module restapi : (2 methods)
-  url :
>  http://(floodlight ip):8080/wm/headerextract/ipspecifier/(main server ip)/(backup server ip)/json
>  http://(floodlight ip):8080/wm/headerextract/ipspecifier/show/json

-  curl :
>  curl -d '{"RHostIP":"(main server ip)", "FHostIP":"(backup server ip)"}' http://(floodlight ip)/wm/headerextract/ipspecifier/json

-
advanced restapi : (function not finished)
-  url :
>  http://(floodlight ip):8080/wm/headerextract/ipspecifier/add/(other new backup server ip)/json  
>  http://(floodlight ip):8080/wm/headerextract/ipspecifier/del/(old backup server ip)/json

-  curl :
>  curl -d '{"AddHostIP":"(other new backup server ip)", "DelHostIP":"old backup server ip"}' http://(floodlight ip)/wm/headerextract/ipspecifier/json
