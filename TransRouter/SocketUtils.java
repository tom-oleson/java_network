package com.efx.tps;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class SocketUtils {

	
	public static Socket getSocket(String server, int port, int socketTimeout) throws IOException {
		
		Socket socket = new Socket(server, port);
		socket.setSoTimeout(socketTimeout);
		socket.setReuseAddress(true);
		return socket;
	}	
	
	public static void writeBytes(BufferedOutputStream bos, byte[] buf, int sz)
			throws IOException {
		bos.write(buf, 0, sz);
		bos.flush();
	}

	public static int readBytes(BufferedInputStream bis, byte[] buf, int sz)
			throws IOException {

		// read until we get data or an error...
		int num_bytes = 0;
		while (true) {
			num_bytes = bis.read(buf, 0, sz);
			if (num_bytes == -1)
				return -1;
			if (num_bytes > 0)
				break;
		}
		return num_bytes;
	}

	public static void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {}
	}
}
