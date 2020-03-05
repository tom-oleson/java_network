import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

// test: 
// $ javac RouteServer.java
// $ java RouteServer
// $ nc localhost 9000

public class RouteServer {

	public static final int PORT = 9000;
	public static final int SOCKET_TIMEOUT = 180000;	// 180 seconds
	public static final int POOL_THREADS = 200;


	static String tps_server = "localhost";
	static int tps_port = 4000;
	static Socket tps_socket = null;

	// this function will be expanded to handle multiple routes based on the request data
	public static Socket getRouteSocket (byte[] data) throws IOException {
		// for now, just always point to TPS
		if(tps_socket == null) {
			tps_socket = new Socket (tps_server, tps_port);
			tps_socket.setSoTimeout(SOCKET_TIMEOUT);
			tps_socket.setReuseAddress(true);
		}
		return tps_socket;
	}

	public void closeRouteSocket(Socket s) {
		
		try { s.close(); } catch(IOException ex) { err("failed to close route socket"); }
		if(s == tps_socket) {
			tps_socket = null;
		}
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
	
	
	private static class RouteTask implements Callable<Void> {

		private Socket connection = null;
		private Socket route_socket = null;
	
		RouteTask(Socket connection) {
			this.connection = connection;
			this.route_socket = route_socket;
		}

		public static int readBytes(BufferedInputStream bis, byte[] buf) throws IOException {

			// read until we get data or an error...
			int num_bytes = 0;
			while(num_bytes == 0) {
				num_bytes = bis.read(buf);
				if(num_bytes == -1) return -1;
			} 
			return num_bytes;
		}

		public static void writeBytes(BufferedOutputStream bos, byte[] buf, int sz) throws IOException {
			bos.write(buf, 0, sz);
			bos.flush();
		}


		public static void sleep(int millis) {
			try { Thread.sleep(millis); } catch (InterruptedException e1) {	}
		}

		public static int readBytesWithTimeout(BufferedInputStream bis, byte[] buf, int timeout)  throws IOException  {
		    long max_time = System.currentTimeMillis() + timeout;
		    int num_bytes = 0;
		    while (System.currentTimeMillis() < max_time && num_bytes == 0) {
		    	if(bis.available() > 0) {
			        num_bytes = readBytes(bis, buf);	
			        if (num_bytes == -1) break;
		    	}

		    	sleep(1000);

		    }
     		return num_bytes;
 		}


		public void routeData(BufferedOutputStream terminal_bos, byte[] data, int sz) {

				// output to server console
				System.out.println("====== TERM --> REQUEST --> ROUTE");
				System.out.print(formatHexDump(data, 0, sz, 16));
				System.out.print(formatHexRecord(data, 0, sz));

				try {
					// get connection to the route server for this data and send it a copy of the data...
					Socket route_socket = RouteServer.getRouteSocket(data);
					BufferedInputStream route_bis = new BufferedInputStream(route_socket.getInputStream());
					BufferedOutputStream route_bos = new BufferedOutputStream(route_socket.getOutputStream());
					writeBytes(route_bos, data, sz);


					// get route response and send back to terminal....
					byte[] response = new byte[2048];
					int response_sz = readBytesWithTimeout(route_bis, response, 6000);
					System.out.println("response_sz = "+response_sz);
					if(response_sz > 0) {

						// output to server console
						System.out.println("====== TERM <-- RESPONSE <-- ROUTE");
						System.out.print(formatHexDump(response, 0, response_sz, 16));
						System.out.print(formatHexRecord(response, 0, response_sz));

						writeBytes(terminal_bos, response, response_sz); 
					}



				} catch(IOException ex) {
					err("error in routeData:" + ex.getMessage());
				}
		}

		@Override
		public Void call() {

			info("Running thread for "+connection.toString()+" [connection timeout="+SOCKET_TIMEOUT+"]");
			try {
				// get streams to the connected client...
				BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
				BufferedOutputStream bos = new BufferedOutputStream(connection.getOutputStream());

				// String hello = "RouteServerHello\r\n";
				// bos.write(hello.getBytes());
				// bos.flush();

				int read_count = 0;
				byte[] data = new byte[4096];

				do {
					if((read_count = readBytes(bis, data)) > 0) {				
						routeData(bos, data, read_count);

						// clear the buffer
						Arrays.fill(data, (byte) 0);
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
