#!/bin/bash
#
HOST=localhost
PORT=4000
BIN_FILE=/dev/shm/bin_out
BIN_RESPONSE=/dev/shm/bin_resp
TIMEOUT=3

echo host=$HOST, port=$PORT

file=$1
echo ====
echo $file Request:
cat $file
./hex2bin.sh $file > $BIN_FILE
cat $BIN_FILE | hexdump -C
echo ----
echo $file Response:
cat $BIN_FILE | nc -w $TIMEOUT $HOST $PORT > $BIN_RESPONSE && ./bin2hex.sh $BIN_RESPONSE && echo && hexdump -C $BIN_RESPONSE
echo ====

