# Ubuntu setup instructions :
# install java :
# sudo apt-get install sun-java6-bin
# launch davmail :
for i in lib/*; do export CLASSPATH=$CLASSPATH:$i; done
java -cp davmail.jar:$CLASSPATH davmail.DavGateway
