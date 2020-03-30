#!/bin/bash

HOST=localhost
PORT=4000

echo host=$HOST, port=$PORT

file=$1
echo $file Request
./hex2bin.sh $file > /dev/shm/bin_out
cat /dev/shm/bin_out | hexdump -C
echo $file Response
cat /dev/shm/bin_out | nc -N $HOST $PORT | hexdump -C 

