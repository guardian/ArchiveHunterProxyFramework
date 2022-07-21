#!/usr/bin/env bash

yum -y groupinstall 'Development Tools'
yum -y install jasper-devel libjpeg-devel
yum -y install lcms2-devel
yum -y install wget
wget https://www.dechifro.org/dcraw/archive/dcraw-9.28.0.tar.gz
tar xvzf dcraw-9.28.0.tar.gz
(cd /dcraw && bash ./install)
(cd /dcraw && gcc -o dcraw -O4 dcraw.c -lm -ljasper -ljpeg -llcms2)
yum -y groupremove 'Development Tools'
yum -y remove wget
