# Ubuntu setup instructions :
# install java :
# sudo apt-get install sun-java6-bin
# launch davmail :
BASE=`dirname $0`
for i in $BASE/lib/*; do export CLASSPATH=$CLASSPATH:$i; done
java -cp $BASE/davmail.jar:$CLASSPATH davmail.DavGateway $1
