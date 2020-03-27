#!/bin/bash

NAME=`pwd | cut -d '/' -f 5`
echo $NAME
nohup java -Dserver.port=4001 EchoServer >console-output &
_pid=$!
echo $_pid >pid

