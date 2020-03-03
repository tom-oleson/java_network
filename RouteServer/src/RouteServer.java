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
	public static final int SOCKET_TIMEOUT = 30000;
	public static final int POOL_THREADS = 200;

	public static void info(String s) {
		System.out.println("info: "+s);
	}

	public static void err(String s) {
		System.err.println("error: "+s);
	}

	public static String bytesToHex(byte[] bytes) {
		StringBuilder hex = new StringBuilder();
		for (byte b : bytes) {
			int value = b & 0x00FF;
			String str = Integer.toHexString(value);
			if (str.length() == 1)
				hex.append('0');
			hex.append(str);
			hex.append(' ');
		}
		return hex.toString();
	}

	public static void main(String[] args) {

		// create a pool of threads...
		ExecutorService pool = Executors.newFixedThreadPool(POOL_THREADS);

		// create server socket
		info("RouteServer listening on port "+PORT);
		try (ServerSocket server = new ServerSocket(PORT)) {
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
					
					//info("task submitted");

				} catch (Exception ex) { err(ex.getMessage()); }
			} 
		} catch (IOException ex) {
			err("could not start server: "+ex.getMessage());
		}
	} 
	
	private static class RouteTask implements Callable<Void> {

		private Socket connection;
	
		RouteTask(Socket connection) {
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

		public void processBytes(BufferedOutputStream bos, byte[] buf) throws IOException {

			// output to server console
			System.out.print(bytesToHex(buf));
			System.out.write(buf);

			// echo to client stream
			bos.write(buf);
			bos.flush();
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
				byte[] buf = new byte[2048];

				do {
					if((read_count = readBytes(bis, buf)) > 0) {				
						processBytes(bos, buf);

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
