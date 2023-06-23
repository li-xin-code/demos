import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author lixin
 * @date 2023/6/23 12:57
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperateChangeLog {
    private Long orderId;
    private Integer count;

    /**
     * 1-扣减，2-回滚
     */
    private Integer type;
}
