#!/bin/sh

echo
echo =================================================
echo Installer script for iipsrv
echo You need to have XCode installed for this to work
echo =================================================
echo
echo
echo ==================
echo Installing libjpeg
echo ==================
echo

wget http://www.ijg.org/files/jpegsrc.v8c.tar.gz
tar -xzf jpegsrc.v8c.tar.gz
cd jpeg-8c
cp /usr/share/libtool/config/config.sub .
cp /usr/share/libtool/config/config.guess .
./configure --enable-shared --enable-static
make
sudo make install
sudo ranlib /usr/local/lib/libjpeg.a
cd ..

echo
echo ==================
echo Installing libtiff
echo ==================
echo

wget http://download.osgeo.org/libtiff/tiff-3.9.5.tar.gz
tar -xzf tiff-3.9.5.tar.gz 
cd tiff-3.9.5
cp /usr/share/libtool/config/config.sub .
cp /usr/share/libtool/config/config.guess .
./configure --enable-shared --enable-static
make
sudo make install
sudo ranlib /usr/local/lib/libtiff.a
cd ..

echo
echo ==========================
echo Installing IIPImage Server
echo ==========================
echo


wget "http://downloads.sourceforge.net/project/iipimage/IIP%20Server/iipsrv-0.9.9/iipsrv-0.9.9.tar.bz2?r=http%3A%2F%2Fiipimage.sourceforge.net%2Fdownload%2F&ts=1308752719&use_mirror=ovh"
tar -xjf iipsrv-0.9.9.tar.bz2
cd iipsrv-0.9.9
./configure
make
make install
cp src/iipsrv.fcgi fcgi/

echo
echo ==========================================================
echo Done! The binary is fcgi/iipsrv.fcgi, you can run it with:
echo "./iipsrv.fcgi --bind 127.0.0.1:7000"
echo ==========================================================
