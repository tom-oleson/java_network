import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class EchoServer {


	public final static int PORT = 4000;
	public static final long SLEEP_VALUE = 100L;
	public static final String ETX = "" + (char)0x03;
	public static final int SOCKET_TIMEOUT = 180000;
	public static final char EOT = 0x04;


	public static void main(String[] args) {

		// create a pool of 50 threads...
		ExecutorService pool = Executors.newFixedThreadPool(50);

		// create server socket
		System.out.println("EchoServer listening on port "+PORT);
		try (ServerSocket server = new ServerSocket(PORT)) {
			while (true) {
				try {
					// wait for client connection
					Socket connection = server.accept();
					connection.setSoTimeout(SOCKET_TIMEOUT);

					// create task to process client
					Callable<Void> task = new EchoTask(connection);

					// submit to thread pool...
					pool.submit(task);

				} catch (IOException ex) {}
			} // end while
		} catch (IOException ex) {
			System.err.println("Couldn't start server");
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

			System.out.println("Running thread for "+connection);
			try {

			
				InputStream is = connection.getInputStream();
				OutputStream os = connection.getOutputStream();

				String hello = "EchoServerHello\r\n";
						os.write(hello.getBytes());
						os.flush();

				int read_count;
				byte[] buf = new byte[2048];


				do {
					read_count = is.read(buf);

					if(read_count == 0) continue;

					if(read_count > 0) {
						// echo to client
						os.write(buf);
						os.flush();
					}

					if(read_count < 0) {
						break;
					}

				} while(true);

			} catch (IOException ex) {
				System.err.println(ex);
			} finally { 
				try {
				 connection.close();
				 }  catch (IOException e) {} 
			}

			return null;
		} 
	}
} 
