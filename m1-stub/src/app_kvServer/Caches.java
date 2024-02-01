package app_kvServer;

import java.util.*;

public class Caches {
    // Interface definition
    public static interface Cache<K, V> {
        public V get(K key); // get the value of given key in the cache

        public void put(K key, V value); // put a given KV pair in the cache

        public void remove(K key); // remove a given key in the cache

        public int size(); // return the size of the cache

        public boolean containsKey(K key); // check if the cache contains a given key

        public Set<String> keySet(); // return a set of all keys in the cache
    }

    // LRU Cache
    public static class LRUCache implements Cache<String, String> {
        private final LinkedHashMap<String, String> kvs;
        private final int capacity;

        public LRUCache(final int capacity){
            this.capacity = capacity;
            this.kvs = new LinkedHashMap<String, String>(capacity, 0.75f, false){
                @Override // returns true after removing its eldest entry
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest){
                    if (size() > capacity){ // if size exceeds capacity, remove eldest (least recently accessed)
                        String keyToRemove = eldest.getKey();
                        kvs.remove(keyToRemove);
                        return true;
                    }
                    return false;
                }
            };
        }

        @Override
        public String get(String key){
            return kvs.getOrDefault(key, null);
        }

        @Override
        public void put(String key, String value){
            if (capacity <= 0) return; // edge case, if capacity is 0, return
            kvs.put(key, value); 
        }

        @Override
        public void remove(String key) {
            if (!containsKey(key)) return; // no such key
            kvs.remove(key);
        }

        @Override
        public int size(){
            return kvs.size();
        }

        @Override
        public boolean containsKey(String key){
            return kvs.containsKey(key);
        }

        @Override
        public Set<String> keySet(){
            return kvs.keySet();
        }
    }

    // LFU Cache
    public static class LFUCache implements Cache<String, String> {
        private final Map<String, String> kvs;
        private final Map<String, Integer> keyFrequencies;
        private final Map<Integer, LinkedHashSet<String>> frequencyToKeys;
        private int minFrequency;
        private final int capacity;

        public LFUCache(int capacity){
            kvs = new HashMap<>();
            keyFrequencies = new HashMap<>();
            frequencyToKeys = new HashMap<>();
            minFrequency = 0;
            this.capacity = capacity;
        }

        // helper method to increment frequency and adjust keyFrequencies and frequencyToKeys given another get request
        private void incrementFrequency(String key){
            int frequency = keyFrequencies.get(key);
            keyFrequencies.put(key, frequency + 1);
            frequencyToKeys.get(frequency).remove(key);

            if (!frequencyToKeys.containsKey(frequency + 1)) // Update frequencyToKeys mapping if frequency + 1 does not exist
                frequencyToKeys.put(frequency + 1, new LinkedHashSet<String>());
            frequencyToKeys.get(frequency + 1).add(key);

            if (frequency == minFrequency && frequencyToKeys.get(frequency).size() == 1) // if frequency is minimum and current key is the only key with this frequency
                ++minFrequency;
        }
        
        @Override
        public String get(String key){
            if (!containsKey(key)) return null;
                
            incrementFrequency(key);

            return kvs.get(key);
        }

        @Override
        public void put(String key, String value){
            if (capacity <= 0) return;  // edge case, if capacity is 0, return

            if (containsKey(key)){ // if key already exists
                kvs.put(key, value);
                incrementFrequency(key);
                return;
            }
            
            if (size() >= capacity){ // if size exceeds capacity
                LinkedHashSet<String> leastFrequentKeys = frequencyToKeys.get(minFrequency);
                String leastFrequentKey = leastFrequentKeys.iterator().next();
                leastFrequentKeys.remove(leastFrequentKey);

                if (leastFrequentKeys.isEmpty()) { // if there is no more keys with this minFrequency, remove it 
                    frequencyToKeys.remove(minFrequency);
                }
                
                kvs.remove(leastFrequentKey); 
                keyFrequencies.remove(leastFrequentKey);
            }

            kvs.put(key, value);
            keyFrequencies.put(key, 1);

            if (!frequencyToKeys.containsKey(1)) // if there is no keys with frequency 1, append the current kv pair
                frequencyToKeys.put(1, new LinkedHashSet<String>());
            frequencyToKeys.get(1).add(key);
            minFrequency = 1;
        }

        @Override
        public void remove(String key){
            if (!containsKey(key))
                return;

            int frequency = keyFrequencies.get(key);
            kvs.remove(key);
            keyFrequencies.remove(key);
            frequencyToKeys.get(frequency).remove(key);

            if (frequencyToKeys.get(frequency).size() == 0)
                frequencyToKeys.remove(minFrequency);

            if (size() == 0){
                minFrequency = 0;
                return;
            }

            if (frequency == minFrequency){ // given frequency list is empty now after removing
                // increment minFrequency until there is a frequency that is second minimum
                while (frequencyToKeys.containsKey(minFrequency) && frequencyToKeys.get(minFrequency).isEmpty()){
                    ++minFrequency;
                }
            }
        }

        @Override
        public int size(){
            return kvs.size();
        }

        @Override
        public boolean containsKey(String key){
            return kvs.containsKey(key);
        }

        @Override
        public Set<String> keySet(){
            return kvs.keySet();
        }
    }

    // FIFO Cache
    public static class FIFOCache implements Cache<String, String> {
        private final Map<String, String> kvs;
        private final Queue<String> queue;
        private final int capacity;

        public FIFOCache(int capacity){
            kvs = new HashMap<>();
            queue = new LinkedList<>();
            this.capacity = capacity;
        }

        @Override
        public String get(String key){
            return kvs.getOrDefault(key, null);
        }

        @Override
        public void put(String key, String value) {
            if (capacity <= 0) return; // edge case, if capacity is 0, return
            
            if (!containsKey(key)){
                if (size() >= capacity){ 
                    String keyOldest = queue.poll(); // remove head (i.e. oldest kv)
                    if (keyOldest != null) {
                        kvs.remove(keyOldest);
                    }
                }
                queue.add(key);
            }
            kvs.put(key, value);
        }

        @Override
        public void remove(String key){
            if (!containsKey(key)) return; // no such key
            
            kvs.remove(key);
            queue.remove(key);
        }

        @Override
        public int size(){
            return kvs.size();
        }

        @Override
        public boolean containsKey(String key){
            return kvs.containsKey(key);
        }

        @Override
        public Set<String> keySet(){
            return kvs.keySet();
        }
    }
}