import java.net.*;
import java.io.*;
import java.util.Date;

public class DaytimeServer {
	public final static int PORT = 1313;
	public static void main(String[] args) {
		// server is autoclosed if there is a bind exception...
		try (ServerSocket server = new ServerSocket(PORT) ) {
			System.out.println("DaytimeServer listening on port "+PORT);
			while (true) {
				try (Socket connection = server.accept()) {
					Writer out = new OutputStreamWriter(connection.getOutputStream());
					Date now = new Date();
					out.write(now.toString() +"\r\n");
					out.flush();
					connection.close();
				} catch (IOException ex) {}
			}
		} catch (IOException ex) {
			System.err.println(ex);
		}
	}
}