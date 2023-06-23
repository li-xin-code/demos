import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author lixin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Request {
    private Long orderId;
    private Long userId;
    private Integer count;
}