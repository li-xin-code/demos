import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author lixin
 * @date 2023/6/22 19:04
 */
public class Main {
    private static final int THREAD_SIZE = 20;

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executorService = Executors.newCachedThreadPool();
        CyclicBarrier barrier = new CyclicBarrier(3);
        RequestMergeDemo mergeDemo = new RequestMergeDemo();
        int stock = 30;
        mergeDemo.setStock(stock);
        Map<Request, CompletableFuture<Result>> futureMap = new HashMap<>(16);
        Thread.sleep(100);
        List<Request> requestList = IntStream.range(0, THREAD_SIZE).mapToObj(i -> {
            long orderId = i + 1000L;
            long userId = i + 2000L;
            int count = ThreadLocalRandom.current().nextInt(1, 3);
            return new Request(orderId, userId, count);
        }).collect(Collectors.toList());

        List<CompletableFuture<Result>> list = requestList.stream().parallel()
                .map(request -> {
                    CompletableFuture<Result> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            int await = barrier.await(200, TimeUnit.MILLISECONDS);
                            if (barrier.getParties() == (await + 1)) {
                                Thread.sleep(ThreadLocalRandom.current().nextInt(50, 100));
                            }
                        } catch (TimeoutException | BrokenBarrierException e) {
                            System.out.println("等待超时，放行请求");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            Thread.currentThread().interrupt();
                        }
                        return mergeDemo.operate(request);
                    }, executorService);
                    futureMap.put(request, future);
                    return future;
                })
                .collect(Collectors.toList());
        futureMap.forEach((k, v) -> {
            try {
                Result result = v.get(450, TimeUnit.MILLISECONDS);
                System.out.println("request:" + k + ", result: " + result);
                if (!result.getSuccess()) {
                    mergeDemo.rollback(k);
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
            }

        });

        while (true) {
            if (list.stream().allMatch(CompletableFuture::isDone)) {
                System.out.println("stock:" + stock);
                List<OperateChangeLog> logRecord = mergeDemo.getLogRecord();
                Map<Integer, List<OperateChangeLog>> logMap =
                        logRecord.stream().collect(Collectors.groupingBy(OperateChangeLog::getType));
                List<OperateChangeLog> records = logMap.getOrDefault(1, Collections.emptyList());
                records.forEach(System.out::println);
                System.out.println(records.stream().mapToInt(OperateChangeLog::getCount).sum());
                records = logMap.getOrDefault(2, Collections.emptyList());
                records.forEach(System.out::println);
                System.out.println(records.stream().mapToInt(OperateChangeLog::getCount).sum());
                System.out.println("stock:" + mergeDemo.getStock());
                executorService.shutdown();
                mergeDemo.done();
                break;
            }
        }
    }
}
