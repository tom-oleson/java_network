#!/bin/bash

HOST=localhost
PORT=4000

echo host=$HOST, port=$PORT

for f in request1 request2 request3 request4 request5
do
        echo $f Request
	./hex2bin.sh $f > bin_out
	cat /dev/shm/bin_out | hexdump -C
	echo $f Response
	cat /dev/shm/bin_out | nc -N $HOST $PORT | hexdump -C 
done

