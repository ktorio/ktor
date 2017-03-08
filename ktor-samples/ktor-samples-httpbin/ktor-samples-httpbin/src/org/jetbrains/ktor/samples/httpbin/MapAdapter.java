package org.jetbrains.ktor.samples.httpbin;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;
import org.jetbrains.ktor.util.ValuesMap;
import org.jetbrains.ktor.util.ValuesMapBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class MapAdapter {
    @FromJson
    public ValuesMap fromJson(Map map) {
        throw new RuntimeException("NIY");
    }

    @ToJson
    public Map toJson(ValuesMap parseMap) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, List<String>> entry : parseMap.entries()) {
            if (entry.getValue().size() == 1) {
                result.put(entry.getKey(), entry.getValue().get(0));
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
