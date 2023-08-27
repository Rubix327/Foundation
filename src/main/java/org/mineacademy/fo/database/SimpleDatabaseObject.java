package org.mineacademy.fo.database;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ConfigSerializable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class SimpleDatabaseObject<T extends ConfigSerializable> extends SimpleDatabaseManager {

    public SimpleDatabaseObject() {
        this.addVariable("table", getTableName());
    }

    public abstract String getTableName();
    public abstract Class<T> getObjectClass();

    public final void insert(@NotNull T object, Runnable onFinish) {
        Common.runAsync(() -> {
            this.insert(getTableName(), object.serialize());
            if (onFinish != null){
                onFinish.run();
            }
        });
    }

    public final void insert(@NonNull SerializedMap columnsAndValues) {
        this.insert(getTableName(), columnsAndValues);
    }

    public final void insertBatch(@NonNull List<SerializedMap> maps) {
        this.insertBatch(getTableName(), maps);
    }

    public void selectAll(Consumer<List<T>> consumer){
        Common.runAsync(() -> {
            List<T> objects = new ArrayList<>();
            this.selectAll(getTableName(), set -> {
                T object = SerializeUtil.deserialize(SerializeUtil.Mode.YAML, getObjectClass(), set);
                objects.add(object);
            });
            consumer.accept(objects);
        });
    }

    public final void select(String param, ResultReader consumer) {
        this.select(getTableName(), param, consumer);
    }

    protected final int count(Object... array) {
        return this.count(getTableName(), SerializedMap.ofArray(array));
    }

    protected final int count(SerializedMap conditions) {
        return this.count(getTableName(), conditions);
    }
}