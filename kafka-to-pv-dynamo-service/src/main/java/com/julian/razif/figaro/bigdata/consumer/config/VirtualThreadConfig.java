package com.julian.razif.figaro.bigdata.consumer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuration class for Java 21+ virtual threads integration.
 * <p>
 * This configuration provides virtual thread executors for various components:
 * <ul>
 * <li>Project Reactor scheduler for reactive streams</li>
 * <li>Kafka listener container executor for message consumption</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Virtual Threads Benefits:</strong>
 * <ul>
 * <li>80-90% memory reduction compared to platform threads (1MB vs 100KB stack)</li>
 * <li>Supports millions of concurrent lightweight threads</li>
 * <li>Eliminates the need for bounded thread pools</li>
 * <li>Better throughput for I/O-bound operations</li>
 * <li>Simplified concurrency model</li>
 * </ul>
 * </p>
 * <p>
 * <strong>When to Use Virtual Threads:</strong>
 * <ul>
 * <li>I/O-bound operations (database calls, HTTP requests, file I/O)</li>
 * <li>High-concurrency scenarios (thousands of concurrent tasks)</li>
 * <li>Blocking operations that can't be made reactive</li>
 * </ul>
 * </p>
 * <p>
 * <strong>When NOT to Use Virtual Threads:</strong>
 * <ul>
 * <li>CPU-bound operations (better with ForkJoinPool)</li>
 * <li>Synchronized blocks with long critical sections (pinning issue)</li>
 * <li>Native method calls that hold monitor locks</li>
 * </ul>
 * </p>
 *
 * @author Julian Razif Figaro
 * @version 2.0
 * @since 2.0
 * @see <a href="https://openjdk.org/jeps/444">JEP 444: Virtual Threads</a>
 */
@Configuration
public class VirtualThreadConfig {

	private static final Logger logger = LoggerFactory.getLogger(VirtualThreadConfig.class);

	/**
	 * Creates a virtual thread executor service for general-purpose async tasks.
	 * <p>
	 * This executor creates a new virtual thread for each submitted task, providing
	 * excellent scalability for I/O-bound operations without the memory overhead
	 * of platform threads.
	 * </p>
	 * <p>
	 * <strong>Performance Characteristics:</strong>
	 * <ul>
	 * <li>Thread creation: ~1 microsecond (vs ~1 millisecond for platform threads)</li>
	 * <li>Memory per thread: ~100KB (vs ~1MB for platform threads)</li>
	 * <li>Context switch: Extremely cheap (managed by JVM, not OS)</li>
	 * <li>Practical concurrency: Millions of threads</li>
	 * </ul>
	 * </p>
	 *
	 * @return virtual thread executor service
	 */
	@Primary
	@Bean(name = "virtualThreadExecutor", destroyMethod = "close")
	public ExecutorService virtualThreadExecutor() {
		logger.info("Initializing virtual thread executor for Java 21+ features");
		return Executors.newVirtualThreadPerTaskExecutor();
	}

	/**
	 * Creates a Reactor scheduler backed by virtual threads for reactive streams.
	 * <p>
	 * This scheduler replaces {@code Schedulers.boundedElastic()} with a virtual
	 * thread-based implementation, eliminating the need for bounded thread pools
	 * while maintaining excellent performance for blocking I/O operations.
	 * </p>
	 * <p>
	 * <strong>Comparison with Traditional Schedulers:</strong>
	 * <table border="1">
	 * <tr>
	 * <th>Scheduler</th>
	 * <th>Max Threads</th>
	 * <th>Memory/Thread</th>
	 * <th>Best For</th>
	 * </tr>
	 * <tr>
	 * <td>boundedElastic()</td>
	 * <td>10 * CPU cores</td>
	 * <td>~1MB</td>
	 * <td>Bounded blocking I/O</td>
	 * </tr>
	 * <tr>
	 * <td>virtualThreadScheduler()</td>
	 * <td>Millions</td>
	 * <td>~100KB</td>
	 * <td>Unbounded blocking I/O</td>
	 * </tr>
	 * <tr>
	 * <td>parallel()</td>
	 * <td>CPU cores</td>
	 * <td>~1MB</td>
	 * <td>CPU-bound tasks</td>
	 * </tr>
	 * </table>
	 * </p>
	 * <p>
	 * <strong>Use Cases:</strong>
	 * <ul>
	 * <li>Database queries and transactions</li>
	 * <li>HTTP client requests</li>
	 * <li>File I/O operations</li>
	 * <li>Kafka message processing with blocking operations</li>
	 * </ul>
	 * </p>
	 *
	 * @param virtualThreadExecutor the virtual thread executor service
	 * @return Reactor scheduler using virtual threads
	 */
	@Primary
	@Bean(name = "virtualThreadScheduler", destroyMethod = "dispose")
	public Scheduler virtualThreadScheduler(
		ExecutorService virtualThreadExecutor) {

		logger.info("Creating Reactor scheduler with virtual threads for reactive streams");
		logger.info("Virtual threads provide 80-90% memory reduction and support millions of concurrent tasks");
		return Schedulers.fromExecutorService(virtualThreadExecutor);
	}

	/**
	 * Creates a dedicated virtual thread executor for Kafka listener containers.
	 * <p>
	 * This executor is specifically tuned for Kafka message consumption patterns,
	 * allowing each consumer thread to spawn virtual threads for parallel message
	 * processing without exhausting system resources.
	 * </p>
	 * <p>
	 * <strong>Kafka Virtual Thread Integration:</strong>
	 * <ul>
	 * <li>Consumer threads: 3 platform threads (configurable)</li>
	 * <li>Message processing: Virtual threads per message</li>
	 * <li>Total capacity: Limited only by heap memory</li>
	 * </ul>
	 * </p>
	 * <p>
	 * <strong>Performance Benefits:</strong>
	 * <ul>
	 * <li>Process thousands of messages concurrently per consumer thread</li>
	 * <li>No thread pool exhaustion under a high load</li>
	 * <li>Better throughput for I/O-heavy message processing</li>
	 * <li>Simplified backpressure management</li>
	 * </ul>
	 * </p>
	 *
	 * @return virtual thread executor for Kafka operations
	 */
	@Bean(name = "kafkaVirtualThreadExecutor", destroyMethod = "close")
	public ExecutorService kafkaVirtualThreadExecutor() {
		logger.info("Creating dedicated virtual thread executor for Kafka listener containers");
		logger.info("Kafka virtual threads enable unlimited message concurrency without platform thread exhaustion");
		return Executors.newVirtualThreadPerTaskExecutor();
	}
}
