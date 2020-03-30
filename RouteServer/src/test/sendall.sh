#!/bin/bash

HOST=localhost
PORT=4000

echo host=$HOST, port=$PORT

for file in request1 request2 request3 request4 request5
do
        echo $file Request
	./hex2bin.sh $file > /dev/shm/bin_out
	cat /dev/shm/bin_out | hexdump -C
	echo $file Response
	cat /dev/shm/bin_out | nc -N $HOST $PORT | hexdump -C 
done

