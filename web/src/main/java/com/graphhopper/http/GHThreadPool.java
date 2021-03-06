package com.graphhopper.http;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class GHThreadPool {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private final ExecutorService service;
    private final int threads;
    private BlockingQueue<GHWorker> resolverQueue;

    public GHThreadPool(int queueSize, int threads) {
        resolverQueue = new LinkedBlockingQueue<GHWorker>(queueSize);
        this.threads = threads;
        service = Executors.newFixedThreadPool(threads);
    }

    /**
     * Starts this thread pool in background
     */
    public GHThreadPool startService() {
        new Thread("ThreadPool Service Executor") {
            @Override public void run() {
                Collection<Callable<Object>> callableCollection = new ArrayList<Callable<Object>>(threads);
                for (int i = 0; i < threads; i++) {
                    final int threadNo = i;
                    callableCollection.add(new Callable<Object>() {
                        @Override public Object call() throws Exception {
                            try {
                                while (!isInterrupted()) {
                                    execute(threadNo);
                                }
                            } catch (InterruptedException ex) {
                                logger.debug(getName() + " - thread " + threadNo + " interrupted. Error: " + ex.getMessage());
                            } catch (Throwable ex) {
                                logger.error(getName() + " - thread " + threadNo + " died", ex);
                            }
                            return null;
                        }
                    });
                }
                try {
                    logger.info(getName() + " STARTED");
                    service.invokeAll(callableCollection);
                    logger.info(getName() + " FINISHED");
                } catch (RejectedExecutionException ex) {
                    logger.info(getName() + " cannot create threads, " + ex.getMessage());
                } catch (InterruptedException ex) {
                    logger.info(getName() + " was interrupted, " + ex.getMessage());
                }
            }
        }.start();
        return this;
    }

    public void enqueue(GHWorker worker) {
        if (!resolverQueue.offer(worker.doEnqueue()))
            logger.error("Queue full!? " + resolverQueue.size() + " couldn't enqueue " + worker);
    }

    protected void execute(int workerNo) throws InterruptedException {
        GHWorker worker = resolverQueue.take();
        try {
            if (!worker.isTimedOut()) {
                worker.run();                
            } else
                logger.warn(worker + " timed out - maximum livetime reached (" + worker.maxLiveTimeInMillis + ")");
        } catch (Exception ex) {
            logger.warn(workerNo + " Error for worker " + worker + ", error:" + ex.getMessage());
        }
        worker.finish();
    }

    public void stopService() {
        service.shutdown();
    }

    public void waitFor(List<GHWorker> workers, long timeOutInMillis) {        
        long remainingTimeout = timeOutInMillis;
        try {
            for (GHWorker w : workers) {
                long tmp = System.currentTimeMillis();
                synchronized (w) {
                    w.wait(timeOutInMillis);
                }                
                remainingTimeout -= (System.currentTimeMillis() - tmp);
                if (remainingTimeout < 10)                    
                    break;
            }
        } catch (InterruptedException ex) {
            logger.warn("workers were interrupted " + workers);
        }
    }

    public static abstract class GHWorker implements Runnable {

        private long maxLiveTimeInMillis = 5000;
        private long startTime = -1;

        public GHWorker(long maxLiveTimeInMillis) {
            this.maxLiveTimeInMillis = maxLiveTimeInMillis;
        }

        private GHWorker doEnqueue() {
            startTime = System.currentTimeMillis();
            return this;
        }

        private boolean isTimedOut() {
            if (startTime < 0)
                throw new IllegalStateException("Call doEnqueue before");
            return (System.currentTimeMillis() - startTime) > maxLiveTimeInMillis;
        }

        public abstract String name();

        @Override public String toString() {
            return name();
        }

        private void finish() {
            synchronized (this) {
                notifyAll();
            }
        }
    }
}
