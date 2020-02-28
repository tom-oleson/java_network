import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class EchoServer {

	public final static int PORT = 4000;

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
				Writer out = new OutputStreamWriter(connection.getOutputStream());
				do {
						Date now = new Date();
						out.write(now.toString() +"\r\n");
						out.flush();
				} while(false);
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
