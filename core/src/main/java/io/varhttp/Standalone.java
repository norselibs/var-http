package io.varhttp;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.inject.Inject;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import javax.inject.Inject;
import javax.inject.Provider;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Standalone implements Runnable {
	protected VarServlet servlet;
	protected ControllerMapper controllerMapper;
	protected VarConfig varConfig;
	private HttpServer server;
	private SSLContext sslContext = null;

	@Inject
	public Standalone(ControllerMapper controllerMapper, VarConfig varConfig,
					  Provider<ParameterHandler> parameterHandlerProvider, FilterFactory filterFactory) {
		this.servlet = new VarServlet(parameterHandlerProvider, filterFactory, "");
		this.controllerMapper = controllerMapper;
		this.varConfig = varConfig;
	}

	public void addControllerPackage(Package controllerPackage) {
		controllerMapper.map(servlet, controllerPackage.getName());
	}

	public void setSslContext(SSLContext sslContext) {
		this.sslContext = sslContext;
	}

	public void setSslContext(KeyStore keyStore, char[] password) {
		try {
			// Set up the key manager factory
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(keyStore, password);

			// Set up the trust manager factory
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(keyStore);

			// Set up the HTTPS context and parameters
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException | KeyManagementException e) {
			throw new VarInitializationException(e);
		}
	}

	public void setSslContext(InputStream x509Certificate, InputStream privateKey) {
		try {
			sslContext = SSLContext.getInstance("TLS");

			// Initialise the keystore
			char[] password = "".toCharArray();
			KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(null, password);
			Certificate certificate = CertificateFactory.getInstance("X.509")
					.generateCertificate(x509Certificate);

			String text = new BufferedReader(
					new InputStreamReader(privateKey, StandardCharsets.UTF_8))
					.lines()
					.collect(Collectors.joining("\n"));

			text = text.replaceAll("-----(BEGIN|END) PRIVATE KEY-----","").replace("\n","");
			text = text.replaceAll("","");
			byte[] dec = Base64.getDecoder().decode(text);
//			byte[] dec = Base64.decode(text);

			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(dec);

			PrivateKey privKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec);


			ks.setCertificateEntry("server", certificate);
			ks.setKeyEntry("server", privKey,"".toCharArray(), new Certificate[]{certificate});


			setSslContext(ks, password);
			// Set up the key manager factory
		} catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException | InvalidKeySpecException e) {
			throw new VarInitializationException(e);
		}
	}

	private HttpServer getServer() {
		try {
			if (sslContext != null) {
				HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(varConfig.getPort()), 0);
				httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
					@Override
					public void configure(HttpsParameters params) {
						try {
							// Initialise the SSL context
							SSLContext c = SSLContext.getDefault();
							SSLEngine engine = c.createSSLEngine();
							params.setNeedClientAuth(false);
							params.setCipherSuites(engine.getEnabledCipherSuites());

							params.setProtocols(engine.getEnabledProtocols());

							// Get the default parameters
							SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
							params.setSSLParameters(defaultSSLParameters);
						} catch (NoSuchAlgorithmException httpsInitialisationException) {
							throw new VarInitializationException(httpsInitialisationException);
						}
					}
				});
				return httpsServer;
			} else {
				return HttpServer.create(new InetSocketAddress(varConfig.getPort()), 0);
			}
		} catch (IOException exception) {
			throw new VarInitializationException(exception);
		}
	}

	@Override
	public void run() {
		server = getServer();
		server.createContext("/", new VarHttpContext(servlet));
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
	}

	public void stop() {
		server.stop(0);
	}
}
