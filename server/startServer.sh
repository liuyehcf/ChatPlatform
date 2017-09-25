java -classpath chat-platform-server-jar-with-dependencies.jar org.liuyehcf.chat.server.boot.CmdMain $1 $2 &
touch server-info.log
tail -f server-info.log
