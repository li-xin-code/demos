import java.lang.annotation.*;

/**
 * @author lixin
 * @date 2023/6/23 13:13
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface UseTransactional {

}