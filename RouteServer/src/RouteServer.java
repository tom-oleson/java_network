import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

// test: 
// $ javac RouteServer.java
// $ java RouteServer
// $ nc localhost 9000

public class RouteServer {

	public final static int PORT = 9000;
	public static final int SOCKET_TIMEOUT = 180000;	// 180 seconds
	public static final int POOL_THREADS = 200;

	public static void info(String s) {
		System.out.println("info: "+s);
	}

	public static void err(String s) {
		System.err.println("error: "+s);
	}

	public static String formatHexRecord(byte[] bytes, int offset, int sz) {
		StringBuilder builder = new StringBuilder();

		for(int index = offset; index < sz; index++) {
			int value = bytes[index] & 0x00FF;
			builder.append(String.format("%02x ", value));
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

	Socket getRouteSocket (byte[] data) throws Exception {
			// for now, just always point to the legacy TPS
			return new Socket (TPS_SERVER, TPS_PORT);
	}

	public static void main(String[] args) {

		// create a pool of threads...
		ExecutorService pool = Executors.newFixedThreadPool(POOL_THREADS);

		// create server socket
		try (ServerSocket server = new ServerSocket(PORT)) {
			info("RouteServer listening on port "+PORT);
			server.setReuseAddress(true);
			while (true) {
				try {
					// wait for client connection
					Socket connection = server.accept();
					connection.setSoTimeout(SOCKET_TIMEOUT);
					connection.setReuseAddress(true);

					// create task to process client socket
					Callable<Void> task = new RouteTask(connection);

					// submit to thread pool...
					pool.submit(task);

				} catch (Exception ex) { err(ex.getMessage()); }
			} 
		} catch (IOException ex) {
			err("could not start server: "+ex.getMessage());
		}
	} 
	
	private static class RouteTask implements Callable<Void> {

		private Socket connection = null;
		private Socket route_socket = null;
	
		RouteTask(Socket connection, Socket route_socket) {
			this.connection = connection;
			this.route_socket = route_socket;
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
			System.out.println("======");
			System.out.print(formatHexDump(buf, 0, sz, 16));
			System.out.print(formatHexRecord(buf, 0, sz));


			// send data to all routes
			routeBytes(buf, sz);
		}


		public void routeBytes(byte[] buf, int sz) throws IOException {

		}



		@Override
		public Void call() {

			info("Running thread for "+connection.toString()+" [connection timeout="+SOCKET_TIMEOUT+"]");
			try {
				BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
				BufferedOutputStream bos = new BufferedOutputStream(connection.getOutputStream());

				String hello = "RouteServerHello\r\n";
				bos.write(hello.getBytes());
				bos.flush();

				int read_count = 0;
				byte[] buf = new byte[4096];

				do {
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

				} while(true);
			
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
} 
