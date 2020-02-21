
import java.io.*;
import java.net.*;

public class LocalPortScanner {
	public static void main(String[] args) {
		for (int port = 1; port <= 65535; port++) {
			try {
			// the next line will fail and drop into the catch block if
			// the port is already in use.
				ServerSocket server = new ServerSocket(port);
				server.close();
			} catch (IOException ex) {

				if(ex.getMessage().startsWith("Permission denied")) {
					// supress message
				}
				else if(ex.getMessage().startsWith("Address already in use")) {
					System.out.println("Port " + port + " is in use");
				}

				else {
					System.out.println("Port " + port + ": "+ex.getMessage());
				}
			}
		}
	}
}