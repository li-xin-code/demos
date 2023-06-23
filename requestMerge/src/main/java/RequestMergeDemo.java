import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author lixin
 * @date 2023/6/22 15:27
 */
public class RequestMergeDemo {
    private final BlockingQueue<RequestPromise> queue = new LinkedBlockingQueue<>(1000);
    ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
            .setNameFormat("merge-pool-%d").build();
    private final ScheduledThreadPoolExecutor executor =
            new ScheduledThreadPoolExecutor(1, namedThreadFactory);

    @Getter
    private final List<OperateChangeLog> logRecord = new LinkedList<>();

    @Getter
    @Setter
    private Integer stock = 13;

    public RequestMergeDemo() {
        mergeJob();
    }

    /**
     * 操作数据库，扣件库存
     *
     * @param request ...
     * @return ...
     */
    public Result operate(Request request) {
        RequestPromise promise = new RequestPromise(request, null);
        boolean isOfferSuccess;
        synchronized (promise) {
            try {
                isOfferSuccess = queue.offer(promise, 100, TimeUnit.MICROSECONDS);
                if (!isOfferSuccess) {
                    return new Result(false, "系统繁忙");
                }
                promise.wait(200);
                if (Objects.isNull(promise.getResult())) {
                    return new Result(false, "等待超时");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return promise.getResult();
    }

    private void saveLog(List<Request> requestList, Integer type) {
        requestList.stream()
                .map(request -> new OperateChangeLog(request.getOrderId(), request.getCount(), type))
                .forEach(logRecord::add);
    }

    public void done() {
        executor.shutdown();
    }

    private final Object lock = new Object();
    private final AtomicInteger atomicInteger = new AtomicInteger(1);

    private void mergeJob() {
        executor.scheduleAtFixedRate(() -> {
            synchronized (lock) {
                // 模拟随机的Full GC
                int frequency = 3;
                if (atomicInteger.getAndIncrement() % frequency == 0) {
                    try {
                        System.out.println("Full GC");
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                List<RequestPromise> list = new ArrayList<>();
                int size = queue.size();
                for (int i = 0; i < size; i++) {
                    list.add(queue.poll());
                }
                List<Long> userIds = list.stream()
                        .map(RequestPromise::getRequest)
                        .map(Request::getUserId)
                        .collect(Collectors.toList());
                if (!userIds.isEmpty()) {
                    System.out.println(Thread.currentThread().getName() + "#merge: " + userIds);
                }
                int sum = list.stream()
                        .map(RequestPromise::getRequest)
                        .mapToInt(Request::getCount)
                        .sum();
                if (sum <= stock) {
                    decrementStock(list.stream().map(RequestPromise::getRequest).collect(Collectors.toList()));
                    list.forEach(promise -> {
                        promise.setResult(new Result(true, "ok"));
                        synchronized (promise) {
                            promise.notify();
                        }
                    });
                } else {
                    for (RequestPromise promise : list) {
                        Integer count = promise.getRequest().getCount();
                        if (count <= stock) {
                            decrementStock(Collections.singletonList(promise.getRequest()));
                            promise.setResult(new Result(true, "ok"));
                        } else {
                            promise.setResult(new Result(false, "库存不足"));
                        }
                        synchronized (promise) {
                            promise.notify();
                        }
                    }
                }
                list.clear();
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    @UseTransactional()
    private void decrementStock(List<Request> list) {
        int sum = list.stream()
                .mapToInt(Request::getCount)
                .sum();
        stock -= sum;
        saveLog(list, 1);
    }

    @UseTransactional()
    private void rollbackStock(Request request) {
        System.out.println("rollback: " + request);
        stock += request.getCount();
        saveLog(Collections.singletonList(request), 2);
    }

    public void rollback(Request request) {
        System.out.println("call rollback :" + request);
        boolean isLogExist = logRecord.stream().map(OperateChangeLog::getOrderId)
                .anyMatch(orderId -> Objects.equals(orderId, request.getOrderId()));
        if (isLogExist) {
            boolean isAlreadyRollback = logRecord.stream()
                    .anyMatch(logRecord -> Objects.equals(logRecord.getOrderId(), request.getOrderId())
                            && Objects.equals(logRecord.getType(), 2));
            if (isAlreadyRollback) {
                return;
            }
            rollbackStock(request);
        }
    }
}


