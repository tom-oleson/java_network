import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

// test: 
// $ javac SSLRouteServer.java
// $ java SSLRouteServer
// $ nc localhost 9000

public class SSLRouteServer {

	public static int PORT = 9000;
	public static int SSL_PORT = 4000;
	public static int SOCKET_TIMEOUT = 180000;
	public static int POOL_THREADS = 200;

	static final char EOT = 0x04;

	static ServerSocket serverSocket;
	static ServerSocket sslServerSocket;

	static String tps_server = "localhost";
	static int tps_port = 4001;

	static boolean run = true;

	public static int getIntProperty(String key, int def) {
		String value = System.getProperty(key);
		if(value != null) {
			return Integer.parseInt(value);
		}
		return def;
	}

	public static void sleep(int millis) {
		try { Thread.sleep(millis); } catch (InterruptedException ex) {	}
	}

	public static void main(String[] args) {

		PORT = getIntProperty("server.port", PORT);
		SSL_PORT = getIntProperty("server.ssl.port", SSL_PORT);
		SOCKET_TIMEOUT = getIntProperty("socket.timeout", SOCKET_TIMEOUT);
		POOL_THREADS = getIntProperty("pool.threads", POOL_THREADS);

		tps_server = System.getProperty("tps.server", tps_server);
		tps_port = getIntProperty("tps.port", tps_port);

		info(String.format("server.port=%d, socket.timeout=%d, pool.threads=%d, tps.server=%s, tps.port=%d",
			PORT, SOCKET_TIMEOUT, POOL_THREADS, tps_server, tps_port));

		// create a pool of threads...
		ExecutorService pool = Executors.newFixedThreadPool(POOL_THREADS);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown()));

		Thread server_thread = new Thread( () -> {
			// create SSL server socket
			try (ServerSocket server = new ServerSocket(PORT) ) {
				info("started TransactionRouter listening on port "+PORT);
				server.setReuseAddress(true);
				while (run && !server.isClosed()) {
					try {
						// wait for client connection...
						Socket connection = server.accept();
						connection.setSoTimeout(SOCKET_TIMEOUT);
						connection.setReuseAddress(true);

						// save for shutdown...
						serverSocket = server;

						// create task to process client socket
						Callable<Void> task = new RouteTask(connection);

						// submit to thread pool...
						pool.submit(task);

					} catch (Exception ex) { err(ex.getMessage()); }
				} 
			} catch (IOException ex) {
				err("could not start server: "+ex.getMessage());
			}
			run = false;
		});


		Thread ssl_server_thread = new Thread( () -> {

			SSLContext ctx = null;
			SSLServerSocketFactory ssf = null;

			String ks_password = "changeit";
			String ks_path = "./combined.jks";

			System.getProperty("javax.net.ssl.keyStore", ks_path);
			System.getProperty("javax.net.ssl.keyStorePassword", ks_password);


			try {
				char[] password = ks_password.toCharArray();

				KeyStore ks = KeyStore.getInstance("JKS");
				ks.load(new FileInputStream(ks_path), password);
				KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
				kmf.init(ks, password);
				ctx = SSLContext.getInstance("TLSv1.2");
				ctx.init(kmf.getKeyManagers(), null, null);
				ssf = ctx.getServerSocketFactory();

			} catch (IOException | KeyStoreException | KeyManagementException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException ex) {
					err(ex.getMessage());
					run = false;
					System.exit(-1);
			}

			// create SSL server socket
			try ( SSLServerSocket server = (SSLServerSocket) ssf.createServerSocket(SSL_PORT) ) {
				info("started SSL TransactionRouter listening on port "+SSL_PORT);
				server.setReuseAddress(true);
				while (run && !server.isClosed()) {
					try {
						// wait for client connection...
						Socket connection = server.accept();
						connection.setSoTimeout(SOCKET_TIMEOUT);
						connection.setReuseAddress(true);

						// save for shutdown...
						sslServerSocket = server;
						
						// create task to process client socket
						Callable<Void> task = new RouteTask(connection);

						// submit to thread pool...
						pool.submit(task);

					} catch (Exception ex) { err(ex.getMessage()); }
				} 
			} catch (IOException ex) {
				err("could not start SSL server: "+ex.getMessage());
			}
			run = false;
		});

		server_thread.start();
		ssl_server_thread.start();


		while(run) {

			// wait a bit before we check status again...
			sleep(10000);
		}
	} 

	static void shutdown() {
		if(sslServerSocket != null) {
			try {
				sslServerSocket.close();
				sslServerSocket = null;
				info("SSL server socket closed");
			} catch (IOException e) {
				// nothing is needed to be logger here, we are shutting down
			}
		}

		if(serverSocket != null) {
			try {
				serverSocket.close();
				serverSocket = null;
				info("server socket closed");
			} catch (IOException e) {
				// nothing is needed to be logger here, we are shutting down
			}
		}

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

		long tid;	// thread id

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

		public int routeData(byte[] data, int sz) throws IOException {

				// send copy of data to route...
				writeBytes(route_bos, data, sz);
				info("| REQUEST ---> ROUTE (WRITE)");

				// get route response and send back to terminal....
				int response_count = 0;
				byte[] resp = new byte[2048];
				if((response_count = readBytes(route_bis, resp, resp.length)) > 0) {

					// output to server console...
					info("| ROUTE --> RESPONSE (READ)");
					System.out.print(formatHexDump(resp, 0, response_count, 16));
					//System.out.print(formatHexRecord(resp, 0, response_count));

					// write copy of response to terminal...
					writeBytes(terminal_bos, resp, response_count); 
					info("| RESPONSE --> TERMINAL (WRITE)");
				}
				else if(response_count < 0) {
					err("*EOF* reading route response: "+route_socket.toString());
				}

				return response_count;
		}

		@Override
		public Void call() {

			info("Incoming connection: "+connection.toString()+" [connection timeout="+SOCKET_TIMEOUT+"]");
			
			try {
				// get streams to the connected client...
				terminal_bis = new BufferedInputStream(connection.getInputStream());
				terminal_bos = new BufferedOutputStream(connection.getOutputStream());

				int read_count = 0;
				byte[] data = new byte[4096];

				while(true) {
					if((read_count = readBytes(terminal_bis, data, data.length)) > 0) {	


						// output to server console
						info("======");
						info("| TERMINAL --> REQUEST (READ)");
						System.out.print(formatHexDump(data, 0, read_count, 16));
						//System.out.print(formatHexRecord(data, 0, read_count));						

						// if this is the first time through the loop, connect to route
						if(route_socket == null) {
							// get connection to the route server for this data...
							route_socket = getRouteSocket(data);
							route_bis = new BufferedInputStream(route_socket.getInputStream());
							route_bos = new BufferedOutputStream(route_socket.getOutputStream());
							info("Connected to route: "+route_socket.toString());							
						}

						if(routeData(data, read_count) == -1)
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

				// allow time for socket buffers to empty...
				sleep(1000);

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
