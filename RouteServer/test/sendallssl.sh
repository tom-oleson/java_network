#!/bin/bash

HOST=172.25.3.51
PORT=19076
BIN_FILE=/dev/shm/bin_out
BIN_RESPONSE=/dev/shm/bin_resp
TIMEOUT=2

echo host=$HOST, port=$PORT

for file in request1 request2 request3 request4 request5
do
echo ====
echo $file Request:
cat $file
./hex2bin.sh $file > $BIN_FILE
cat $BIN_FILE | hexdump -C
echo ----
echo $file Response:
cat $BIN_FILE | nc --ssl -w$TIMEOUT $HOST $PORT > $BIN_RESPONSE && ./bin2hex.sh $BIN_RESPONSE && echo && hexdump -C $BIN_RESPONSE
echo ====
done

