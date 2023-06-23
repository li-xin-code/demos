import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author lixin
 * @date 2023/6/22 15:36
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestPromise {
    private Request request;
    private Result result;
}
