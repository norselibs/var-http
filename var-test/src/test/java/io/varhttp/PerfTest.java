package io.varhttp;

import io.odinjector.OdinJector;
import io.varhttp.test.HttpClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PerfTest extends BasePerf {
	static PerfLauncher launcher;
	static Thread thread;

	@BeforeClass
	public static void setup() throws InterruptedException, ExecutionException, TimeoutException {
		long s = System.currentTimeMillis();
		OdinJector odinJector = OdinJector.create().addContext(new OdinContext(new VarConfig().setPort(8089)));
		launcher = odinJector.getInstance(PerfLauncher.class);
		thread = new Thread(launcher);
		thread.run();
		launcher.isStarted().get(5, TimeUnit.SECONDS);
		System.out.println("total startup: "+(System.currentTimeMillis()-s)+"ms");
	}

	@AfterClass
	public static void teardown() {
		launcher.stop();
	}



}