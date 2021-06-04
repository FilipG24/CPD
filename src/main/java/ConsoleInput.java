import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.*;

public class ConsoleInput {
    private final int timeout;
    private final TimeUnit unit;

    public ConsoleInput(int timeout, TimeUnit unit) {
        this.timeout = timeout;
        this.unit = unit;
    }

    public String readLine() throws InterruptedException {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        String input = null;
        try {
            Future<String> result = ex.submit(new ConsoleInputReadTask());
            try {
                input = result.get(timeout, unit);
            } catch (ExecutionException e) {
                e.getCause().printStackTrace();
            } catch (TimeoutException e) {
                result.cancel(true);
            }
        } finally {
            ex.shutdownNow();
        }
        return input;
    }
}
