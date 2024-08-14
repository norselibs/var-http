package io.varhttp;

import io.varhttp.test.HttpClient;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class BasePerf {

	@Test
	public void simple() throws Throwable {
		int reps = 10000;
		int threadCount = 10;

		List<Runnable> threads = new ArrayList<>();

		String body = IntStream.range(0,200).mapToObj(in -> UUID.randomUUID().toString()).collect(Collectors.joining("-"));
		AtomicInteger failed = new AtomicInteger(0);
		for(int j=0;j<threadCount;j++) {
			Runnable t = () -> {
				for (int i = 0; i < reps; i++) {
					int classnum = (int) (Math.random() * 7) + 1;
					int methodNum = (int) (Math.random() * 5) + 1;
					String path = "http://localhost:8089/class" + classnum + "/controller" + methodNum+"/muhbuh?name="+classnum+methodNum;
					try {
						HttpURLConnection con = HttpClient.post(path, body, "text/plain");

						String output = HttpClient.readContent(con).toString();
						assertEquals("muh", output);
					} catch (Exception e) {
						System.out.println(path+" failed");
						e.printStackTrace();
						failed.incrementAndGet();
					}
				}
			};
			threads.add(t);
		}

		ExecutorService tp = Executors.newFixedThreadPool(threadCount);
		long s = System.currentTimeMillis();
		threads.forEach(tp::execute);
		tp.shutdown();
		tp.awaitTermination(10, TimeUnit.SECONDS);
		assertEquals(0, failed.get());
		System.out.println("avg time to run: "+((System.currentTimeMillis()-s)/(reps*threadCount*1.0d)));
		System.out.println("total runtime: "+(System.currentTimeMillis()-s));
	}
}
