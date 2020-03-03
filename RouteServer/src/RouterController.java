package com.efx.tcc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.efx.common.logging.LogManager;

import lombok.Getter;

@RestController
@RequestMapping("/api")
public class RouterController
{
	static final long SLEEP_VALUE = 100L;
	
	public static final String ETX = "" + (char)0x03;

	static final int SOCKET_TIMEOUT = 180000;
	static final char EOT = 0x04;
	
	boolean debug = true;
	
	Logger logger = null;

	@Getter
    ExecutorService executor;
    
    @Getter
    @Value("${router.server.port:5555}")
    int serverPortNumber = 0;
	ServerSocket serverSocket;
	
	@Value("${router.legacy.port:4000}")
	int legacyServerPort;
	@Value("${router.legacy.server}")
	String legacyServerName;
	
	/*
	@Value("${router.tps.port:5557}")
	int tpsServerPort;
	@Value("${router.tps.server}")
	String tpsServerName;
	 */
    
	@PostConstruct
    public void init ()
    {
    	logger = LogManager.getInstance().getLogger(this.getClass().getName());

       	executor = Executors.newFixedThreadPool(1000);

    	if (this.serverPortNumber != 0)
    	{
        	executor.execute(() -> openServerSocket());
    	}
   }

    void openServerSocket()
    {
		logger.info("Creating Socket for host...");
		try {
			serverSocket = new ServerSocket(serverPortNumber);		// define in config file?!?!?
		} catch (IOException e) {
			logger.log(Level.INFO, "Exception: ", e);
		}
		Runtime.getRuntime().addShutdownHook(new Thread(() -> closeServerSocket()));
		
		acceptConnections(serverSocket);
    }
	
	void closeServerSocket()
	{
		try {
			serverSocket.close();
		} catch (IOException e) {
			/* nothing is needed to be logger here, we are shutting down */
		}
	}

	/**
	 * Accepts incoming connections and schedules task
	 * 
	 * @param serverSocket
	 */
	void acceptConnections(ServerSocket serverSocket) {
		try {
			logger.info("Accepting SSL Socket Connections at host on port: " + serverSocket.getLocalPort());
			while (true) {
				Socket socket = serverSocket.accept();
				logger.info("Accepted connection from" + socket.getRemoteSocketAddress());
				executor.execute(() -> onConnection(socket)); 
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE, e, () -> "Unable to instantiate a ServerSocket on port: " + serverSocket.getLocalPort());
		} catch (Exception e) {
			logger.log(Level.SEVERE, e, () -> "Unable to bind to port " + serverSocket.getLocalPort());
		}
	}

	void debugData (String label, byte[] data)
	{
		if (debug)
		{
			System.err.println(label + bytesToHex(data));
		}
	}
	
	void onConnection(Socket socket)
	{
		byte[] data = null;
		try
		{
			socket.setSoTimeout(SOCKET_TIMEOUT);
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();

			data = readData(is);
			debugData("ATM IN->APP: ", data);

			Socket tpsSocket = getRouteSocket(data);	
			
			tpsSocket.setSoTimeout(SOCKET_TIMEOUT);
			InputStream tpsIs = tpsSocket.getInputStream();
			OutputStream tpsOs = tpsSocket.getOutputStream();

			while (true)
			{
				//String request = "000000  td302       TES00001       60CE-V601.30OS-0601.08RM01.030320  0T00000000     00000001K0130000300102K040000510040000000000000000000000000000005[2";
				//tpsOs.write(request.getBytes());
				writeData(tpsOs, data);
				debugData("ATM OUT->APP: ", data);
				
				data = readData (tpsIs);
				debugData("APP IN->ATM: ", data);

				os.write(data);
				os.flush();
				debugData("APP OUT->ATM: ", data);

				if (data.length == 1 && data[0] == EOT)
				{
					closeSocket(socket, tpsSocket);
					break;
				}
				data = readData (is);
				debugData("APP IN->APP: ", data);
			}
		} catch (Exception e) {
			logger.log(Level.INFO, "Exception: ", e);
		}
	}
	
	void closeSocket(Socket socket, Socket tpsSocket)
	{
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e1) {
			// okay if terminates early
		}
		
		try
		{
			socket.close();
			tpsSocket.close();
		} catch (Exception e) { } 		
	}
	
	void writeData (OutputStream os, byte[] data) throws IOException
	{
		String checkString = new String(data);
		String[] newString = checkString.split(ETX);
		for (String part : newString)
		{
			StringBuilder sb = new StringBuilder();
			sb.append(part);
			sb.append(ETX);
			os.write(sb.toString().getBytes());
			os.flush();
			try {
				Thread.sleep(SLEEP_VALUE);
			} catch (InterruptedException e) { }
		}
	}
	
	byte[] readData(InputStream in) throws Exception
	{
		byte[] retval = null;
		byte[] bytes = new byte[5000];
		while (true)
		{
			int found = in.read(bytes);
			if (found == 0)
			{
				continue;
			} else if (found != -1) {
				retval = new byte[found];
				System.arraycopy(bytes, 0, retval, 0, found);
			}
			break;
		}
		return retval;
	}
	
	Socket getRouteSocket (byte[] data) throws Exception
	{
		// for now, just always point to the legacy TPS
		return new Socket (this.legacyServerName, this.legacyServerPort);
	}
	
	String bytesToHex(byte[] bytes)
	{
		StringBuilder hex = new StringBuilder();
		for (byte b : bytes)
		{
			int value = b & 0x00FF;
			String str = Integer.toHexString(value);
			if (str.length() == 1)
			{
				str = "0" + str;
			}
			hex.append(str);
		}
		return hex.toString();
	}
}
