package org.mineacademy.fo.model;

import org.mineacademy.fo.collection.SerializedMap;

/**
 * <p>Classes implementing this can be stored/loaded from a settings file</p>
 *
 * <p>** All classes must also implement the following: **</p>
 * <p>public static T deserialize(SerializedMap map)</p>
 */
public interface ConfigSerializable {

	/**
	 * If false, collections (lists, maps, sets, etc.) with 0 elements would not be loaded
	 * and assigned to a field when using @AutoSerialize
	 * @return default = true
	 */
	default boolean loadEmptyCollections() {
		return true;
	}

	/**
	 * Serialize an object to a String.<br>
	 * Override this only if you use @SerializeToString on the class.<br>
	 * Also, the class must have 'public static YourClass deserialize(String obj)'
	 * method to be deserialized.
	 * @return the string containing information about the object
	 */
	default String serializeToString(){
		return null;
	}

	/**
	 * Creates a Map representation of this class that you can
	 * save in your settings yaml or json file.
	 *<br><br>
	 * Since v6.2.1.5 we have @AutoSerialize so this method is not mandatory now.
	 *
	 * @return Map containing the current state of this class
	 */
	default SerializedMap serialize() {
		return null;
	};
}
