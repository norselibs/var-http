package io.varhttp;

import io.odinjector.OdinJector;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

public class LauncherTest {
	static Launcher launcher;
	static Thread thread;

	@BeforeClass
	public static void setup() {
		OdinJector odinJector = OdinJector.create().addContext(new OdinContext(new VarConfig().setPort(8088)));
		launcher = odinJector.getInstance(Launcher.class);
		thread = new Thread(launcher);
		thread.run();
	}

	@AfterClass
	public static void teardown() {
		launcher.stop();
	}

	@Test
	public void simple() throws Throwable {
		HttpURLConnection con = HttpClient.get("http://localhost:8088/my-test", "");

		StringBuffer content = HttpClient.readContent(con);

		assertEquals("Simple string", content.toString());
	}

	@Test
	public void pathVariable() throws Throwable {
		HttpURLConnection con = HttpClient.get("http://localhost:8088/pathVar/my-string", "");

		StringBuffer content = HttpClient.readContent(con);

		assertEquals("my-string", content.toString());
	}

	@Test
	public void requestParameter() throws Throwable {
		HttpURLConnection con = HttpClient.get("http://localhost:8088/requestParameter","var=my");

		StringBuffer content = HttpClient.readContent(con);

		assertEquals("my", content.toString());
	}


	@Test
	public void serializedReturnObject() throws Throwable {
		HttpURLConnection con = HttpClient.get("http://localhost:8088/my-test-serialized", "");

		StringBuffer content = HttpClient.readContent(con);

		assertEquals("{\"string\":\"Simple string\"}", content.toString());
	}

	@Test
	public void perfSimple() throws Throwable {
		int reps = 100;
		long s = System.currentTimeMillis();
		for(int i=0;i<reps;i++) {
			HttpURLConnection con = HttpClient.get("http://localhost:8088/my-test", "");

			HttpClient.readContent(con);
		}
		System.out.println("avg time to run: "+((System.currentTimeMillis()-s)/(reps*1.0d)));

	}

	@Test
	public void headers() throws Throwable {
		HttpURLConnection con = HttpClient.get("http://localhost:8088/header", "");
		con.addRequestProperty("My", "Input header");

		String response = HttpClient.readContent(con).toString();
		assertEquals("Input header", con.getHeaderField("My"));
		assertEquals("text/plainish", con.getHeaderField("Content-Type"));
		assertEquals("muh", response);
	}

	@Test
	public void rootController() throws Throwable {
		HttpURLConnection con = HttpClient.get("http://localhost:8088/", "");

		String response = HttpClient.readContent(con).toString();
		assertEquals("Who am i", response);
	}

	@Test
	public void prefixedController() throws Throwable {
		HttpURLConnection con = HttpClient.get("http://localhost:8088/packageprefix/classprefix/controller", "");

		String response = HttpClient.readContent(con).toString();
		assertEquals("prefixed", response);
	}

	@Test
	public void defaultValueInParameter() throws Throwable {
		HttpURLConnection con = HttpClient.get("http://localhost:8088/defaultValue", "");

		String response = HttpClient.readContent(con).toString();
		assertEquals("muh", response);
	}

	@Test
	public void optionalBody() throws Throwable {
		HttpURLConnection con = HttpClient.post("http://localhost:8088/optionalBody", null);

		String response = HttpClient.readContent(con).toString();
		assertEquals("Nothing passed in", response);
	}

}