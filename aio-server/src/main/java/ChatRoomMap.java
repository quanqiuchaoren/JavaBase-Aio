import java.util.*;

public class ChatRoomMap<K, V> {
    //本质上保存数据的是一个特殊的HashMap，我们定义过的，线程安全
    public Map<K, V> map = Collections.synchronizedMap(new HashMap<>());

    //可以根据V来删除指定的项目
    public synchronized void removeByValue(Object value) {
        for (Object key : map.keySet()) {
            if (map.get(key) == value) {
                map.remove(key);
                break;
            }
        }
    }

    //获取所有的Value组合成的set集合
    public synchronized Set<V> getValueSet() {
        Set<V> res = new HashSet<>();
        //将map中的value添加到res集合里
        for (Object key : map.keySet()) {
            res.add(map.get(key));
        }
        return res;
    }

    //根据value值来找到key
    public synchronized K getKeyByValue(V val) {
        for (K key : map.keySet()) {
            if (map.get(key) == val || map.get(key).equals(val)) {
                return key;
            }
        }
        return null;
    }

    //实现添加数据到ChatRoomMap中,此数据结构规定value不能重复
    public synchronized V put(K key, V value) {
        //遍历所有的value值判断有重复的没得
        for (V val : getValueSet()) {
            if (val.equals(value) && val.hashCode() == value.hashCode()) {
                throw new RuntimeException("Map实例中不允许有重复的value值！");
            }
        }
        return map.put(key, value);
    }
}
