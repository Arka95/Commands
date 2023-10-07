package work;

import icommands.IWork;
import java.util.HashMap;
import java.util.Map;

/**
 * Context under which {@link IWork} is executed. There should be an ongoing
 * database transaction, plus registry that provides all necessary
 * non-persistable references.
 */
public class WorkContext {

    private final Map<String, String> bag = new HashMap<>();

    public String put(String key, String value){
        return bag.put(key, value);
    }

    public String get(String key){
        return bag.getOrDefault(key, null);
    }

    public String putIntoBag(String key, Object value) {
        String valueStr = null;
        if (value != null) {
            valueStr = String.valueOf(value);
        }
        return this.put(key, valueStr);
    }

    void populateBag(Map<String, String> bag) {
        this.bag.putAll(bag);
    }

}
