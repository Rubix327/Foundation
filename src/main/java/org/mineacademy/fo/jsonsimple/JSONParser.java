/* Copyright 2016 Clifton Labs
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
package org.mineacademy.fo.jsonsimple;

import lombok.NonNull;

import java.io.*;
import java.util.*;

/** Jsoner provides JSON utilities for escaping strings to be JSON compatible, thread safe parsing (RFC 7159) JSON
 * strings, and thread safe serializing data to strings in JSON format.
 *
 * @author https://cliftonlabs.github.io/json-simple/
 * @since 2.0.0 */
public class JSONParser {
	/** Flags to tweak the behavior of the primary deserialization method. */
	private enum DeserializationOptions {
		/** Whether multiple JSON values can be deserialized as a root element. */
		ALLOW_CONCATENATED_JSON_VALUES,
		/** Whether a JsonArray can be deserialized as a root element. */
		ALLOW_JSON_ARRAYS,
		/** Whether a boolean, null, Number, or String can be deserialized as a root element. */
		ALLOW_JSON_DATA,
		/** Whether a JsonObject can be deserialized as a root element. */
		ALLOW_JSON_OBJECTS;
	}

	/** Flags to tweak the behavior of the primary serialization method. */
	private enum SerializationOptions {
		/** Instead of aborting serialization on non-JSON values it will continue serialization by serializing the
		 * non-JSON value directly into the now invalid JSON. Be mindful that invalid JSON will not successfully
		 * deserialize. */
		ALLOW_INVALIDS,
		/** Instead of aborting serialization on non-JSON values that implement Jsonable it will continue serialization
		 * by deferring serialization to the Jsonable.
		 * @see Jsonable */
		ALLOW_JSONABLES;
	}

	/** The possible States of a JSON deserializer. */
	private enum States {
		/** Post-parsing state. */
		DONE,
		/** Pre-parsing state. */
		INITIAL,
		/** Parsing error, ParsingException should be thrown. */
		PARSED_ERROR,
		PARSING_ARRAY,
		/** Parsing a key-value pair inside of an object. */
		PARSING_ENTRY,
		PARSING_OBJECT;
	}

	/**
	 * Returns a new instance of the json parser
	 *
	 * @deprecated simply call static methods instead
	 *
	 * @return
	 */
	@Deprecated
	public static JSONParser getInstance() {
		return new JSONParser();
	}

	private JSONParser() {
		/* Jsoner is purely static so instantiation is unnecessary. */
	}

	/**
	 * @see #deserialize(String)
	 *
	 * @param reader
	 * @return
	 * @throws JSONParseException
	 * @deprecated use deserialize() instead
	 *
	 */
	@Deprecated
	public static Object parse(Reader reader) throws JSONParseException {
		return deserialize(reader);
	}

	/**
	 * @see #deserialize(String)
	 *
	 * @param json
	 * @return
	 * @throws JSONParseException
	 * @deprecated use {@link #deserialize(String)} instead
	 *
	 */
	@Deprecated
	public static Object parse(String json) throws JSONParseException {
		return deserialize(json);
	}

	/** Deserializes a readable stream according to the RFC 7159 JSON specification.
	 * @param readableDeserializable representing content to be deserialized as JSON.
	 * @return either a boolean, null, Number, String, JsonObject, or JsonArray that best represents the deserializable.
	 * @throws JSONParseException if an unexpected token is encountered in the deserializable. To recover from a
	 *         JsonException: fix the deserializable to no longer have an unexpected token and try again. */
	public static Object deserialize(final Reader readableDeserializable) throws JSONParseException {
		return JSONParser.deserialize(readableDeserializable, EnumSet.of(DeserializationOptions.ALLOW_JSON_ARRAYS, DeserializationOptions.ALLOW_JSON_OBJECTS, DeserializationOptions.ALLOW_JSON_DATA)).get(0);
	}

	/** Deserialize a stream with all deserialized JSON values are wrapped in a JsonArray.
	 * @param deserializable representing content to be deserialized as JSON.
	 * @param flags representing the allowances and restrictions on deserialization.
	 * @return the allowable object best represented by the deserializable.
	 * @throws JSONParseException if a disallowed or unexpected token is encountered in the deserializable. To recover from a
	 *         JSONParseException: fix the deserializable to no longer have a disallowed or unexpected token and try
	 *         again. */
	private static JSONArray deserialize(final Reader deserializable, final Set<DeserializationOptions> flags) throws JSONParseException {
		final Yylex lexer = new Yylex(deserializable);
		Yytoken token;
		States currentState;
		int returnCount = 1;
		final LinkedList<States> stateStack = new LinkedList<>();
		final LinkedList<Object> valueStack = new LinkedList<>();
		stateStack.addLast(States.INITIAL);
		do {
			/* Parse through the parsable string's tokens. */
			currentState = JSONParser.popNextState(stateStack);
			token = JSONParser.lexNextToken(lexer);
			switch (currentState) {
				case DONE:
					/* The parse has finished a JSON value. */
					if (!flags.contains(DeserializationOptions.ALLOW_CONCATENATED_JSON_VALUES) || Yytoken.Types.END.equals(token.getType()))
						/* Break if concatenated values are not allowed or if an END token is read. */
						break;
					/* Increment the amount of returned JSON values and treat the token as if it were a fresh parse. */
					returnCount += 1;
					/* Fall through to the case for the initial state. */
					//$FALL-THROUGH$
				case INITIAL:
					/* The parse has just started. */
					switch (token.getType()) {
						case DATUM:
							/* A boolean, null, Number, or String could be detected. */
							if (flags.contains(DeserializationOptions.ALLOW_JSON_DATA)) {
								valueStack.addLast(token.getValue());
								stateStack.addLast(States.DONE);
							} else
								throw new JSONParseException(lexer.getPosition(), JSONParseException.Problems.DISALLOWED_TOKEN, token);
							break;
						case LEFT_BRACE:
							/* An object is detected. */
							if (flags.contains(DeserializationOptions.ALLOW_JSON_OBJECTS)) {
								valueStack.addLast(new JSONObject());
								stateStack.addLast(States.PARSING_OBJECT);
							} else
								throw new JSONParseException(lexer.getPosition(), JSONParseException.Problems.DISALLOWED_TOKEN, token);
							break;
						case LEFT_SQUARE:
							/* An array is detected. */
							if (flags.contains(DeserializationOptions.ALLOW_JSON_ARRAYS)) {
								valueStack.addLast(new JSONArray());
								stateStack.addLast(States.PARSING_ARRAY);
							} else
								throw new JSONParseException(lexer.getPosition(), JSONParseException.Problems.DISALLOWED_TOKEN, token);
							break;
						default:
							/* Neither a JSON array or object was detected. */
							throw new JSONParseException(lexer.getPosition(), JSONParseException.Problems.UNEXPECTED_TOKEN, token);
					}
					break;
				case PARSED_ERROR:
					/* The parse could be in this state due to the state stack not having a state to pop off. */
					throw new JSONParseException(lexer.getPosition(), JSONParseException.Problems.UNEXPECTED_TOKEN, token);
				case PARSING_ARRAY:
					switch (token.getType()) {
						case COMMA:
							/* The parse could detect a comma while parsing an array since it separates each element. */
							stateStack.addLast(currentState);
							break;
						case DATUM:
							/* The parse found an element of the array. */
							JSONArray val = (JSONArray) valueStack.getLast();
							val.add(token.getValue());
							stateStack.addLast(currentState);
							break;
						case LEFT_BRACE:
							/* The parse found an object in the array. */
							val = (JSONArray) valueStack.getLast();
							final JSONObject object = new JSONObject();
							val.add(object);
							valueStack.addLast(object);
							stateStack.addLast(currentState);
							stateStack.addLast(States.PARSING_OBJECT);
							break;
						case LEFT_SQUARE:
							/* The parse found another array in the array. */
							val = (JSONArray) valueStack.getLast();
							final JSONArray array = new JSONArray();
							val.add(array);
							valueStack.addLast(array);
							stateStack.addLast(currentState);
							stateStack.addLast(States.PARSING_ARRAY);
							break;
						case RIGHT_SQUARE:
							/* The parse found the end of the array. */
							if (valueStack.size() > returnCount)
								valueStack.removeLast();
							else
								/* The parse has been fully resolved. */
								stateStack.addLast(States.DONE);
							break;
						default:
							/* Any other token is invalid in an array. */
							throw new JSONParseException(lexer.getPosition(), JSONParseException.Problems.UNEXPECTED_TOKEN, token);
					}
					break;
				case PARSING_OBJECT:
					/* The parse has detected the start of an object. */
					switch (token.getType()) {
						case COMMA:
							/* The parse could detect a comma while parsing an object since it separates each key value
							 * pair. Continue parsing the object. */
							stateStack.addLast(currentState);
							break;
						case DATUM:
							/* The token ought to be a key. */
							if (token.getValue() instanceof String) {
								/* JSON keys are always strings, strings are not always JSON keys but it is going to be
								 * treated as one. Continue parsing the object. */
								final String key = (String) token.getValue();
								valueStack.addLast(key);
								stateStack.addLast(currentState);
								stateStack.addLast(States.PARSING_ENTRY);
							} else
								/* Abort! JSON keys are always strings and it wasn't a string. */
								throw new JSONParseException(lexer.getPosition(), JSONParseException.Problems.UNEXPECTED_TOKEN, token);
							break;
						case RIGHT_BRACE:
							/* The parse has found the end of the object. */
							if (valueStack.size() > returnCount)
								/* There are unresolved values remaining. */
								valueStack.removeLast();
							else
								/* The parse has been fully resolved. */
								stateStack.addLast(States.DONE);
							break;
						default:
							/* The parse didn't detect the end of an object or a key. */
							throw new JSONParseException(lexer.getPosition(), JSONParseException.Problems.UNEXPECTED_TOKEN, token);
					}
					break;
				case PARSING_ENTRY:
					switch (token.getType()) {
						/* Parsed pair keys can only happen while parsing objects. */
						case COLON:
							/* The parse could detect a colon while parsing a key value pair since it separates the key
							 * and value from each other. Continue parsing the entry. */
							stateStack.addLast(currentState);
							break;
						case DATUM:
							/* The parse has found a value for the parsed pair key. */
							String key = (String) valueStack.removeLast();
							JSONObject parent = (JSONObject) valueStack.getLast();
							parent.put(key, token.getValue());
							break;
						case LEFT_BRACE:
							/* The parse has found an object for the parsed pair key. */
							key = (String) valueStack.removeLast();
							parent = (JSONObject) valueStack.getLast();
							final JSONObject object = new JSONObject();
							parent.put(key, object);
							valueStack.addLast(object);
							stateStack.addLast(States.PARSING_OBJECT);
							break;
						case LEFT_SQUARE:
							/* The parse has found an array for the parsed pair key. */
							key = (String) valueStack.removeLast();
							parent = (JSONObject) valueStack.getLast();
							final JSONArray array = new JSONArray();
							parent.put(key, array);
							valueStack.addLast(array);
							stateStack.addLast(States.PARSING_ARRAY);
							break;
						default:
							/* The parse didn't find anything for the parsed pair key. */
							throw new JSONParseException(lexer.getPosition(), JSONParseException.Problems.UNEXPECTED_TOKEN, token);
					}
					break;
				default:
					break;
			}
			/* If we're not at the END and DONE then do the above again. */
		} while (!(States.DONE.equals(currentState) && Yytoken.Types.END.equals(token.getType())));
		return new JSONArray(valueStack);
	}

	/** A convenience method that assumes a StringReader to deserialize a string.
	 * @param deserializable representing content to be deserialized as JSON.
	 * @return either a boolean, null, Number, String, JsonObject, or JsonArray that best represents the deserializable.
	 * @throws JSONParseException if an unexpected token is encountered in the deserializable. To recover from a
	 *         JsonException: fix the deserializable to no longer have an unexpected token and try again.
	 *
	 * @see StringReader */
	public static Object deserialize(@NonNull final String deserializable) throws JSONParseException {

		final String trimmed = deserializable.trim();

		// Assume it's just a normal string
		if (!trimmed.startsWith("{") || !trimmed.endsWith("}"))
			return deserializable;

		Object returnable;
		StringReader readableDeserializable = null;
		try {
			readableDeserializable = new StringReader(deserializable);
			returnable = JSONParser.deserialize(readableDeserializable);
		} catch (final NullPointerException caught) {
			/* They both have the same recovery scenario.
			 * See StringReader.
			 * If deserializable is null, it should be reasonable to expect null back. */
			returnable = null;
		} finally {
			if (readableDeserializable != null)
				readableDeserializable.close();
		}
		return returnable;
	}

	/** A convenience method that assumes a JsonArray must be deserialized.
	 * @param deserializable representing content to be deserializable as a JsonArray.
	 * @param defaultValue representing what would be returned if deserializable isn't a JsonArray or an IOException,
	 *        NullPointerException, or JsonException occurs during deserialization.
	 * @return a JsonArray that represents the deserializable, or the defaultValue if there isn't a JsonArray that
	 *         represents deserializable.
	 */
	public static JSONArray deserialize(final String deserializable, final JSONArray defaultValue) {
		StringReader readable = null;
		JSONArray returnable;
		try {
			readable = new StringReader(deserializable);
			returnable = JSONParser.deserialize(readable, EnumSet.of(DeserializationOptions.ALLOW_JSON_ARRAYS)).getArray(0);
		} catch (NullPointerException | JSONParseException caught) {
			/* Don't care, just return the default value. */
			returnable = defaultValue;
		} finally {
			if (readable != null)
				readable.close();
		}
		return returnable;
	}

	/** A convenience method that assumes a JsonObject must be deserialized.
	 * @param deserializable representing content to be deserializable as a JsonObject.
	 * @param defaultValue representing what would be returned if deserializable isn't a JsonObject or an IOException,
	 *        NullPointerException, or JsonException occurs during deserialization.
	 * @return a JsonObject that represents the deserializable, or the defaultValue if there isn't a JsonObject that
	 *         represents deserializable.
	 */
	public static JSONObject deserialize(final String deserializable, final JSONObject defaultValue) {
		StringReader readable = null;
		JSONObject returnable;
		try {
			readable = new StringReader(deserializable);
			returnable = JSONParser.deserialize(readable, EnumSet.of(DeserializationOptions.ALLOW_JSON_OBJECTS)).<JSONObject>getMap(0);
		} catch (NullPointerException | JSONParseException caught) {
			/* Don't care, just return the default value. */
			returnable = defaultValue;
		} finally {
			if (readable != null)
				readable.close();
		}
		return returnable;
	}

	/** A convenience method that assumes multiple RFC 7159 JSON values (except numbers) have been concatenated together
	 * for deserilization which will be collectively returned in a JsonArray wrapper.
	 * There may be numbers included, they just must not be concatenated together as it is prone to
	 * NumberFormatExceptions (thus causing a JsonException) or the numbers no longer represent their
	 * respective values.
	 * Examples:
	 * "123null321" returns [123, null, 321]
	 * "nullnullnulltruefalse\"\"{}[]" returns [null, null, null, true, false, "", {}, []]
	 * "123" appended to "321" returns [123321]
	 * "12.3" appended to "3.21" throws JsonException(NumberFormatException)
	 * "123" appended to "-321" throws JsonException(NumberFormatException)
	 * "123e321" appended to "-1" throws JsonException(NumberFormatException)
	 * "null12.33.21null" throws JsonException(NumberFormatException)
	 * @param deserializable representing concatenated content to be deserialized as JSON in one reader. Its contents
	 *        may not contain two numbers concatenated together.
	 * @return a JsonArray that contains each of the concatenated objects as its elements. Each concatenated element is
	 *         either a boolean, null, Number, String, JsonArray, or JsonObject that best represents the concatenated
	 *         content inside deserializable.
	 * @throws JSONParseException if an unexpected token is encountered in the deserializable. To recover from a
	 *         JsonException: fix the deserializable to no longer have an unexpected token and try again. */
	public static JSONArray deserializeMany(final Reader deserializable) throws JSONParseException {
		return JSONParser.deserialize(deserializable, EnumSet.of(DeserializationOptions.ALLOW_JSON_ARRAYS, DeserializationOptions.ALLOW_JSON_OBJECTS, DeserializationOptions.ALLOW_JSON_DATA, DeserializationOptions.ALLOW_CONCATENATED_JSON_VALUES));
	}

	/** Escapes potentially confusing or important characters in the String provided.
	 * @param escapable an unescaped string.
	 * @return an escaped string for usage in JSON; An escaped string is one that has escaped all of the quotes ("),
	 *         backslashes (\), return character (\r), new line character (\n), tab character (\t),
	 *         backspace character (\b), form feed character (\f) and other control characters [u0000..u001F] or
	 *         characters [u007F..u009F], [u2000..u20FF] with a
	 *         backslash (\) which itself must be escaped by the backslash in a java string. */
	public static String escape(final String escapable) {
		final StringBuilder builder = new StringBuilder();
		final int characters = escapable.length();
		for (int i = 0; i < characters; i++) {
			final char character = escapable.charAt(i);
			switch (character) {
				case '"':
					builder.append("\\\"");
					break;
				case '\\':
					builder.append("\\\\");
					break;
				case '\b':
					builder.append("\\b");
					break;
				case '\f':
					builder.append("\\f");
					break;
				case '\n':
					builder.append("\\n");
					break;
				case '\r':
					builder.append("\\r");
					break;
				case '\t':
					builder.append("\\t");
					break;
				default:
					/* The many characters that get replaced are benign to software but could be mistaken by people
					 * reading it for a JSON relevant character. */
					if (((character >= '\u0000') && (character <= '\u001F')) || ((character >= '\u007F') && (character <= '\u009F')) || ((character >= '\u2000') && (character <= '\u20FF'))) {
						final String characterHexCode = Integer.toHexString(character);
						builder.append("\\u");
						for (int k = 0; k < (4 - characterHexCode.length()); k++)
							builder.append("0");
						builder.append(characterHexCode.toUpperCase());
					} else
						/* Character didn't need escaping. */
						builder.append(character);
			}
		}
		return builder.toString();
	}

	/** Processes the lexer's reader for the next token.
	 * @param lexer represents a text processor being used in the deserialization process.
	 * @return a token representing a meaningful element encountered by the lexer.
	 * @throws JSONParseException if an unexpected character is encountered while processing the text. */
	private static Yytoken lexNextToken(final Yylex lexer) throws JSONParseException {
		Yytoken returnable;
		/* Parse through the next token. */
		try {
			returnable = lexer.yylex();
		} catch (final IOException caught) {
			throw new JSONParseException(-1, JSONParseException.Problems.UNEXPECTED_EXCEPTION, caught);
		}
		if (returnable == null)
			/* If there isn't another token, it must be the end. */
			returnable = new Yytoken(Yytoken.Types.END, null);
		return returnable;
	}

	/** Used for state transitions while deserializing.
	 * @param stateStack represents the deserialization states saved for future processing.
	 * @return a state for deserialization context so it knows how to consume the next token. */
	private static States popNextState(final LinkedList<States> stateStack) {
		if (stateStack.size() > 0)
			return stateStack.removeLast();
		else
			return States.PARSED_ERROR;
	}

	/** Makes the JSON input more easily human readable using indentation and newline of the caller's choice. This means
	 * the validity of the JSON printed by this method is dependent on the caller's choice of indentation and newlines.
	 * @param readable representing a JSON formatted string with out extraneous characters, like one returned from
	 *        Jsoner#serialize(Object).
	 * @param writable represents where the pretty printed JSON should be written to.
	 * @param indentation representing the indentation used to format the JSON string. NOT validated as a proper
	 *        indentation. It is recommended to use tabs ("\t"), but 3 or 4 spaces are common alternatives.
	 * @param newline representing the newline used to format the JSON string. NOT validated as a proper newline. It is
	 *        recommended to use "\n", but "\r" or "/r/n" are common alternatives.
	 * @throws IOException if the provided writer encounters an IO issue.
	 * @throws JSONParseException if the provided reader encounters an IO issue.
	 *
	 * @since 3.1.0 made public to allow large JSON inputs and more pretty print control. */
	public static void prettyPrint(final Reader readable, final Writer writable, final String indentation, final String newline) throws IOException, JSONParseException {
		final Yylex lexer = new Yylex(readable);
		Yytoken lexed;
		int level = 0;
		do {
			lexed = JSONParser.lexNextToken(lexer);
			switch (lexed.getType()) {
				case COLON:
					writable.append(lexed.getValue().toString());
					writable.append(' ');
					break;
				case COMMA:
					writable.append(lexed.getValue().toString());
					writable.append(newline);
					for (int i = 0; i < level; i++)
						writable.append(indentation);
					break;
				case END:
					break;
				case LEFT_BRACE:
				case LEFT_SQUARE:
					writable.append(lexed.getValue().toString());
					writable.append(newline);
					level++;
					for (int i = 0; i < level; i++)
						writable.append(indentation);
					break;
				case RIGHT_BRACE:
				case RIGHT_SQUARE:
					writable.append(newline);
					level--;
					for (int i = 0; i < level; i++)
						writable.append(indentation);
					writable.append(lexed.getValue().toString());
					break;
				default:
					if (lexed.getValue() == null)
						writable.append("null");
					else if (lexed.getValue() instanceof String) {
						writable.append("\"");
						writable.append(JSONParser.escape((String) lexed.getValue()));
						writable.append("\"");
					} else
						writable.append(lexed.getValue().toString());
					break;
			}
		} while (!lexed.getType().equals(Yytoken.Types.END));
		writable.flush();
	}

	/** A convenience method to pretty print a String with tabs ("\t") and "\n" for newlines.
	 * @param printable representing a JSON formatted string with out extraneous characters, like one returned from
	 *        Jsoner#serialize(Object).
	 * @return printable except it will have '\n' then '\t' characters inserted after '[', '{', ',' and before ']' '}'
	 *         tokens in the JSON. It will return null if printable isn't a JSON string. */
	public static String prettyPrint(final String printable) {
		final StringWriter writer = new StringWriter();
		try {
			JSONParser.prettyPrint(new StringReader(printable), writer, "\t", "\n");
		} catch (final IOException caught) {
			/* See java.io.StringReader.
			 * See java.io.StringWriter. */
		} catch (final JSONParseException caught) {
			/* Would have been caused by a an IO exception while lexing, but the StringReader does not throw them. See
			 * java.io.StringReader. */
		}
		return writer.toString();
	}

	/** A convenience method that assumes a StringWriter.
	 * @param jsonSerializable represents the object that should be serialized as a string in JSON format.
	 * @return a string, in JSON format, that represents the object provided.
	 * @throws IllegalArgumentException if the jsonSerializable isn't serializable in JSON.
	 *
	 * @see StringWriter */
	public static String serialize(final Object jsonSerializable) {
		final StringWriter writableDestination = new StringWriter();
		try {
			JSONParser.serialize(jsonSerializable, writableDestination);
		} catch (final IOException caught) {
			/* See java.io.StringWriter. */
		}
		return writableDestination.toString();
	}

	/** Serializes values according to the RFC 7159 JSON specification. It will also trust the serialization provided by
	 * any Jsonables it serializes.
	 * @param jsonSerializable represents the object that should be serialized in JSON format.
	 * @param writableDestination represents where the resulting JSON text is written to.
	 * @throws IOException if the writableDestination encounters an I/O problem, like being closed while in use.
	 * @throws IllegalArgumentException if the jsonSerializable isn't serializable in JSON. */
	public static void serialize(final Object jsonSerializable, final Writer writableDestination) throws IOException {
		JSONParser.serialize(jsonSerializable, writableDestination, EnumSet.of(SerializationOptions.ALLOW_JSONABLES));
	}

	/** Serialize values to JSON and write them to the provided writer based on behavior flags.
	 * @param jsonSerializable represents the object that should be serialized to a string in JSON format.
	 * @param writableDestination represents where the resulting JSON text is written to.
	 * @param flags represents the allowances and restrictions on serialization.
	 * @throws IOException if the writableDestination encounters an I/O problem.
	 * @throws IllegalArgumentException if the jsonSerializable isn't serializable in JSON.
	 * @see SerializationOptions */
	private static void serialize(final Object jsonSerializable, final Writer writableDestination, final Set<SerializationOptions> flags) throws IOException {
		if (jsonSerializable == null)
			/* When a null is passed in the word null is supported in JSON. */
			writableDestination.write("null");
		else if (((jsonSerializable instanceof Jsonable) && flags.contains(SerializationOptions.ALLOW_JSONABLES)))
			/* Writes the writable as defined by the writable. */
			((Jsonable) jsonSerializable).toJson(writableDestination);
		else if (jsonSerializable instanceof String) {
			/* Make sure the string is properly escaped. */
			writableDestination.write('"');
			writableDestination.write(JSONParser.escape((String) jsonSerializable));
			writableDestination.write('"');
		} else if (jsonSerializable instanceof Character)
			/* Make sure the string is properly escaped.
			 * Quotes for some reason are necessary for String, but not Character. */
			writableDestination.write(JSONParser.escape(jsonSerializable.toString()));
		else if (jsonSerializable instanceof Double) {
			if (((Double) jsonSerializable).isInfinite() || ((Double) jsonSerializable).isNaN())
				/* Infinite and not a number are not supported by the JSON specification, so null is used instead. */
				writableDestination.write("null");
			else
				writableDestination.write(jsonSerializable.toString());
		} else if (jsonSerializable instanceof Float) {
			if (((Float) jsonSerializable).isInfinite() || ((Float) jsonSerializable).isNaN())
				/* Infinite and not a number are not supported by the JSON specification, so null is used instead. */
				writableDestination.write("null");
			else
				writableDestination.write(jsonSerializable.toString());
		} else if (jsonSerializable instanceof Number)
			writableDestination.write(jsonSerializable.toString());
		else if (jsonSerializable instanceof Boolean)
			writableDestination.write(jsonSerializable.toString());
		else if (jsonSerializable instanceof Map) {
			/* Writes the map in JSON object format. */
			boolean isFirstEntry = true;
			@SuppressWarnings("rawtypes")
			final Iterator entries = ((Map) jsonSerializable).entrySet().iterator();
			writableDestination.write('{');
			while (entries.hasNext()) {
				if (isFirstEntry)
					isFirstEntry = false;
				else
					writableDestination.write(',');
				@SuppressWarnings("rawtypes")
				final Map.Entry entry = (Map.Entry) entries.next();
				JSONParser.serialize(entry.getKey(), writableDestination, flags);
				writableDestination.write(':');
				JSONParser.serialize(entry.getValue(), writableDestination, flags);
			}
			writableDestination.write('}');
		} else if (jsonSerializable instanceof Collection) {
			/* Writes the collection in JSON array format. */
			boolean isFirstElement = true;
			@SuppressWarnings("rawtypes")
			final Iterator elements = ((Collection) jsonSerializable).iterator();
			writableDestination.write('[');
			while (elements.hasNext()) {
				if (isFirstElement)
					isFirstElement = false;
				else
					writableDestination.write(',');
				JSONParser.serialize(elements.next(), writableDestination, flags);
			}
			writableDestination.write(']');
		} else if (jsonSerializable instanceof byte[]) {
			/* Writes the array in JSON array format. */
			final byte[] writableArray = (byte[]) jsonSerializable;
			final int numberOfElements = writableArray.length;
			writableDestination.write('[');
			for (int i = 0; i < numberOfElements; i++)
				if (i == (numberOfElements - 1))
					JSONParser.serialize(writableArray[i], writableDestination, flags);
				else {
					JSONParser.serialize(writableArray[i], writableDestination, flags);
					writableDestination.write(',');
				}
			writableDestination.write(']');
		} else if (jsonSerializable instanceof short[]) {
			/* Writes the array in JSON array format. */
			final short[] writableArray = (short[]) jsonSerializable;
			final int numberOfElements = writableArray.length;
			writableDestination.write('[');
			for (int i = 0; i < numberOfElements; i++)
				if (i == (numberOfElements - 1))
					JSONParser.serialize(writableArray[i], writableDestination, flags);
				else {
					JSONParser.serialize(writableArray[i], writableDestination, flags);
					writableDestination.write(',');
				}
			writableDestination.write(']');
		} else if (jsonSerializable instanceof int[]) {
			/* Writes the array in JSON array format. */
			final int[] writableArray = (int[]) jsonSerializable;
			final int numberOfElements = writableArray.length;
			writableDestination.write('[');
			for (int i = 0; i < numberOfElements; i++)
				if (i == (numberOfElements - 1))
					JSONParser.serialize(writableArray[i], writableDestination, flags);
				else {
					JSONParser.serialize(writableArray[i], writableDestination, flags);
					writableDestination.write(',');
				}
			writableDestination.write(']');
		} else if (jsonSerializable instanceof long[]) {
			/* Writes the array in JSON array format. */
			final long[] writableArray = (long[]) jsonSerializable;
			final int numberOfElements = writableArray.length;
			writableDestination.write('[');
			for (int i = 0; i < numberOfElements; i++)
				if (i == (numberOfElements - 1))
					JSONParser.serialize(writableArray[i], writableDestination, flags);
				else {
					JSONParser.serialize(writableArray[i], writableDestination, flags);
					writableDestination.write(',');
				}
			writableDestination.write(']');
		} else if (jsonSerializable instanceof float[]) {
			/* Writes the array in JSON array format. */
			final float[] writableArray = (float[]) jsonSerializable;
			final int numberOfElements = writableArray.length;
			writableDestination.write('[');
			for (int i = 0; i < numberOfElements; i++)
				if (i == (numberOfElements - 1))
					JSONParser.serialize(writableArray[i], writableDestination, flags);
				else {
					JSONParser.serialize(writableArray[i], writableDestination, flags);
					writableDestination.write(',');
				}
			writableDestination.write(']');
		} else if (jsonSerializable instanceof double[]) {
			/* Writes the array in JSON array format. */
			final double[] writableArray = (double[]) jsonSerializable;
			final int numberOfElements = writableArray.length;
			writableDestination.write('[');
			for (int i = 0; i < numberOfElements; i++)
				if (i == (numberOfElements - 1))
					JSONParser.serialize(writableArray[i], writableDestination, flags);
				else {
					JSONParser.serialize(writableArray[i], writableDestination, flags);
					writableDestination.write(',');
				}
			writableDestination.write(']');
		} else if (jsonSerializable instanceof boolean[]) {
			/* Writes the array in JSON array format. */
			final boolean[] writableArray = (boolean[]) jsonSerializable;
			final int numberOfElements = writableArray.length;
			writableDestination.write('[');
			for (int i = 0; i < numberOfElements; i++)
				if (i == (numberOfElements - 1))
					JSONParser.serialize(writableArray[i], writableDestination, flags);
				else {
					JSONParser.serialize(writableArray[i], writableDestination, flags);
					writableDestination.write(',');
				}
			writableDestination.write(']');
		} else if (jsonSerializable instanceof char[]) {
			/* Writes the array in JSON array format. */
			final char[] writableArray = (char[]) jsonSerializable;
			final int numberOfElements = writableArray.length;
			writableDestination.write("[\"");
			for (int i = 0; i < numberOfElements; i++)
				if (i == (numberOfElements - 1))
					JSONParser.serialize(writableArray[i], writableDestination, flags);
				else {
					JSONParser.serialize(writableArray[i], writableDestination, flags);
					writableDestination.write("\",\"");
				}
			writableDestination.write("\"]");
		} else if (jsonSerializable instanceof Object[]) {
			/* Writes the array in JSON array format. */
			final Object[] writableArray = (Object[]) jsonSerializable;
			final int numberOfElements = writableArray.length;
			writableDestination.write('[');
			for (int i = 0; i < numberOfElements; i++)
				if (i == (numberOfElements - 1))
					JSONParser.serialize(writableArray[i], writableDestination, flags);
				else {
					JSONParser.serialize(writableArray[i], writableDestination, flags);
					writableDestination.write(",");
				}
			writableDestination.write(']');
		} else /* It cannot by any measure be safely serialized according to specification. */
		if (flags.contains(SerializationOptions.ALLOW_INVALIDS))
			/* Can be helpful for debugging how it isn't valid. */
			writableDestination.write(jsonSerializable.toString());
		else
			/* Notify the caller the cause of failure for the serialization. */
			throw new IllegalArgumentException("Encountered a: " + jsonSerializable.getClass().getName() + " as: " + jsonSerializable.toString()
					+ "  that isn't JSON serializable.\n  Try:\n    1) Implementing the Jsonable interface for the object to return valid JSON. If it already does it probably has a bug.\n    2) If you cannot edit the source of the object or couple it with this library consider wrapping it in a class that does implement the Jsonable interface.\n    3) Otherwise convert it to a boolean, null, number, JsonArray, JsonObject, or String value before serializing it.\n    4) If you feel it should have serialized you could use a more tolerant serialization for debugging purposes.");
	}

	/** Serializes like the first version of this library.
	 * It has been adapted to use Jsonable for serializing custom objects, but otherwise works like the old JSON string
	 * serializer. It will allow non-JSON values in its output like the old one. It can be helpful for last resort log
	 * statements and debugging errors in self generated JSON. Anything serialized using this method isn't guaranteed to
	 * be deserializable.
	 * @param jsonSerializable represents the object that should be serialized in JSON format.
	 * @param writableDestination represents where the resulting JSON text is written to.
	 * @throws IOException if the writableDestination encounters an I/O problem, like being closed while in use. */
	public static void serializeCarelessly(final Object jsonSerializable, final Writer writableDestination) throws IOException {
		JSONParser.serialize(jsonSerializable, writableDestination, EnumSet.of(SerializationOptions.ALLOW_JSONABLES, SerializationOptions.ALLOW_INVALIDS));
	}

	/** Serializes JSON values and only JSON values according to the RFC 7159 JSON specification.
	 * @param jsonSerializable represents the object that should be serialized in JSON format.
	 * @param writableDestination represents where the resulting JSON text is written to.
	 * @throws IOException if the writableDestination encounters an I/O problem, like being closed while in use.
	 * @throws IllegalArgumentException if the jsonSerializable isn't serializable in raw JSON. */
	public static void serializeStrictly(final Object jsonSerializable, final Writer writableDestination) throws IOException {
		JSONParser.serialize(jsonSerializable, writableDestination, EnumSet.noneOf(SerializationOptions.class));
	}
}
