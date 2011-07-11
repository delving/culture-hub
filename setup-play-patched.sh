#!/bin/sh

echo =========================
echo Cloning play into ../play
echo =========================
echo
echo

cd ..
git clone git://github.com/playframework/play.git --depth 1
cd play
git checkout 1.2.x
cd ..

echo
echo
echo ================
echo Patching play...
echo ================
echo
echo
cd play
git apply ../play-services/binder-patch.patch
cd ..

echo
echo
echo =================
echo Compiling play...
echo =================
echo
echo
cd play/framework
ant
cd ../../tm

echo =====
echo Done!
echo =====
