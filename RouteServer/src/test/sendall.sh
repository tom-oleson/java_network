#!/bin/bash

for f in request1 request2 request3 request4 request5
do
        echo $f Request
	./hex2bin.sh $f > bin_out
	cat bin_out | hexdump -C
	echo $f Response
	cat bin_out | nc -N localhost 4000 | hexdump -C 
done

