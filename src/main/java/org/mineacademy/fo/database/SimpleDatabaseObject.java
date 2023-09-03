package org.mineacademy.fo.database;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ConfigSerializable;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public abstract class SimpleDatabaseObject<T extends ConfigSerializable> extends SimpleDatabaseManager {

    public SimpleDatabaseObject() {
        this.addVariable("table", getTableName());
    }

    public abstract String getTableName();
    public abstract Class<T> getObjectClass();

    public final void insert(@NotNull T object, @NotNull Callback<Void> callback) {
        this.insert(getTableName(), object, callback);
    }

    public final void insert(@NonNull SerializedMap columnsAndValues, @NotNull Callback<Void> callback) {
        this.insert(getTableName(), columnsAndValues, callback);
    }

    public final void insertBatch(@NonNull List<SerializedMap> maps, @NotNull Callback<Void> callback) {
        this.insertBatch(getTableName(), maps, callback);
    }

    public final void select(String columns, @NotNull Callback<ResultSet> callback) {
        this.select(getTableName(), columns, callback);
    }

    public void selectAll(@NotNull Callback<List<T>> callback){
        this.selectAllWhere(null, callback);
    }

    public void selectAllWhere(String where, @NotNull Callback<List<T>> callback){
        final boolean[] failed = new boolean[1];
        List<T> objects = new ArrayList<>();

        this.selectAllForEach(getTableName(), where, new Callback<ResultSet>() {
            @Override
            public void onSuccess(ResultSet set) {
                SerializedMap map = SerializedMap.of(set);
                T object = SerializeUtil.deserialize(getMode(), getObjectClass(), map);
                objects.add(object);
            }

            @Override
            public void onFail(Throwable t) {
                callback.onFail(t);
                failed[0] = true;
            }
        });

        if (!failed[0]){
            callback.onSuccess(objects);
        }
    }

    protected final void count(@NotNull Callback<Integer> callback, Object... array) {
        this.count(getTableName(), callback, SerializedMap.ofArray(array));
    }

    protected final void count(SerializedMap conditions, @NotNull Callback<Integer> callback) {
        this.count(getTableName(), conditions, callback);
    }
}