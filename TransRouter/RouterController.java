package com.efx.tps;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.google.common.base.MoreObjects;

import java.security.*;
import java.security.cert.CertificateException;
import java.time.Instant;

import javax.net.ssl.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import static com.efx.tps.SocketUtils.*;
import static com.efx.tps.HexDumpUtils.*;


@RestController
@RequestMapping("/api")
@Builder
@AllArgsConstructor
public class RouterController implements CommandLineRunner {
	
	RouterController() {}	

	static final char STX = 0x02;
	static final char ETX = 0x03;
	static final char EOT = 0x04;
	static final char ACK = 0x06;
	static final char FS = 0x1c;
	
	public static final String FS_STR = "" + (char)0x1c;

	static final int ETX_DELAY = 100;
	
	Logger log = null;
	ServerSocket serverSocket = null;
	ServerSocket sslServerSocket = null;
	
	RouteConfiguration routeConfiguration = null;

	@Getter
	ExecutorService pool = null;
	
	@Getter
    @Value("${router.port:4000}")
    int serverPort = -1;

    @Getter
    @Value("${router.ssl.port:19076}")
    int sslServerPort = -1;
   
    @Getter
    @Value("${router.config:terminal-routes.conf}")
    String terminalRoutesPath;
   
   	@Getter
	@Value("${socket.timeout:180000}")
    int socketTimeout = -1;
   	
	@Getter
	@Setter
	@Value("${socket.connect.wait:180000}")
	int socketConnectWait = -1;	   	
   	
   	@Getter
   	@Value("${socket.close.delay:1000}")
   	int socketCloseDelay = -1;
	
	@Getter
	@Value("${pool.threads:200}")
	int poolThreads = -1;
	
	@Getter
	@Setter
	@Value("${runtime.sleep:10000}")
	int runTimeSleep = -1;
	
	@Getter
	@Setter
	@Value("${runtime.duration:0}")
	int runTimeDuration = -1;	

	@Getter
	@Setter
	@Value("${router.debug:false}")
	boolean debug;			// enables data hex dump to log output

	@Setter
	Consumer<Object> initConsumer;

	@PostConstruct
    public void init() {	
    	log = LoggerFactory.getLogger(this.getClass().getName());
    	
    	// allow external test case config to be inject here otherwise do nothing
		initConsumer = MoreObjects.firstNonNull(initConsumer, (o)->{});
		initConsumer.accept(null);
		log.info("init: consumer accept completed");

    	log.info( String.format("router.port=%d, router.ssl.port=%d, socket.timeout=%d, socket.close.delay=%d, pool.threads=%d, router.debug=%b",
				serverPort, sslServerPort, socketTimeout, socketCloseDelay, poolThreads, debug ));
    	
		// create a pool of threads...
		pool = Executors.newFixedThreadPool(poolThreads);
		process();
  	
    }

	@Override
	public void run(String... args) { init() ;}
		
	public void process() {   	
    	
		Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown()));
		
		log.info(String.format("terminal routes path: %s", terminalRoutesPath));
		
		routeConfiguration = RouteConfiguration.builder().path(terminalRoutesPath).build();
		routeConfiguration.load();		
		
		log.info("Starting server threads...");

		if(serverPort != -1) pool.submit(() -> {

			// create server socket
			try (ServerSocket server = new ServerSocket(serverPort) ) {
				log.info("TransactionRouter listening on port "+serverPort);
				server.setReuseAddress(true);
				while (!server.isClosed()) {
					try {
						// wait for client connection...
						Socket connection = server.accept();
						connection.setSoTimeout(socketTimeout);
						connection.setReuseAddress(true);

						// save for shutdown...
						serverSocket = server;

						// create task to process client socket
						Callable<Void> task = new RouteTask(connection);

						// submit to thread pool...
						pool.submit(task);

					} catch (Exception ex) { log.error(ex.getMessage()); }
				} 
			} catch (IOException ex) {
				log.error("could not start server: "+ex.getMessage());
			}
		});

		if(sslServerPort != -1) pool.submit(() -> {

			SSLContext ctx = null;
			SSLServerSocketFactory ssf = null;

			String ks_password = "changeit";
			String ks_path = "./combined.jks";

			ks_path = System.getProperty("javax.net.ssl.keyStore", ks_path);
			ks_password = System.getProperty("javax.net.ssl.keyStorePassword", ks_password);

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
					log.error(ex.getMessage());
					System.exit(-1);
			}

			// create SSL server socket
			try ( SSLServerSocket server = (SSLServerSocket) ssf.createServerSocket(sslServerPort) ) {
				log.info("TransactionRouter listening on port "+sslServerPort+" (SSL)");
				server.setReuseAddress(true);
				while (!server.isClosed()) {
					try {
						// wait for client connection...
						Socket connection = server.accept();
						connection.setSoTimeout(socketTimeout);
						connection.setReuseAddress(true);

						// save for shutdown...
						sslServerSocket = server;
						
						// create task to process client socket
						Callable<Void> task = new RouteTask(connection);

						// submit to thread pool...
						pool.submit(task);

					} catch (Exception ex) { 
						log.error(ex.getMessage());
						System.exit(-1);
					}
				} 
			} catch (IOException ex) {
				log.error("could not start SSL server: "+ex.getMessage());
			}
		});

		long endTimeMillis = Instant.now().toEpochMilli() + runTimeDuration;
		log.info("Entering runtime loop...");
		do {
			sleep(runTimeSleep);
			if(runTimeDuration > 0) {
				long now = Instant.now().toEpochMilli();
				if(now >= endTimeMillis) {
					log.info("Runtime has expired, calling shutdown");
					shutdown();
					break;
				}
			}
		} while (true);

    }
    
	void shutdown() {

		if(sslServerSocket != null) {
			try {
				sslServerSocket.close();
				sslServerSocket = null;
				log.info("SSL server socket closed");
			} catch (IOException e) {
				// nothing is needed to be logger here, we are shutting down
			}
		}

		if(serverSocket != null) {
			try {
				serverSocket.close();
				serverSocket = null;
				log.info("server socket closed");
			} catch (IOException e) {
				// nothing is needed to be logger here, we are shutting down
			}
		}
	}

	
	private class RouteTask implements Callable<Void> {

		Socket connection = null;
		BufferedInputStream terminal_bis = null;
		BufferedOutputStream terminal_bos = null; 

		RouteTask(Socket connection) {
			this.connection = connection;
		}
		
		public String[] asStringArray(byte[] data) {
			String request = new String(data);
			return request.split(FS_STR);
		}
		
		public String terminalID(byte[] data) {
			String[] dataAsArray = asStringArray(data);
			// Standard 1 (Nautilus Hyosung) and Standard 3 (Triton):
			// Terminal ID is always in Request Message field 2 
			String terminalID = dataAsArray.length < 2 ? "" : dataAsArray[1];  
			if(terminalID.length() == 0) {
				log.warn("No terminalID present in request");
			} else {
				log.info(String.format("terminalID=[%s]", terminalID));
			}			
			return terminalID;
		}
		
		public Socket getSocket (String tpsServer, int tpsPort) throws IOException {
			Socket socket = new Socket (tpsServer, tpsPort);
			socket.setSoTimeout(socketTimeout);
			socket.setReuseAddress(true);
			return socket;
		}

		
		public byte computeLRC(byte[] bytes) 	{
			 byte lrc = 0x00;
		 	// STX + bytes + ETX + LRC
		 	// compute each byte after STX up to and including ETX
		 	// be careful not to include anything beyond the ETX!
		    for (int i = 1; i < bytes.length-1; i++) {
		    	lrc ^= (bytes[i] & 0xff);
		    	if(bytes[i] == ETX) break;
		    }
		    return lrc;
		}				
		
		public void writeBytes(BufferedOutputStream bos, byte[] buf, int sz) throws IOException {

			byte b;
			int etx_count = 0, fs_count = 0;
			byte lrc = 0x00;
			
			// buffer each byte, flush and delay for each ETX
			for(int i = 0; i < sz; i++) {
				b = buf[i];
				bos.write(b);
				
				if(b == ETX) {
					bos.flush();
					sleep(ETX_DELAY);
					etx_count++;		// count ETX bytes
					
					// store received LRC byte
					if(i+1 < sz) lrc = buf[i+1];
				}
				else if(b == FS) {
					fs_count++;			// count fields
				}
			}

			// flush accumulated (if any)
			bos.flush();
			
			if(debug) {
				if(fs_count > 0) {
					log.debug(String.format("Counted %d fields", fs_count));
				}

				if(etx_count > 0) {
					log.debug(String.format("Counted %d ETX bytes (delay %d ms each)", etx_count, ETX_DELAY));
				}
			}
			
			if(etx_count == 1) {
				byte calcLRC = computeLRC(buf);
				if(calcLRC != lrc) {
					log.warn(String.format("LRC byte is %02x, expected %02x",  lrc, calcLRC));
				}
			}
		}
		
		
		public int routeData(byte[] data, int sz) {

			List<Target> targets = routeConfiguration.getRouteTargets(terminalID(data));
			Target server_target = null;
			
			// run non-server targets on separate threads...
			for(Target target: targets) {
				if(!target.mode.equals("S")) {
					pool.submit(() -> {
						routeToTarget(target, data, sz);
					});
				} else {
					server_target = target;
				}
			}

			
			if(server_target != null) {
				return routeToTarget(server_target, data, sz);
			}
			
			log.error("No server target found!");;
			
			return -1;
			
		}
		
		public int routeToTarget(Target target, byte[] data, int sz) { 
			Socket socket = null;
			int response_count = 0;
			
			try {
				log.info("route target: "+target.toString());
				socket = getSocket(target.host, target.port);
				log.info("Connected to route: "+socket.toString());
			} catch(IOException ex) {
				log.error("faild on getSocket: "+ex.getMessage());
			}
			
			
			try {
				response_count = routeData(data, sz, socket, target.mode.equals("S"), target.name);
				
			} catch(IOException ex) {
				log.error("failed on routeData: "+ex.getMessage());
			}
			
			
			finally { 
				// allow time for socket buffers to empty...
				sleep(socketCloseDelay);
				try {
					if(socket != null) {
						if(!(socket instanceof SSLSocket))
							socket.shutdownOutput();	// TCP FIN/ACK (done sending)
						socket.close();
						log.info("route socket closed: "+socket);
					}
				} catch (IOException ex) { log.error("route socket close failed: "+ex.getMessage()); }
			}
			
			return response_count;			
			
		}		

		public int routeData(byte[] data, int sz, Socket socket, boolean responder, String name) throws IOException {			

			BufferedInputStream route_bis = new BufferedInputStream(socket.getInputStream());
			BufferedOutputStream route_bos =  new BufferedOutputStream(socket.getOutputStream());
			
			// send copy of data to route...
			writeBytes(route_bos, data, sz);
			log.info(String.format("REQUEST ---> ROUTE %s (WRITE) %d bytes", name, sz));

			// get route response and send back to terminal....
			int response_count = 0;
			byte[] resp = new byte[2048];
			
			while(true) {
				
				if((response_count = readBytes(route_bis, resp, resp.length)) > 0) {
					
					byte handshake = resp[response_count-1];

					// output to server console...
					log.info(String.format("ROUTE %s --> RESPONSE (READ) %d bytes", name, response_count));
					
					if(debug) { 
						log.info(formatHexRecord(resp, 0, response_count));
						System.out.print(formatHexDump(resp, 0, response_count, 16));
					}

					if(responder) {
						// write copy of response to terminal...
						writeBytes(terminal_bos, resp, response_count); 
						log.info(String.format("RESPONSE --> TERMINAL (WRITE) %d bytes", response_count));
					}

					// ACK from earlier EOT...
					if(handshake == ACK) {
						log.info("ROUTE --> *ACK* RESPONSE");
						break;
					}						
					
					// EOT from route signals end of route session...							
					if(handshake == EOT) {
						log.info("ROUTE --> *EOT*");
						break;
					}
					
				}
				else if(response_count < 0) {
					log.info("*EOF* reading route response: "+socket.toString());
					break;
				}
			}
			return response_count;
		}

		@Override
		public Void call() {

			log.info("TERMINAL --> INBOUND CONNECTION: "+connection.toString()+" [connection timeout="+socketTimeout+"]");
			
			try {
				// get streams to the connected client...
				terminal_bis = new BufferedInputStream(connection.getInputStream());
				terminal_bos = new BufferedOutputStream(connection.getOutputStream());

				int read_count = 0;
				byte[] data = new byte[4096];

				while(true) {
					if((read_count = readBytes(terminal_bis, data, data.length)) > 0) {	

						byte handshake = data[read_count-1];	
						
						// output to server console
						log.info(String.format("TERMINAL --> REQUEST (READ) %d bytes", read_count));
						if(debug) { 
							log.info(formatHexRecord(data, 0, read_count));
							System.out.print(formatHexDump(data, 0, read_count, 16));
						}

						// ACK from earlier EOT...
						if(handshake == ACK) {
							log.info("TERMINAL --> *ACK* RESPONSE");
							break;
						}
						
						if(routeData(data, read_count) == -1)
							break;
						
						// EOT from terminal signals end of session...							
						if(handshake == EOT) {
							log.info("TERMINAL --> *EOT*");
							break;
						}

						// clear the buffer
						Arrays.fill(data, (byte) 0);
					}
					// EOF on client disconnect
					else if(read_count < 0) {
						log.info("*EOF* reading from terminal: "+connection.toString());
						break;
					}
				}
			}
			catch (IOException ex) { log.error(ex.getMessage()); }
			finally { 

				// allow time for socket buffers to empty...
				sleep(socketCloseDelay);

				try {
					if(!(connection instanceof SSLSocket))
						connection.shutdownOutput();  // TCP FIN/ACK (done sending)
					connection.close();
					log.info("terminal connection closed: "+connection.toString());
				}
				catch (IOException ex) { log.error("terminal connection close failed: "+ex.getMessage()); } 
			}

			return null;
		} 
	}
}
