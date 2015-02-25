package ca.mcmaster.capstone.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

import ca.mcmaster.capstone.monitoralgorithm.interfaces.BinaryOperator;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Node;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Operator;
import lombok.NonNull;

public class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .enableComplexMapKeySerialization()
            //.registerTypeAdapter(Node.class, new InterfaceAdapter<Node>())
            //.registerTypeAdapter(Operator.class, new InterfaceAdapter<Operator>())
           // .registerTypeAdapter(BinaryOperator.class, new InterfaceAdapter<BinaryOperator>())
            .create();

    public static <T> T fromJson(@NonNull final String json, @NonNull final TypeToken<T> type) {
        return fromJson(json, type.getType());
    }

    public static <T> T fromJson(@NonNull final String json, @NonNull final Type type) {
        return GSON.fromJson(json, type);
    }

    public static String asJson(final Object data) {
        return GSON.toJson(data);
    }

}
