import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

// test: 
// $ javac EchoServer.java
// $ java EchoServer
// $ nc localhost 4000

public class EchoServer {

	public static int PORT = 4001;
	public static int SOCKET_TIMEOUT = 180000;
	public static int POOL_THREADS = 3;


	public static int getIntProperty(String key, int def) {
		String value = System.getProperty(key);
		if(value != null) {
			return Integer.parseInt(value);
		}
		return def;
	}

	public static void info(String s) {
		System.out.println(System.currentTimeMillis()+" ["+Thread.currentThread().getId()+"] info: "+s);
	}

	public static void err(String s) {
		System.err.println(System.currentTimeMillis()+" ["+Thread.currentThread().getId()+"] error: "+s);
	}

	public static String formatHexRecord(byte[] bytes, int offset, int sz) {
		StringBuilder builder = new StringBuilder();

		for(int index = offset; index < sz; index++) {
			int value = bytes[index] & 0x00FF;
			builder.append(String.format("%02x", value));
		}
		// line seperator
		builder.append(String.format("%n"));
		return builder.toString();
	}

	public static String formatHexDump(byte[] bytes, int offset, int sz, int width) {

		StringBuilder builder = new StringBuilder();

		for(int row_offset = offset; row_offset < offset + sz; row_offset += width) {

			builder.append(String.format("%06d: ", row_offset));

			StringBuilder ascii = new StringBuilder();

			// row of hex digits of specified width followed by ascii field...
			// non-printable characters are output as '.'
			for(int index = 0; index < width; index++) {
				if(row_offset + index < sz) {
					int value = bytes[row_offset + index] & 0x00FF;
					builder.append(String.format("%02x ", value));

					if(value < 0x20 || value > 0x7e) {
						ascii.append('.');
					} else {
						ascii.append( (char) value);
					}

				} else {
					builder.append("   ");
				}
			}

			if(row_offset < sz) {
				builder.append(" | ");
				builder.append(ascii);
			}

			// line seperator
			builder.append(String.format("%n"));

		}
		return builder.toString();
	}
	
	private static class EchoTask implements Callable<Void> {

		private Socket connection;
	
		EchoTask(Socket connection) {
			this.connection = connection;
		}

		public int readBytes(BufferedInputStream bis, byte[] buf) throws IOException {

			// read until we get data or an error...
			int num_bytes = 0;
			while(num_bytes == 0) {
				num_bytes = bis.read(buf);
				if(num_bytes == -1) return -1;
			} 
			return num_bytes;
		}

		public void processBytes(BufferedOutputStream bos, byte[] buf, int sz) throws IOException {

			// output to server console
			info("======");
			info("| REQUEST (READ)");
			System.out.print(formatHexDump(buf, 0, sz, 16));
			//System.out.print(formatHexRecord(buf, 0, sz));

			// echo to client stream as response
			String resp = "response: ";
			bos.write(resp.getBytes());
			bos.write(buf, 0, sz);
			bos.flush();
			info("| RESPONSE (WRITE)");
		}

		@Override
		public Void call() {

			info("Running thread for "+connection.toString()+" [connection timeout="+SOCKET_TIMEOUT+"]");
			try {
				BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
				BufferedOutputStream bos = new BufferedOutputStream(connection.getOutputStream());

				// String hello = "EchoServerHello\r\n";
				// bos.write(hello.getBytes());
				// bos.flush();

				int read_count = 0;
				byte[] buf = new byte[4096];

				while(true) {
					if((read_count = readBytes(bis, buf)) > 0) {				
						processBytes(bos, buf, read_count);

						// clear the buffer
						Arrays.fill(buf, (byte) 0);
					}
					// EOF on client disconnect
					else if(read_count < 0) {
						info("***EOF***: "+connection.toString());
						break;
					}
				}
			}
			catch (IOException ex) { err(ex.getMessage()); }
			finally { 
				try {
					connection.close();
					info("connection closed: "+connection.toString());
				}
				catch (IOException ex) { err("close failed: "+ex.getMessage()); } 
			}

			return null;
		} 
	}

	public static void main(String[] args) {

		PORT = getIntProperty("server.port", PORT);
		SOCKET_TIMEOUT = getIntProperty("socket.timeout", SOCKET_TIMEOUT);
		POOL_THREADS = getIntProperty("pool.threads", POOL_THREADS);	

		info(String.format("server.port=%d, socket.timeout=%d, pool.threads=%d",
			PORT, SOCKET_TIMEOUT, POOL_THREADS));

		// create a pool of threads...
		ExecutorService pool = Executors.newFixedThreadPool(POOL_THREADS);

		// create server socket
		info("EchoServer listening on port "+PORT);
		try (ServerSocket server = new ServerSocket(PORT)) {
				server.setReuseAddress(true);
			while (true) {
				try {
					// wait for client connection
					Socket connection = server.accept();
					connection.setSoTimeout(SOCKET_TIMEOUT);
					connection.setReuseAddress(true);

					// create task to process client socket
					Callable<Void> task = new EchoTask(connection);

					// submit to thread pool...
					pool.submit(task);
					
					//info("task submitted");

				} catch (Exception ex) { err(ex.getMessage()); }
			} 
		} catch (IOException ex) {
			err("could not start server: "+ex.getMessage());
		}
	} 
} 
