package cucumber.api;

import java.util.List;

/**
 * @author Daniel
 */
public interface Step {
    List<String> getComments();
    String getKeyword() ;
    String getName();
}
