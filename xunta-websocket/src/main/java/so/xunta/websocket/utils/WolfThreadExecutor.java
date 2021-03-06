package so.xunta.websocket.utils;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;



/**
 * @author Bright zheng
 * 继承ThreadPoolExecutor，在每次线程执行结束后查看线程池中空闲情况，如果queue中的线程数小于容量的一半，就增加pending的任务
 * */
public class WolfThreadExecutor extends ThreadPoolExecutor{
	@Autowired
	private RecommendTaskPool recommendTaskPool;
	@Autowired
	private PendingTaskQueue pendingTaskQueue;
	
	Logger logger=Logger.getRootLogger();

	public WolfThreadExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
			BlockingQueue<Runnable> workQueue) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
	}

	@Override
	protected void afterExecute(Runnable r, Throwable t) {
		super.afterExecute(r, t);
		int pendingTaskCount = this.getQueue().size();
		int queueCpacity = this.getQueue().remainingCapacity();
		if(pendingTaskCount < (queueCpacity/2)){
			List<Runnable> tasks = pendingTaskQueue.getTaskList(queueCpacity/2);
			for(Runnable task:tasks){
				logger.info("线程池空闲，执行搁置任务");
				recommendTaskPool.execute(task);
			}
		}		
	}
	
}
