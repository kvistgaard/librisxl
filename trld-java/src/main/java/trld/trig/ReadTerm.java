/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/trig/parser.py
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

import trld.Input;
import static trld.Common.dumpJson;
import static trld.jsonld.Base.VALUE;
import static trld.jsonld.Base.TYPE;
import static trld.jsonld.Base.LANGUAGE;
import static trld.jsonld.Base.ID;
import static trld.jsonld.Base.LIST;
import static trld.jsonld.Base.GRAPH;
import static trld.jsonld.Base.CONTEXT;
import static trld.jsonld.Base.VOCAB;
import static trld.jsonld.Base.BASE;
import static trld.jsonld.Base.PREFIX;
import static trld.jsonld.Base.PREFIX_DELIMS;
import static trld.Rdfterms.RDF_TYPE;
import static trld.Rdfterms.XSD;
import static trld.Rdfterms.XSD_DOUBLE;
import static trld.Rdfterms.XSD_INTEGER;
import static trld.trig.Parser.*;


public class ReadTerm extends BaseParserState { // LINE: 127
  public static final String ESCAPE_CHAR = "\\"; // LINE: 129
  public List<String> collected; // LINE: 131
  public Map<String, String> escapeChars; // LINE: 132
  public Boolean escapeNext; // LINE: 133
  public List<String> unicodeChars; // LINE: 134
  public Integer unicodeEscapesLeft; // LINE: 135

  public ReadTerm(/*@Nullable*/ ParserState parent) { // LINE: 137
    super(parent); // LINE: 138
    this.collected = new ArrayList<>(); // LINE: 139
    this.escapeNext = false; // LINE: 140
    this.unicodeChars = new ArrayList<>(); // LINE: 141
    this.unicodeEscapesLeft = 0; // LINE: 142
  }

  public void collect(String c) { // LINE: 144
    this.collected.add(c); // LINE: 145
  }

  public String pop() { // LINE: 147
    String value = String.join("", this.collected); // LINE: 148
    this.collected = new ArrayList<>(); // LINE: 149
    return value; // LINE: 150
  }

  public boolean handleEscape(String c) { // LINE: 152
    if (this.unicodeEscapesLeft > 0) { // LINE: 153
      this.unicodeChars.add(c); // LINE: 154
      if (this.unicodeEscapesLeft == 1) { // LINE: 155
        String hexSeq = String.join("", this.unicodeChars); // LINE: 156
        try { // LINE: 157
          c = Character.toString(((char) Integer.valueOf(hexSeq, 16).intValue())); // LINE: 158
        } catch (NumberFormatException e) { // LINE: 159
          throw new NotationError("Invalid unicode escape: " + hexSeq); // LINE: 160
        }
        this.unicodeChars = new ArrayList<>(); // LINE: 161
        this.unicodeEscapesLeft = 0; // LINE: 162
      } else {
        this.unicodeEscapesLeft -= 1;
        return true; // LINE: 165
      }
    } else if (this.escapeNext) { // LINE: 167
      if ((c == null && ((Object) "u") == null || c != null && (c).equals("u"))) { // LINE: 168
        this.unicodeEscapesLeft = 4; // LINE: 169
        return true; // LINE: 170
      } else if ((c == null && ((Object) "U") == null || c != null && (c).equals("U"))) { // LINE: 171
        this.unicodeEscapesLeft = 8; // LINE: 172
        return true; // LINE: 173
      } else if (this.escapeChars.containsKey(c)) { // LINE: 174
        c = (String) this.escapeChars.get(c); // LINE: 175
      } else {
        throw new NotationError("Invalid escape char: " + c); // LINE: 177
      }
    }
    if (this.escapeNext) { // LINE: 179
      this.escapeNext = false; // LINE: 180
      this.collect(c); // LINE: 181
      return true; // LINE: 182
    }
    if ((c == null && ((Object) this.ESCAPE_CHAR) == null || c != null && (c).equals(this.ESCAPE_CHAR))) { // LINE: 184
      this.escapeNext = true; // LINE: 185
      return true; // LINE: 186
    }
    return false; // LINE: 188
  }

  public Map.Entry<ParserState, Object> backtrack(String prevC, String c, Object value) { // LINE: 190
    Map.Entry<ParserState, Object> state_value = this.parent.consume(prevC, value); // LINE: 191
    ParserState state = state_value.getKey();
    value = state_value.getValue();
    return state.consume(c, value); // LINE: 192
  }
}
