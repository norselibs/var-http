package io.varhttp;

import io.varhttp.performance.Class1;

import javax.inject.Inject;
import java.time.Duration;
import java.util.concurrent.Future;

public class PerfLauncherUndertow implements Runnable {
	private VarUndertow standalone;
	@Inject
	public PerfLauncherUndertow(VarUndertow standalone) {
		this.standalone = standalone;
	}

	@Override
	public void run() {
		long s = System.currentTimeMillis();
		standalone.configure(configuration -> {
			configuration.addControllerPackage(Class1.class.getPackage());
		});
		standalone.getStarted().thenAccept(b -> {
			System.out.println("Startup time: "+(System.currentTimeMillis()-s));
		});
		standalone.run();

	}

	public void stop() {
		standalone.stop();
	}

	public Future<Boolean> isStarted() {
		return standalone.getStarted();
	}

}
