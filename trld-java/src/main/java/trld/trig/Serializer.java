/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/trig/serializer.py
 */
package trld.trig;

//import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.io.*;

import trld.Builtins;
import trld.KeyValue;

import trld.Output;
import static trld.Common.uuid4;
import static trld.jsonld.Base.BASE;
import static trld.jsonld.Base.CONTAINER;
import static trld.jsonld.Base.CONTEXT;
import static trld.jsonld.Base.GRAPH;
import static trld.jsonld.Base.ID;
import static trld.jsonld.Base.INDEX;
import static trld.jsonld.Base.LANGUAGE;
import static trld.jsonld.Base.LIST;
import static trld.jsonld.Base.PREFIX;
import static trld.jsonld.Base.REVERSE;
import static trld.jsonld.Base.TYPE;
import static trld.jsonld.Base.VALUE;
import static trld.jsonld.Base.VOCAB;

public class Serializer {
  public static final String ANNOTATION = "@annotation"; // LINE: 11
  public static final Pattern WORD_START = (Pattern) Pattern.compile("^\\w*$"); // LINE: 13
  public static final Pattern PNAME_LOCAL_ESC = (Pattern) Pattern.compile("([~!$&'()*+,;=/?#@%]|^[.-]|[.-]$)"); // LINE: 14

  public static void serialize(Map<String, Object> data, Output out) {
    serialize(data, out, null);
  }
  public static void serialize(Map<String, Object> data, Output out, /*@Nullable*/ Map context) {
    serialize(data, out, context, null);
  }
  public static void serialize(Map<String, Object> data, Output out, /*@Nullable*/ Map context, /*@Nullable*/ String baseIri) {
    serialize(data, out, context, baseIri, null);
  }
  public static void serialize(Map<String, Object> data, Output out, /*@Nullable*/ Map context, /*@Nullable*/ String baseIri, Settings settings) { // LINE: 45
    settings = (settings != null ? settings : new Settings()); // LINE: 52
    SerializerState state = new SerializerState(out, settings, context, baseIri); // LINE: 53
    state.serialize(data); // LINE: 54
  }

  public static void serializeTurtle(Map<String, Object> data, Output out) {
    serializeTurtle(data, out, null);
  }
  public static void serializeTurtle(Map<String, Object> data, Output out, /*@Nullable*/ Map context) {
    serializeTurtle(data, out, context, null);
  }
  public static void serializeTurtle(Map<String, Object> data, Output out, /*@Nullable*/ Map context, /*@Nullable*/ String baseIri) {
    serializeTurtle(data, out, context, baseIri, false);
  }
  public static void serializeTurtle(Map<String, Object> data, Output out, /*@Nullable*/ Map context, /*@Nullable*/ String baseIri, Boolean union) { // LINE: 57
    Settings settings = new Settings(true, !(union)); // LINE: 64
    serialize(data, out, context, baseIri, settings); // LINE: 65
  }

  public static Map<String, String> collectPrefixes(/*@Nullable*/ Object context) { // LINE: 650
    if (!(context instanceof Map)) { // LINE: 651
      return new HashMap<>(); // LINE: 652
    }
    Map prefixes = new HashMap<>(); // LINE: 654
    for (Map.Entry<String, Object> key_value : ((Map<String, Object>) context).entrySet()) { // LINE: 655
      String key = key_value.getKey();
      Object value = key_value.getValue();
      if ((value instanceof String && new HashSet(new ArrayList<>(Arrays.asList(new String[] {(String) "#", "/", ":"}))).contains(((String) value).substring(((String) value).length() - 1, ((String) value).length() - 1 + 1)))) { // LINE: 657
        prefixes.put(((key == null && ((Object) VOCAB) == null || key != null && (key).equals(VOCAB)) ? "" : key), (String) value); // LINE: 658
      } else if ((value instanceof Map && (((Map) value).get(PREFIX) == null && ((Object) true) == null || ((Map) value).get(PREFIX) != null && (((Map) value).get(PREFIX)).equals(true)))) { // LINE: 659
        prefixes.put(key, ((Map) value).get(ID)); // LINE: 660
      }
    }
    return prefixes; // LINE: 662
  }

  public static List asList(Object value) { // LINE: 665
    return (value instanceof List ? (List) value : new ArrayList<>(Arrays.asList(new Object[] {(Object) value}))); // LINE: 666
  }
}
