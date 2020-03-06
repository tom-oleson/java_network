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
	public static final int SOCKET_TIMEOUT = 180000;
	public static final int POOL_THREADS = 200;

	static final char EOT = 0x04;

	static String tps_server = "localhost";
	static int tps_port = 4000;

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
		BufferedInputStream terminal_bis = null;
		BufferedOutputStream terminal_bos = null; 

		private Socket route_socket = null;
		BufferedInputStream route_bis = null;
		BufferedOutputStream route_bos = null;


		RouteTask(Socket connection) {
			this.connection = connection;
		}

		// this function will be expanded to handle multiple routes based on the request data
		public Socket getRouteSocket (byte[] data) throws IOException {
			// for now, just always point to TPS
			Socket tps_socket = new Socket (tps_server, tps_port);
			tps_socket.setSoTimeout(SOCKET_TIMEOUT);
			tps_socket.setReuseAddress(true);
			return tps_socket;
		}

		public static int readBytes(BufferedInputStream bis, byte[] buf, int sz) throws IOException {

			// read until we get data or an error...
			int num_bytes = 0;
			while(true) {
				num_bytes = bis.read(buf, 0, sz);
				if(num_bytes == -1) return -1;
				if(num_bytes > 0) break;
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

		public int routeData(byte[] data, int sz) throws IOException {

				// send copy of data to route...
				writeBytes(route_bos, data, sz);
				info("REQUEST ---> ROUTE (WRITE)");

				// get route response and send back to terminal....
				byte[] resp = new byte[2048];
				int response_sz = readBytes(route_bis, resp, resp.length);
				if(response_sz > 0) {

					// output to server console...
					info("====== ROUTE --> RESPONSE (READ)");
					System.out.print(formatHexDump(resp, 0, response_sz, 16));
					System.out.print(formatHexRecord(resp, 0, response_sz));

					// write copy of response to terminal...
					writeBytes(terminal_bos, resp, response_sz); 
					info("RESPONSE --> TERMINAL (WRITE)");
				}
				else if(response_sz == -1) {
					err("error reading route response");
				}

				return response_sz;
		}

		@Override
		public Void call() {

			info("Running thread for "+connection.toString()+" [connection timeout="+SOCKET_TIMEOUT+"]");
			try {
				// get streams to the connected client...
				terminal_bis = new BufferedInputStream(connection.getInputStream());
				terminal_bos = new BufferedOutputStream(connection.getOutputStream());

				int read_count = 0;
				byte[] data = new byte[4096];

				while(true) {
					if((read_count = readBytes(terminal_bis, data, data.length)) > 0) {	

						// output to server console
						info("====== TERMINAL --> REQUEST (READ)");
						System.out.print(formatHexDump(data, 0, read_count, 16));
						System.out.print(formatHexRecord(data, 0, read_count));						

						// if this is the first time through the loop, connect to route
						if(route_socket == null) {
							// get connection to the route server for this data...
							route_socket = getRouteSocket(data);
							route_bis = new BufferedInputStream(route_socket.getInputStream());
							route_bos = new BufferedOutputStream(route_socket.getOutputStream());
							info("Connected to route: "+route_socket.toString());							
						}

						if( routeData(data, read_count) == -1)
							break;

						// EOT from client signals end of terminal session...
						if (data.length == 1 && data[0] == EOT)
							break;

						// clear the buffer
						Arrays.fill(data, (byte) 0);
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
					if(route_socket != null) {
						route_socket.close();
						info("route socket closed: "+route_socket);
					}
				} catch (IOException ex) { err("route socket close failed: "+ex.getMessage()); }

				try {
					connection.close();
					info("connection closed: "+connection.toString());
				}
				catch (IOException ex) { err("connection close failed: "+ex.getMessage()); } 
			}

			return null;
		} 
	}
} 
