package com.efx.tps;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import lombok.Builder;

@DisplayName("TestRouterController")
public class TestTransactionRouterController {

	static Logger log = LoggerFactory.getLogger(TestTransactionRouterController.class);

	static ExecutorService pool = Executors.newFixedThreadPool(20);

	@BeforeAll
	static void beforeAll() {

		log.info("beforeAll running");

		ControllerConfiguration configuration = ControllerConfiguration
				.builder().serverHost("localhost")
				.serverPort(6200)
				.socketTimeout(10000)
				.socketCloseDelay(1000)
				.socketConnectWait(10000)
				.legacyServer("localhost")
				.legacyServerPort(4001)
				.nextGenServer("localhost")
				.nextGenServerPort(4002)
				.log(LoggerFactory.getLogger(ControllerConfiguration.class))
				.build();

		pool.submit(() -> {

			RouterController controller = RouterController.builder()
					.initConsumer(configuration.initConsumer())
					.serverPort(6200)
					.sslServerPort(-1)
					.socketTimeout(10000)
					.socketConnectWait(10000)
					.socketCloseDelay(1000)
					.terminalRoutesPath("terminal-routes.conf")
					.poolThreads(200)
					.runTimeDuration(30000)
					.debug(false)
					.build();

			log.info("" + controller);

			// init - starts new communication threads
			controller.init();
		});

	}

	@Test
	@DisplayName("Running")
	void testRunning() {
		SocketUtils.sleep(60000);
	}

	@Builder
	public static class ControllerConfiguration {

		Logger log = LoggerFactory.getLogger(ControllerConfiguration.class);

		Socket routerSocket = null;
		Socket legacySocket = null;
		Socket nextGenSocket = null;

		ServerSocket legacyServerSocket = null;
		ServerSocket nextGenServerSocket = null;

		@Value("${router.host:localhost}")
		String serverHost = null;

		@Value("${router.port:6200}")
		private int serverPort = -1;

		@Value("${socket.timeout:180000}")
		private int socketTimeout = -1;

		@Value("${socket.close.delay:1000}")
		private int socketCloseDelay = -1;

		@Value("${socket.connect.wait:180000}")
		private int socketConnectWait = -1;

		@Value("${legacy.server:localhost}")
		private String legacyServer;

		@Value("${legacy.port:4001}")
		private int legacyServerPort;

		@Value("${nextgen.server:localhost}")
		private String nextGenServer;

		@Value("${nextgen.port:4002}")
		private int nextGenServerPort;

		@Bean("Consumer")
		public Consumer<Object> consumer() {
			return initConsumer();
		}

		public Consumer<Object> initConsumer() {

			log.info("Initializing for test...");

			// create a cleanup thread for sockets we create in this test
			Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown()));

			// create mock legacy tps
			pool.submit(() -> {

				// create server socket
				try (ServerSocket server = new ServerSocket(legacyServerPort)) {
					log.info("MOCK LEGACY TPS listening on port "
							+ legacyServerPort);
					server.setReuseAddress(true);

					// save for shutdown...
					legacyServerSocket = server;

					while (!server.isClosed()) {
						try {
							// wait for client connection...
							Socket socket = server.accept();
							socket.setSoTimeout(socketTimeout);
							socket.setReuseAddress(true);

							log.info("MOCK LEGACY TPS --> INBOUND CONNECTION: "
									+ socket.toString()
									+ " [socket timeout=" + socketTimeout
									+ "]");

							legacySocket = socket;

							MockTPSConnection connection = new MockTPSConnection(
									socket, "LEGACY");
							SocketUtils.sleep(1000);
							connection.process_pulse_responses();

						} catch (Exception ex) {
							log.error(ex.getMessage());
						}
					}
				} catch (IOException ex) {
					log.error("could not start mock legacy server: "
							+ ex.getMessage());
				}

			});

			// create mock nextgen tps
			pool.submit(() -> {

				// create server socket
				try (ServerSocket server = new ServerSocket(
						nextGenServerPort)) {
					log.info("MOCK NEXTGEN TPS listening on port "
							+ nextGenServerPort);
					server.setReuseAddress(true);

					// save for shutdown...
					nextGenServerSocket = server;

					while (!server.isClosed()) {
						try {
							// wait for client connection...
							Socket socket = server.accept();
							socket.setSoTimeout(socketTimeout);
							socket.setReuseAddress(true);

							log.info("NEXTGEN TPS --> INBOUND CONNECTION: "
									+ socket.toString() + " [socket timeout="
									+ socketTimeout + "]");

							nextGenSocket = socket;

							MockTPSConnection connection = new MockTPSConnection(
									socket, "NEXTGEN");
							SocketUtils.sleep(1000);
							connection.process_pulse_responses();

						} catch (Exception ex) {
							log.error(ex.getMessage());
						}
					}
				} catch (IOException ex) {
					log.error("could not start mock nextgen server: "
							+ ex.getMessage());
				}

			});


			// create mock pulse
			pool.submit(() -> {
				MockATMConnection atm = new MockATMConnection();

				while (true) {
					try {
						if (atm.connect() == true) {
							log.info("MockATMConnection connected to Router");
							atm.read_process();
						}
					} catch (Exception ex) {
						log.error("MOCK ATM --> ROUTER: " + ex.getMessage());
					}

					log.info(String
							.format("MOCK ATM --> CONNECT RETRY IN %d ms", socketConnectWait));
					SocketUtils.sleep(socketConnectWait);
				}

			});
			
			return null;
		}

		void shutdown() {

			if (legacyServerSocket != null) {
				try {
					legacyServerSocket.close();
					legacyServerSocket = null;
					log.info("legacy server socket closed");
				} catch (IOException e) {
					/* shutdown */}
			}

			if (nextGenServerSocket != null) {
				try {
					nextGenServerSocket.close();
					nextGenServerSocket = null;
					log.info("nextgen server socket closed");
				} catch (IOException e) {
					/* shutdown */}
			}

			if (routerSocket != null) {
				try {
					routerSocket.close();
					routerSocket = null;
					log.info("router socket closed");
				} catch (IOException e) {
					/* shutdown */}
			}

			if (nextGenSocket != null) {
				try {
					nextGenSocket.close();
					nextGenSocket = null;
					log.info("nextgen socket closed");
				} catch (IOException e) {
					/* shutdown */}
			}

			if (legacySocket != null) {
				try {
					legacySocket.close();
					legacySocket = null;
					log.info("legacy socket closed");
				} catch (IOException e) {
					/* shutdown */}
			}

		}


		public class MockATMConnection {

			Socket socket;
			String recent; // most recently read string

			boolean connect() {

				log.info(String
						.format("MockATMConnection connecting to %s:%d", serverHost, serverPort));

				try {
					socket = SocketUtils
							.getSocket(serverHost, serverPort, socketTimeout);
					routerSocket = socket; // save for shutdown
				} catch (Exception ex) {
					log.error("MockATMConnection: " + ex.getMessage());
					return false;
				}

				return true;
			}

			public void send(String s) {
				String log_msg = null;
				try {
					byte[] data = ((String) s).getBytes("ISO-8859-1");
					log_msg = String
							.format("MOCK ATM ---> (SOCKET WRITE) %d bytes", data.length);

					BufferedOutputStream bos = new BufferedOutputStream(
							socket.getOutputStream());

					SocketUtils.writeBytes(bos, data, data.length);

					log.info(log_msg);

				} catch (IOException ex) {
					log.error(log_msg + " failed: " + ex.getMessage());
				}
			}

			// Continuous loop to read bytes arriving on this connection...
			public void read_process() {

				try {

					BufferedInputStream bis = new BufferedInputStream(
							socket.getInputStream());

					while (true) {

						byte[] data = new byte[4096];
						final int read_count = SocketUtils
								.readBytes(bis, data, data.length);

						if (read_count > 0) {

							// output to server console
							log.info(String
									.format("MOCK ATM --> (SOCKET READ) %d bytes", read_count));

							// save data to string
							recent = new String(data, 0, read_count,
									"ISO-8859-1");
						}
						// EOF on client disconnect
						else if (read_count < 0) {
							log.info("*EOF* reading from: "
									+ socket.toString());
							break;
						}
					}
				} catch (IOException ex) {
					log.error(ex.getMessage());
				} finally {

					try {
						// allow time for socket buffer to empty...
						SocketUtils.sleep(socketCloseDelay);

						socket.close();
						log.info("connection closed: " + socket.toString());
					} catch (IOException ex) {
						log.error("connection close failed: "
								+ ex.getMessage());
					}
				}
			}

		}		
		
		// inbound connection from pulse router that has connected to legacy or
		// nextgen
		// tps
		public class MockTPSConnection {

			String name;
			Socket socket = null;
			String recent; // most recently read string

			MockTPSConnection(Socket socket, String name) {
				this.socket = socket;
				this.name = name;
			}

			public void send(String s) {
				String log_msg = null;
				try {
					byte[] data = ((String) s).getBytes("ISO-8859-1");
					log_msg = String
							.format("%s MOCK TPS INJECTION ---> (SOCKET WRITE) %d bytes", name, data.length);

					BufferedOutputStream bos = new BufferedOutputStream(
							socket.getOutputStream());

					SocketUtils.writeBytes(bos, data, data.length);

					log.info(log_msg);

				} catch (IOException ex) {
					log.error(log_msg + " failed: " + ex.getMessage());
				}
			}

			// Continuous loop to read bytes arriving on this connection...
			public void process_pulse_responses() {

				try {

					log.info("process_pulse_responses on: " + this.socket);
					BufferedInputStream bis = new BufferedInputStream(
							this.socket.getInputStream());

					while (true) {

						byte[] data = new byte[4096];
						final int read_count = SocketUtils
								.readBytes(bis, data, data.length);

						if (read_count > 0) {

							// output to server console
							log.info(String
									.format("%s MOCK TPS --> (SOCKET READ) %d bytes", name, read_count));

							// save data to string
							recent = new String(data, 0, read_count,
									"ISO-8859-1");
						}
						// EOF on client disconnect
						else if (read_count < 0) {
							log.info("*EOF* reading from: "
									+ socket.toString());
							break;
						}
					}
				} catch (IOException ex) {
					log.error(ex.getMessage());
				} finally {

					try {
						// allow time for socket buffer to empty...
						SocketUtils.sleep(socketCloseDelay);

						socket.close();
						log.info("connection closed: " + socket.toString());
					} catch (IOException ex) {
						log.error("connection close failed: "
								+ ex.getMessage());
					}
				}
			}

		}


	}

}
