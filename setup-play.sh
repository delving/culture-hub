#!/bin/sh

## uncomment the lines below to install the default play version. For now we need a patched version

#echo ================================
#echo Installing play in ../play-1.2.2
#echo ================================
#echo
#echo

#cd ..
#wget http://download.playframework.org/releases/play-1.2.2.zip
#unzip play-1.2.2.zip
#cd play-1.2.2

# Starting with the patched version

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

git apply ../culture-hub/error-display-patch.patch

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
cd ../../culture-hub

echo
echo
echo ========================
echo Installing play-scala...
echo ========================
echo
echo

# until we have a new release we use the latest version
# ../play/play install scala-0.9.1

mkdir module-extra
cd module-extra
git clone git://github.com/delving/play-scala.git scala-head --depth 1
cd scala-head

# http://play.lighthouseapp.com/projects/74274-play-scala/tickets/44-jvmmemory-configuration-option-not-taken-into-account#ticket-44-2
git checkout --track -b template-dev-faster origin/template-dev-faster
git apply ../../jvm-args-patch.patch
ant -Dplay.path=../../../play
cd ../../

echo
echo
echo ======================
echo Resolving dependencies
echo ======================
echo
echo

../play/play dependencies

echo =========================================
echo Done! Run the application with "../play/play run" or when in your path just "play run"
echo =========================================

