#!/usr/bin/env bash

yum -y groupinstall 'Development Tools'
yum -y install jasper-devel libjpeg-devel
yum -y install lcms2-devel
yum -y install wget
wget https://www.dechifro.org/dcraw/archive/dcraw-9.28.0.tar.gz
tar xvzf dcraw-9.28.0.tar.gz
(cd /dcraw && bash ./install)
(cd /dcraw && gcc -o dcraw -O4 dcraw.c -lm -ljasper -ljpeg -llcms2)
wget https://cdn.theguardian.tv/ffmpeg-release-amd64-static.tar.xz
wget https://cdn.theguardian.tv/ffmpeg-release-amd64-static.tar.xz.md5
md5sum ffmpeg-release-amd64-static.tar.xz > downloaded-md5.txt
diff downloaded-md5.txt ffmpeg-release-amd64-static.tar.xz.md5

if [ "$?" != "0" ]; then
  echo ffmpeg binary failed checksum
  exit 1
fi
xz -dc ffmpeg-release-amd64-static.tar.xz | tar xv
/usr/bin/install ffmpeg-5.0-amd64-static/ffmpeg /usr/local/bin
/usr/bin/install ffmpeg-5.0-amd64-static/ffprobe /usr/local/bin
/usr/bin/install ffmpeg-5.0-amd64-static/qt-faststart /usr/local/bin
rm -rf ffmpeg-5.0-amd64-static
rm -f ffmpeg-release-amd64-static.tar.xz ffmpeg-release-amd64-static.tar.xz.md5 downloaded-md5.txt
yum -y groupremove 'Development Tools'
yum -y remove wget
