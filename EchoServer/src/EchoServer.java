import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;


// test: 
// $ java EchoServer&
// $ nc localhost 4000

public class EchoServer {


	public final static int PORT = 4000;
	public static final long SLEEP_VALUE = 100L;
	public static final String ETX = "" + (char)0x03;
	//public static final int SOCKET_TIMEOUT = 180000;
	public static final int SOCKET_TIMEOUT = 5000;
	public static final int POOL_THREADS = 2;
	public static final char EOT = 0x04;


	public static void info(String s) {
		System.out.println("info: "+s);
	}

	public static void err(String s) {
		System.err.println("error: "+s);
	}


	public static void main(String[] args) {

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

					// create task to process client
					Callable<Void> task = new EchoTask(connection);

					// submit to thread pool...
					pool.submit(task);
					info("task submitted");

				} catch (Exception ex) { err(ex.getMessage()); }
			} // end while
		} catch (IOException ex) {
			err("Couldn't start server");
		}

	} // end main
	

	// private inner class
	private static class EchoTask implements Callable<Void> {

		private Socket connection;
	
		EchoTask(Socket connection) {
			this.connection = connection;
		}

		@Override
		public Void call() {

			String connString = connection.toString();

			info("Running thread for "+connString+" [connection timeout="+SOCKET_TIMEOUT+"]");
			try {
			
				InputStream is = connection.getInputStream();
				OutputStream os = connection.getOutputStream();

				BufferedInputStream bis = new BufferedInputStream(is);
				BufferedOutputStream bos = new BufferedOutputStream(os);

				String hello = "EchoServerHello\r\n";
				os.write(hello.getBytes());
				os.flush();

				int read_count = 0;
				byte[] buf = new byte[2048];

				do {
					try {
						read_count = is.read(buf);
					} catch(SocketTimeoutException ex) {
						// continue loop read loop
						continue;
					}						
					
					if(read_count == 0) {
						info("read_count zero");
						continue;
					}

					if(read_count > 0) {

						// server log input
						System.out.write(buf);

						// echo to client
						os.write(buf);
						os.flush();

						// clear the buffer
						Arrays.fill(buf, (byte) 0);
					}

					// EOF on client disconnect
					if(read_count < 0) {
						info("*EOF* on "+connString);
						break;
					}

				} while(true);
			
			}
			catch (IOException ex) { err(ex.getMessage()); }
			finally { 
				try {
					connection.close();
					info("connection closed for "+connString);
				}
				catch (IOException ex) {} 
			}

			return null;
		} 
	}
} 
